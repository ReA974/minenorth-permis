package com.minenorth_permis.tests;

import com.minenorth_permis.EuroBankCompat;
import com.minenorth_permis.Licences;
import com.minenorth_permis.MinenorthPermis;
import com.minenorth_permis.Msg;
import com.minenorth_permis.PermisConfig;
import com.minenorth_permis.Shop;
import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.net.HudPacket;
import com.minenorth_permis.net.MarkersPacket;
import com.minenorth_permis.net.MarkersPacket.Marker;
import com.minenorth_permis.net.Network;
import com.minenorth_permis.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Épreuve de tir : le candidat reçoit une arme prêtée et doit toucher N fois une cible (bloc target) située
 * dans une zone de tir, sans s'éloigner de sa position de départ.
 *
 * Détection des tirs :
 *  - balles TACZ : événement AmmoHitBlockEvent de TACZ (branché par réflexion, sans dépendance de compilation) ;
 *  - autres projectiles (flèches…) : ProjectileImpactEvent de Forge ;
 *  - secours si TACZ n'est pas branchable : front montant de la puissance redstone des blocs cible de la zone.
 */
public final class ShootingTests {
    private static final int RED = 0xFFFF6A9A;
    private static final int TARGET = 0xFFFF4040;
    private static final int ACCENT = 0xFFC83CF0;
    private static final int MAX_SCAN = 250_000;

    private static final class Session {
        UUID player;
        String range;
        String dim;
        Vec3 origin;
        int hits;
        int ticksLeft;
        int clock;
        int emptyTicks;
        final Set<Integer> seen = new HashSet<>();
        final List<BlockPos> targets = new ArrayList<>();
        final Map<BlockPos, Integer> lastPower = new HashMap<>();
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, UUID> OCCUPIED = new HashMap<>();
    private static final Map<UUID, Long> LAST_CALL = new HashMap<>();

    private static boolean taczHooked;
    private static Method taczHit, taczAmmo;

    private ShootingTests() {}

    public static boolean inTest(UUID p) {
        return SESSIONS.containsKey(p);
    }

    // ------------------------------------------------------------------ branchement TACZ

    @SuppressWarnings("unchecked")
    public static void hookTacz() {
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class<?> c = Class.forName("com.tacz.guns.api.event.server.AmmoHitBlockEvent");
            taczHit = c.getMethod("getHitResult");
            taczAmmo = c.getMethod("getAmmo");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) c, ShootingTests::onTaczHit);
            taczHooked = true;
            MinenorthPermis.LOG.info("[Permis] Détection des balles TACZ activée");
        } catch (Throwable t) {
            MinenorthPermis.LOG.warn("[Permis] Impossible de brancher TACZ, détection par redstone des cibles", t);
        }
    }

    private static void onTaczHit(Event ev) {
        if (SESSIONS.isEmpty()) return;
        try {
            BlockHitResult hit = (BlockHitResult) taczHit.invoke(ev);
            Entity ammo = (Entity) taczAmmo.invoke(ev);
            if (ammo == null || ammo.level().isClientSide || !(ammo instanceof Projectile pr)) return;
            onHit(pr.getOwner(), ammo.level(), hit.getBlockPos(), ammo.getId());
        } catch (Exception e) {
            MinenorthPermis.LOG.debug("[Permis] Lecture de l'impact TACZ impossible", e);
        }
    }

    /** Projectiles non-TACZ (flèches, autres mods d'armes qui passent par Forge). */
    public static void onProjectileImpact(ProjectileImpactEvent e) {
        if (SESSIONS.isEmpty() || !taczHooked || !PermisConfig.get().shooting.countOtherProjectiles) return;
        Projectile pr = e.getProjectile();
        if (pr.level().isClientSide || pr.getClass().getName().startsWith("com.tacz")) return;
        HitResult r = e.getRayTraceResult();
        if (r.getType() != HitResult.Type.BLOCK) return;
        onHit(pr.getOwner(), pr.level(), ((BlockHitResult) r).getBlockPos(), pr.getId());
    }

    private static boolean isTarget(BlockState st) {
        ResourceLocation k = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        return k != null && PermisConfig.get().shooting.targetBlocks.contains(k.toString());
    }

    private static void onHit(Entity owner, Level level, BlockPos pos, int projectileId) {
        if (!(owner instanceof ServerPlayer p)) return;
        Session se = SESSIONS.get(p.getUUID());
        if (se == null) return;
        PermisData.Range r = PermisData.get(p.server).ranges().get(se.range);
        if (r == null || !r.contains(level.dimension().location().toString(), pos)) return;
        if (!isTarget(level.getBlockState(pos))) return;
        if (!se.seen.add(projectileId)) return;
        if (se.seen.size() > 512) se.seen.clear();
        credit(p, se);
    }

    private static void credit(ServerPlayer p, Session se) {
        PermisConfig.Shooting cfg = PermisConfig.get().shooting;
        se.hits++;
        p.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1f, 1f);
        if (se.hits >= cfg.hitsRequired) {
            success(p, se);
            return;
        }
        Msg.ok(p, "Cible touchée ! » " + se.hits + "/" + cfg.hitsRequired + " tirs réussis.");
        sendHud(p, se);
    }

    // ------------------------------------------------------------------ démarrage

    /** Zone demandée, ou la plus proche du joueur dans son monde. null si aucune. */
    public static String resolveRange(ServerPlayer p, String rangeName) {
        Map<String, PermisData.Range> ranges = PermisData.get(p.server).ranges();
        if (rangeName != null) return ranges.containsKey(rangeName) ? rangeName : null;
        String name = null;
        double best = Double.MAX_VALUE;
        for (Map.Entry<String, PermisData.Range> e : ranges.entrySet()) {
            if (!e.getValue().dim.equals(Util.dim(p))) continue;
            double d = e.getValue().center().distanceToSqr(p.position());
            if (d < best) {
                best = d;
                name = e.getKey();
            }
        }
        return name;
    }

    /** Frais d'examen en centimes. */
    public static long examPrice() {
        PermisConfig.Root cfg = PermisConfig.get();
        if (cfg.shooting.examPrice >= 0) return Math.round(cfg.shooting.examPrice * 100);
        PermisConfig.Licence l = cfg.licence(cfg.shooting.licence);
        return l == null ? 0 : l.priceCents();
    }

    /** Raison qui empêche de passer l'épreuve maintenant, ou null si tout est bon. */
    public static String blocker(ServerPlayer p, String name) {
        MinecraftServer s = p.server;
        PermisConfig.Shooting cfg = PermisConfig.get().shooting;
        if (name == null || !PermisData.get(s).ranges().containsKey(name)) return "Aucune zone de tir configurée ici.";
        if (Licences.isValid(s, p.getUUID(), cfg.licence)) return "Tu as déjà ton permis arme !";
        long cd = Licences.cooldown(s, p.getUUID(), "tir");
        if (cd > 0) return "Échec récent : réessaie dans " + Licences.formatDuration(cd) + ".";
        if (inTest(p.getUUID()) || DrivingTests.inTest(p.getUUID())) return "Tu passes déjà une épreuve.";
        if (OCCUPIED.containsKey(name)) return "Une épreuve est déjà en cours sur ce stand.";
        if (Util.parseItem(cfg.gunItem, cfg.gunNbt) == null) return "Arme de l'épreuve introuvable, préviens le staff.";
        return null;
    }

    /** PNJ / commande : ouvre l'écran de paiement de l'examen. */
    public static void openExam(ServerPlayer p, String rangeName) {
        long now = System.currentTimeMillis();
        Long last = LAST_CALL.get(p.getUUID());
        if (last != null && now - last < 1500) return;          // double clic PNJ
        LAST_CALL.put(p.getUUID(), now);
        String name = resolveRange(p, rangeName);
        Network.openTir(p, name, true, "", true);
    }

    /** Paiement validé dans l'écran : on encaisse puis on lance l'épreuve (remboursé si le lancement échoue). */
    public static void payAndStart(ServerPlayer p, String name, boolean card) {
        String why = blocker(p, name);
        if (why != null) {
            Network.openTir(p, name, false, why, false);
            return;
        }
        long price = examPrice();
        String err = Shop.pay(p, price, card);
        if (err != null) {
            Network.openTir(p, name, false, err, false);
            return;
        }
        String fail = begin(p, name);
        if (fail != null) {
            Shop.refund(p, price, card);
            Network.openTir(p, name, false, fail + " Tu as été remboursé.", false);
            return;
        }
        Network.closeMenu(p, com.minenorth_permis.net.MenuStatePacket.TIR);
        if (price > 0) Msg.ok(p, "Frais d'examen payés : " + EuroBankCompat.format(price) + ".");
    }

    /** Lancement direct sans paiement (staff). */
    public static void startFree(ServerPlayer p, String rangeName) {
        String name = resolveRange(p, rangeName);
        String why = blocker(p, name);
        if (why == null) why = begin(p, name);
        if (why != null) Msg.err(p, why);
    }

    /** Démarre l'épreuve. Renvoie un message d'erreur ou null. */
    private static String begin(ServerPlayer p, String name) {
        PermisConfig.Shooting cfg = PermisConfig.get().shooting;
        PermisData.Range r = PermisData.get(p.server).ranges().get(name);
        ItemStack gun = Util.parseItem(cfg.gunItem, cfg.gunNbt);
        if (r == null || gun == null) return "Épreuve impossible à lancer.";
        Session se = new Session();
        se.player = p.getUUID();
        se.range = name;
        se.dim = Util.dim(p);
        se.origin = p.position();
        se.ticksLeft = cfg.timeLimitSeconds > 0 ? cfg.timeLimitSeconds * 20 : -1;
        scanTargets(p.serverLevel(), r, se);
        if (se.targets.isEmpty()) return "Aucune cible dans la zone de tir, préviens le staff.";
        SESSIONS.put(p.getUUID(), se);
        OCCUPIED.put(name, p.getUUID());

        Util.give(p, Util.tagTest(gun));
        for (String spec : cfg.extraItems) {
            ItemStack extra = Util.parseItem(spec, "");
            if (extra != null) Util.give(p, Util.tagTest(extra));
        }
        sendMarkers(p, se);
        sendHud(p, se);
        Msg.ok(p, "Épreuve de tir lancée : touche " + cfg.hitsRequired + " fois la cible ! (reste à moins de "
                + (int) cfg.maxDistance + " blocs)" + (cfg.failWhenOutOfAmmo ? " Attention : chargeur vide = échec." : ""));
        return null;
    }

    /** Arme prêtée vide (chargeur + canon + munitions factices) et plus aucune munition prêtée sur soi. */
    private static boolean outOfAmmo(ServerPlayer p) {
        ItemStack gun = ItemStack.EMPTY;
        boolean spareAmmo = false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!Util.isTestItem(st)) continue;
            if (st.getTag().contains("GunId")) gun = st;
            else spareAmmo = true;
        }
        if (gun.isEmpty()) return true;                         // arme posée / rangée ailleurs
        if (spareAmmo) return false;
        var t = gun.getTag();
        return t.getInt("GunCurrentAmmoCount") <= 0 && !t.getBoolean("HasBulletInBarrel") && t.getInt("DummyAmmo") <= 0;
    }

    private static void scanTargets(ServerLevel level, PermisData.Range r, Session se) {
        long vol = (long) (r.max.getX() - r.min.getX() + 1) * (r.max.getY() - r.min.getY() + 1) * (r.max.getZ() - r.min.getZ() + 1);
        if (vol > MAX_SCAN) return;
        for (BlockPos pos : BlockPos.betweenClosed(r.min, r.max)) {
            BlockState st = level.getBlockState(pos);
            if (isTarget(st)) {
                BlockPos im = pos.immutable();
                se.targets.add(im);
                se.lastPower.put(im, st.hasProperty(BlockStateProperties.POWER) ? st.getValue(BlockStateProperties.POWER) : 0);
            }
        }
    }

    // ------------------------------------------------------------------ boucle

    public static void tick(MinecraftServer s) {
        if (SESSIONS.isEmpty()) return;
        PermisConfig.Shooting cfg = PermisConfig.get().shooting;
        for (Session se : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer p = s.getPlayerList().getPlayer(se.player);
            if (p == null) continue;
            se.clock++;

            if (!taczHooked) pollTargets(p, se);
            if (!SESSIONS.containsKey(se.player)) continue;

            if (se.clock % 10 == 0 && cfg.failWhenOutOfAmmo) {
                // petit délai : laisse arriver la dernière balle avant de déclarer l'échec
                se.emptyTicks = outOfAmmo(p) ? se.emptyTicks + 10 : 0;
                if (se.emptyTicks >= 30) {
                    Msg.err(p, "ÉCHEC ! Ton revolver est vide : " + se.hits + "/" + cfg.hitsRequired + " cibles touchées.");
                    fail(p, se);
                    continue;
                }
            }
            if (se.clock % 10 == 0) {
                boolean away = !Util.dim(p).equals(se.dim) || p.position().distanceTo(se.origin) > cfg.maxDistance;
                if (away) {
                    Msg.err(p, "ÉCHEC ! Tu t'es trop éloigné de la zone de tir (" + (int) cfg.maxDistance + " blocs max). L'épreuve est annulée.");
                    fail(p, se);
                    continue;
                }
            }
            if (se.ticksLeft > 0 && --se.ticksLeft == 0) {
                Msg.err(p, "ÉCHEC ! Temps écoulé.");
                fail(p, se);
                continue;
            }
            if (se.clock % 20 == 0) sendHud(p, se);
        }
    }

    /** Secours sans TACZ : un bloc cible qui s'allume = un tir réussi. */
    private static void pollTargets(ServerPlayer p, Session se) {
        ServerLevel lvl = Util.level(p.server, se.dim);
        if (lvl == null) return;
        for (BlockPos pos : se.targets) {
            BlockState st = lvl.getBlockState(pos);
            int pw = st.hasProperty(BlockStateProperties.POWER) ? st.getValue(BlockStateProperties.POWER) : 0;
            int before = se.lastPower.getOrDefault(pos, 0);
            se.lastPower.put(pos, pw);
            if (pw > 0 && before == 0) {
                credit(p, se);
                if (!SESSIONS.containsKey(se.player)) return;
            }
        }
    }

    private static void success(ServerPlayer p, Session se) {
        String lic = PermisConfig.get().shooting.licence;
        end(p, se);
        Shop.obtain(p, lic);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
        Msg.ok(p, "Félicitations ! Tu as réussi l'épreuve de tir et obtenu ton permis !");
    }

    private static void fail(ServerPlayer p, Session se) {
        Licences.setCooldown(p.server, p.getUUID(), "tir", PermisConfig.get().shooting.failCooldownMinutes);
        p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1f, 1f);
        end(p, se);
    }

    private static void end(ServerPlayer p, Session se) {
        SESSIONS.remove(se.player);
        OCCUPIED.remove(se.range, se.player);
        Util.removeAll(p, Util::isTestItem);
        Network.send(p, HudPacket.hide());
        Network.send(p, MarkersPacket.clear("test"));
    }

    public static boolean stop(ServerPlayer p) {
        Session se = SESSIONS.get(p.getUUID());
        if (se == null) return false;
        Msg.err(p, "Ton épreuve de tir a été arrêtée par le staff.");
        end(p, se);
        return true;
    }

    public static void onLogoutOrDeath(ServerPlayer p) {
        Session se = SESSIONS.get(p.getUUID());
        if (se != null) end(p, se);
    }

    public static void stopAll(MinecraftServer s) {
        for (Session se : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer p = s.getPlayerList().getPlayer(se.player);
            if (p != null) end(p, se);
        }
        SESSIONS.clear();
        OCCUPIED.clear();
    }

    // ------------------------------------------------------------------ affichage

    private static void sendHud(ServerPlayer p, Session se) {
        PermisConfig.Shooting cfg = PermisConfig.get().shooting;
        double d = p.position().distanceTo(se.origin);
        String line = "Cibles touchées : " + se.hits + "/" + cfg.hitsRequired;
        String hint = String.format("Distance : %.1f / %d m", d, (int) cfg.maxDistance);
        Network.send(p, new HudPacket(true, cfg.name, line, se.hits, cfg.hitsRequired,
                se.ticksLeft < 0 ? -1 : (se.ticksLeft + 19) / 20, hint, ACCENT));
    }

    private static void sendMarkers(ServerPlayer p, Session se) {
        PermisConfig.Shooting cfg = PermisConfig.get().shooting;
        List<Marker> list = new ArrayList<>();
        list.add(Marker.ring(se.origin.x, se.origin.y, se.origin.z, (float) cfg.maxDistance, RED, "", MarkersPacket.WALL | MarkersPacket.FAINT));
        int n = 0;
        for (BlockPos t : se.targets) {
            if (n++ > 64) break;
            list.add(Marker.box(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1, TARGET, "CIBLE", 0));
        }
        Network.sendMarkers(p, "test", 0, list);
    }

    /** Aperçu admin d'une zone pendant 30 s. */
    public static int preview(ServerPlayer p, String name) {
        PermisData.Range r = PermisData.get(p.server).ranges().get(name);
        if (r == null) return -1;
        Session tmp = new Session();
        ServerLevel lvl = Util.level(p.server, r.dim);
        if (lvl != null) scanTargets(lvl, r, tmp);
        List<Marker> list = new ArrayList<>();
        list.add(Marker.box(r.min.getX(), r.min.getY(), r.min.getZ(), r.max.getX() + 1, r.max.getY() + 1, r.max.getZ() + 1,
                0xFFFFE066, "Zone de tir : " + name, 0));
        for (BlockPos t : tmp.targets) {
            if (list.size() > 65) break;
            list.add(Marker.box(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1, TARGET, "", 0));
        }
        Network.sendMarkers(p, "preview", 20 * 30, list);
        return tmp.targets.size();
    }

    /** Liste ordonnée des zones (pour les commandes). */
    public static Map<String, PermisData.Range> ranges(MinecraftServer s) {
        return new LinkedHashMap<>(PermisData.get(s).ranges());
    }
}
