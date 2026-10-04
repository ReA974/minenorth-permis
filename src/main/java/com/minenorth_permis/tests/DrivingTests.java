package com.minenorth_permis.tests;

import com.minenorth_permis.Licences;
import com.minenorth_permis.Msg;
import com.minenorth_permis.PermisConfig;
import com.minenorth_permis.Shop;
import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.net.HudPacket;
import com.minenorth_permis.net.MarkersPacket;
import com.minenorth_permis.net.MarkersPacket.Marker;
import com.minenorth_permis.net.Network;
import com.minenorth_permis.util.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests de conduite (conduire / camion / moto) : téléportation au départ, pose du véhicule MTS (lance le chrono),
 * passage des points de contrôle (anneaux verts), réussite = permis + carte. Un seul joueur à la fois par parcours.
 */
public final class DrivingTests {
    public static final int GREEN = 0xFF55FF55;
    public static final int GOLD = 0xFFFFC83C;
    private static final int ACCENT = 0xFF20AAEB;
    private static final int SNEAK_TICKS = 60;

    private enum Phase { WAITING, RUNNING }

    private static final class Session {
        UUID player;
        String test;
        Phase phase = Phase.WAITING;
        int progress;
        int ticksLeft;
        int waitTicks;
        int sneak;
        int clock;
        UUID vehicle;
        Item vehicleItem;
        PermisData.Loc ret;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, UUID> OCCUPIED = new HashMap<>();

    private DrivingTests() {}

    public static boolean isBusy(String test) {
        return OCCUPIED.containsKey(test);
    }

    public static boolean inTest(UUID p) {
        return SESSIONS.containsKey(p);
    }

    // ------------------------------------------------------------------ démarrage

    public static void start(ServerPlayer p, String testId) {
        MinecraftServer s = p.server;
        PermisConfig.DrivingTest t = PermisConfig.get().test(testId);
        if (t == null) {
            Msg.err(p, "Test inconnu : " + testId);
            return;
        }
        PermisData.Course c = PermisData.get(s).course(t.id);
        if (c == null || c.start == null) {
            Msg.err(p, "Le parcours de ce test n'a pas été configuré.");
            return;
        }
        if (c.checkpoints.isEmpty()) {
            Msg.err(p, "Le parcours n'a aucun point de contrôle configuré.");
            return;
        }
        if (Licences.isValid(s, p.getUUID(), t.licence)) {
            Msg.warn(p, "Tu as déjà ce permis !");
            return;
        }
        long cd = Licences.cooldown(s, p.getUUID(), "test:" + t.id);
        if (cd > 0) {
            Msg.err(p, "Tu as échoué récemment à ce test, réessaie dans " + Licences.formatDuration(cd) + ".");
            return;
        }
        if (inTest(p.getUUID()) || ShootingTests.inTest(p.getUUID())) {
            Msg.err(p, "Tu passes déjà une épreuve.");
            return;
        }
        if (isBusy(t.id)) {
            Msg.err(p, "Quelqu'un d'autre passe déjà ce test en ce moment, réessaie dans un instant.");
            return;
        }
        ItemStack vehicle = Util.parseItem(t.vehicleItem, t.vehicleNbt);
        if (vehicle == null) {
            Msg.err(p, "Véhicule de test introuvable (" + t.vehicleItem + "), préviens le staff.");
            return;
        }

        Session se = new Session();
        se.player = p.getUUID();
        se.test = t.id;
        se.ret = Util.loc(p);
        se.waitTicks = Math.max(10, t.placementTimeoutSeconds) * 20;
        se.vehicleItem = vehicle.getItem();
        if (!Util.teleport(p, c.start)) {
            Msg.err(p, "Monde du parcours introuvable (" + c.start.dim() + ").");
            return;
        }
        SESSIONS.put(p.getUUID(), se);
        OCCUPIED.put(t.id, p.getUUID());
        Util.give(p, vehicle);
        sendMarkers(p, se, c);
        sendHud(p, se, t, c);
        Msg.ok(p, "Pose ton véhicule pour commencer le test ! Le chrono démarre dès qu'il est posé.");
        Msg.info(p, "Suis les anneaux verts. Maintiens sneak 3 secondes pour abandonner.");
    }

    /** Appelé quand une entité apparaît : si c'est le véhicule posé par un candidat, le chrono démarre. */
    public static void onEntityJoin(Entity e, ServerLevel level) {
        if (SESSIONS.isEmpty()) return;
        PermisConfig.Root cfg = PermisConfig.get();
        if (!Util.matchesType(e, cfg.vehicleEntityTypes)) return;
        double r2 = cfg.vehicleDetectRadius * cfg.vehicleDetectRadius;
        for (Session se : SESSIONS.values()) {
            if (se.phase != Phase.WAITING) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(se.player);
            if (p == null || p.level() != level || p.distanceToSqr(e) > r2) continue;
            se.vehicle = e.getUUID();
            begin(p, se);
            return;
        }
    }

    private static void begin(ServerPlayer p, Session se) {
        PermisConfig.DrivingTest t = PermisConfig.get().test(se.test);
        PermisData.Course c = PermisData.get(p.server).course(se.test);
        if (t == null || c == null) {
            end(p, se, true);
            return;
        }
        int limit = c.timeLimit > 0 ? c.timeLimit : t.defaultTimeLimit;
        se.phase = Phase.RUNNING;
        se.progress = 0;
        se.ticksLeft = limit * 20;
        sendMarkers(p, se, c);
        sendHud(p, se, t, c);
        p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.2f);
        Msg.ok(p, "Véhicule posé ! Le test commence. Point de contrôle 1/" + c.checkpoints.size() + ". Temps limite : " + limit + " s.");
    }

    // ------------------------------------------------------------------ boucle

    public static void tick(MinecraftServer s) {
        if (SESSIONS.isEmpty()) return;
        PermisConfig.Root cfg = PermisConfig.get();
        for (Session se : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer p = s.getPlayerList().getPlayer(se.player);
            if (p == null) continue;
            PermisConfig.DrivingTest t = cfg.test(se.test);
            PermisData.Course c = PermisData.get(s).course(se.test);
            if (t == null || c == null || c.checkpoints.isEmpty()) {
                Msg.err(p, "Le parcours a été modifié, test annulé.");
                end(p, se, true);
                continue;
            }
            se.clock++;

            // Abandon : sneak maintenu
            if (p.isShiftKeyDown()) {
                if (++se.sneak >= SNEAK_TICKS) {
                    Msg.err(p, "Test annulé (tu t'es mis en sneak).");
                    end(p, se, true);
                    continue;
                }
            } else {
                se.sneak = 0;
            }

            if (se.phase == Phase.WAITING) {
                // Secours : le joueur est déjà assis dans un véhicule du bon mod
                Entity v = p.getVehicle();
                if (v != null && (Util.matchesType(v, cfg.vehicleEntityTypes) || Util.matchesType(v.getRootVehicle(), cfg.vehicleEntityTypes)
                        || isNamespace(v, cfg.vehicleEntityTypes))) {
                    begin(p, se);
                    continue;
                }
                if (--se.waitTicks <= 0) {
                    Msg.err(p, "Tu n'as pas posé ton véhicule à temps, test annulé.");
                    end(p, se, true);
                    continue;
                }
                if (se.clock % 20 == 0) sendHud(p, se, t, c);
                continue;
            }

            // RUNNING : passage du point de contrôle
            double radius = c.radius > 0 ? c.radius : t.defaultRadius;
            Vec3 cp = c.checkpoints.get(Math.min(se.progress, c.checkpoints.size() - 1));
            if (Util.horizontalDist(p.getX(), p.getZ(), cp.x, cp.z) <= radius && Math.abs(p.getY() - cp.y) <= 6) {
                se.progress++;
                if (se.progress >= c.checkpoints.size()) {
                    success(p, se, t);
                    continue;
                }
                p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1f, 1f);
                Msg.ok(p, "Point de contrôle " + se.progress + "/" + c.checkpoints.size() + " validé ! Direction le suivant.");
                sendMarkers(p, se, c);
                sendHud(p, se, t, c);
            }

            if (--se.ticksLeft <= 0) {
                Licences.setCooldown(s, p.getUUID(), "test:" + t.id, t.failCooldownMinutes);
                String again = t.failCooldownMinutes > 0 ? " Réessaie dans " + t.failCooldownMinutes + " minutes." : "";
                Msg.err(p, "✘ Temps écoulé ! Tu as échoué ton test." + again);
                p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
                end(p, se, true);
                continue;
            }
            if (se.clock % 20 == 0) sendHud(p, se, t, c);
        }
    }

    private static boolean isNamespace(Entity e, List<String> types) {
        // Si la config liste "mts:builder_existing", accepte aussi les sièges du même mod (mts:*).
        List<String> ns = new ArrayList<>();
        for (String t : types) {
            int i = t.indexOf(':');
            if (i > 0) ns.add(t.substring(0, i) + ":*");
        }
        return Util.matchesType(e, ns);
    }

    private static void success(ServerPlayer p, Session se, PermisConfig.DrivingTest t) {
        end(p, se, true);
        Shop.obtain(p, t.licence);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
        Msg.ok(p, "✔ " + t.name + " obtenu ! Bravo, tu as réussi ton test.");
    }

    /** Termine la session : supprime le véhicule, nettoie, renvoie le joueur. */
    private static void end(ServerPlayer p, Session se, boolean teleportBack) {
        SESSIONS.remove(se.player);
        OCCUPIED.remove(se.test, se.player);
        p.stopRiding();
        removeVehicle(p.server, se);
        if (se.phase == Phase.WAITING && se.vehicleItem != null) Util.removeSome(p, se.vehicleItem, 1);
        Network.send(p, HudPacket.hide());
        Network.send(p, MarkersPacket.clear("test"));
        if (teleportBack) Util.teleport(p, se.ret);
    }

    private static void removeVehicle(MinecraftServer s, Session se) {
        Entity v = Util.findEntity(s, se.vehicle);
        if (v != null) {
            v.ejectPassengers();
            v.discard();
        }
    }

    /** Arrêt forcé par le staff. */
    public static boolean stop(ServerPlayer p) {
        Session se = SESSIONS.get(p.getUUID());
        if (se == null) return false;
        Msg.err(p, "Ton test a été arrêté par le staff.");
        end(p, se, true);
        return true;
    }

    // ------------------------------------------------------------------ déconnexion / mort / arrêt serveur

    public static void onLogout(ServerPlayer p) {
        Session se = SESSIONS.get(p.getUUID());
        if (se == null) return;
        PermisData d = PermisData.get(p.server);
        d.holder(p.getUUID()).pendingReturn = se.ret;
        d.setDirty();
        end(p, se, false);
    }

    public static void onDeath(ServerPlayer p) {
        Session se = SESSIONS.get(p.getUUID());
        if (se == null) return;
        PermisData d = PermisData.get(p.server);
        d.holder(p.getUUID()).pendingReturn = se.ret;
        d.setDirty();
        Msg.err(p, "Test échoué.");
        end(p, se, false);
    }

    public static void stopAll(MinecraftServer s) {
        for (Session se : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer p = s.getPlayerList().getPlayer(se.player);
            if (p != null) end(p, se, true);
        }
        SESSIONS.clear();
        OCCUPIED.clear();
    }

    // ------------------------------------------------------------------ affichage

    private static void sendHud(ServerPlayer p, Session se, PermisConfig.DrivingTest t, PermisData.Course c) {
        int total = c.checkpoints.size();
        if (se.phase == Phase.WAITING) {
            Network.send(p, new HudPacket(true, t.name, "Pose ton véhicule pour démarrer", 0, total,
                    se.waitTicks / 20, "Sneak 3 s : abandonner", ACCENT));
        } else {
            boolean last = se.progress == total - 1;
            String line = last ? "Dernier point : l'arrivée !" : "Point de contrôle " + (se.progress + 1) + "/" + total;
            Network.send(p, new HudPacket(true, t.name, line, se.progress, total, (se.ticksLeft + 19) / 20,
                    "Sneak 3 s : abandonner", ACCENT));
        }
    }

    private static void sendMarkers(ServerPlayer p, Session se, PermisData.Course c) {
        PermisConfig.DrivingTest t = PermisConfig.get().test(se.test);
        float radius = (float) (c.radius > 0 ? c.radius : t != null ? t.defaultRadius : 6);
        int total = c.checkpoints.size();
        List<Marker> list = new ArrayList<>();
        int i = Math.min(se.progress, total - 1);
        Vec3 cp = c.checkpoints.get(i);
        boolean last = i == total - 1;
        list.add(Marker.ring(cp.x, cp.y, cp.z, radius, last ? GOLD : GREEN, last ? "ARRIVÉE" : (i + 1) + "/" + total,
                MarkersPacket.BEAM | MarkersPacket.NAV | MarkersPacket.WALL));
        if (i + 1 < total) {
            Vec3 nx = c.checkpoints.get(i + 1);
            list.add(Marker.ring(nx.x, nx.y, nx.z, radius, i + 1 == total - 1 ? GOLD : GREEN, "", MarkersPacket.FAINT));
        }
        Network.sendMarkers(p, "test", 0, list);
    }

    /** Aperçu admin : tous les points du parcours pendant 30 s. */
    public static void preview(ServerPlayer p, String testId) {
        PermisConfig.DrivingTest t = PermisConfig.get().test(testId);
        PermisData.Course c = PermisData.get(p.server).course(testId);
        if (c == null) return;
        float radius = (float) (c.radius > 0 ? c.radius : t != null ? t.defaultRadius : 6);
        List<Marker> list = new ArrayList<>();
        for (int i = 0; i < c.checkpoints.size(); i++) {
            Vec3 v = c.checkpoints.get(i);
            boolean last = i == c.checkpoints.size() - 1;
            list.add(Marker.ring(v.x, v.y, v.z, radius, last ? GOLD : GREEN, "#" + (i + 1), MarkersPacket.WALL | (i == 0 ? MarkersPacket.BEAM : 0)));
        }
        if (c.start != null && c.start.dim().equals(Util.dim(p))) {
            list.add(Marker.ring(c.start.x(), c.start.y(), c.start.z(), 1.2f, 0xFF20AAEB, "DÉPART", MarkersPacket.BEAM));
        }
        Network.sendMarkers(p, "preview", 20 * 30, list);
    }
}
