package com.minenorth_permis.admin;

import com.minenorth_permis.Licences;
import com.minenorth_permis.MinenorthPermis;
import com.minenorth_permis.Msg;
import com.minenorth_permis.PermisConfig;
import com.minenorth_permis.Shop;
import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.items.PermisCardItem;
import com.minenorth_permis.net.AdminActionPacket;
import com.minenorth_permis.net.AdminStatePacket;
import com.minenorth_permis.net.Network;
import com.minenorth_permis.tests.DrivingTests;
import com.minenorth_permis.tests.ShootingTests;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Menu staff : gestion des permis de chaque joueur (validé côté serveur, ops uniquement). */
public final class AdminService {
    private AdminService() {}

    public static void open(ServerPlayer staff, UUID selected) {
        Network.send(staff, AdminStatePacket.compute(staff.server, selected, true, "", true));
    }

    public static void handle(ServerPlayer staff, AdminActionPacket m) {
        MinecraftServer s = staff.server;
        UUID id = m.target;
        String msg = "";
        boolean ok = true;
        PermisConfig.Licence def = PermisConfig.get().licence(m.licence);
        ServerPlayer target = s.getPlayerList().getPlayer(id);
        String name = nameOf(s, id);

        switch (m.op) {
            case SELECT, REFRESH -> {}
            case GIVE -> {
                if (def == null) break;
                int days = m.value;                         // -1 = durée de la config, 0 = permanent
                if (target != null) Shop.obtain(target, def.id, days);
                else Licences.grant(s, id, name, def.id, days);
                if (target != null) Msg.ok(target, "Le staff t'a délivré : " + def.name + ".");
                msg = def.name + " donné à " + name + (days == 0 ? " (permanent)" : days > 0 ? " (" + days + " j)" : "")
                        + (target == null ? " — carte à refaire à sa connexion (bouton Carte)." : ".");
                log(staff, "donne " + def.id + " à " + name);
            }
            case REVOKE -> {
                if (def == null) break;
                if (Licences.revoke(s, id, def.id)) {
                    if (target != null) Msg.err(target, "Ton " + def.name + " t'a été retiré par le staff.");
                    msg = def.name + " retiré à " + name + ".";
                    log(staff, "retire " + def.id + " à " + name);
                } else {
                    msg = name + " n'a pas ce permis.";
                    ok = false;
                }
            }
            case CARD -> {
                if (def == null) break;
                if (target == null) {
                    msg = "Le joueur doit être connecté pour recevoir une carte.";
                    ok = false;
                } else if (!Licences.isValid(s, id, def.id)) {
                    msg = "Ce permis n'est pas valide : donne-le d'abord.";
                    ok = false;
                } else {
                    ItemStack c = PermisCardItem.create(target, def.id);
                    if (!target.getInventory().add(c)) target.drop(c, false);
                    Msg.ok(target, "Le staff t'a remis une nouvelle carte : " + def.name + ".");
                    msg = "Nouvelle carte remise à " + name + " (l'ancienne est annulée).";
                }
            }
            case POINTS_SET -> {
                int v = Licences.setPoints(s, id, m.value);
                if (target != null) Msg.warn(target, "Points de permis : " + v + "/" + PermisConfig.get().maxPoints);
                msg = name + " : " + v + "/" + PermisConfig.get().maxPoints + " points" + (v == 0 && PermisConfig.get().revokeAtZeroPoints ? " — permis de conduire retiré." : ".");
                log(staff, "points de " + name + " -> " + v);
            }
            case CLEAR_COOLDOWNS -> {
                PermisData d = PermisData.get(s);
                PermisData.Holder h = d.peek(id);
                if (h != null) h.cooldowns.clear();
                d.setDirty();
                msg = "Délais d'attente des examens effacés pour " + name + ".";
            }
            case STOP_TEST -> {
                boolean stopped = target != null && (DrivingTests.stop(target) | ShootingTests.stop(target));
                msg = stopped ? "Épreuve de " + name + " arrêtée." : name + " ne passe aucune épreuve.";
                ok = stopped;
            }
        }
        Network.send(staff, AdminStatePacket.compute(s, id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0 ? null : id, false, msg, ok));
    }

    private static String nameOf(MinecraftServer s, UUID id) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        PermisData.Holder h = PermisData.get(s).peek(id);
        return h != null && !h.name.isEmpty() ? h.name : id.toString().substring(0, 8);
    }

    private static void log(ServerPlayer staff, String what) {
        MinenorthPermis.LOG.info("[Permis][admin] {} {}", staff.getGameProfile().getName(), what);
    }
}
