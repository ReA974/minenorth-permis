package com.minenorth_permis;

import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.data.PermisData.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Logique des permis/licences : validité, attribution, retrait, points et cooldowns. Thread serveur uniquement. */
public final class Licences {
    private Licences() {}

    public static String formatDate(long ms) {
        if (ms == PermisData.PERMANENT) return "jamais";
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date(ms));
    }

    /** Possédé ET pas expiré. Révoque automatiquement s'il est expiré. */
    public static boolean isValid(MinecraftServer s, UUID id, String licence) {
        PermisData d = PermisData.get(s);
        Holder h = d.peek(id);
        if (h == null) return false;
        Long exp = h.licences.get(licence);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            revoke(s, id, licence);
            return false;
        }
        return true;
    }

    /** Date d'expiration, ou null si non possédé. */
    public static Long expiry(MinecraftServer s, UUID id, String licence) {
        Holder h = PermisData.get(s).peek(id);
        return h == null ? null : h.licences.get(licence);
    }

    public static Long issued(MinecraftServer s, UUID id, String licence) {
        Holder h = PermisData.get(s).peek(id);
        return h == null ? null : h.issued.get(licence);
    }

    /**
     * Donne (ou renouvelle) une licence. days &lt; 0 : durée de la config ; 0 : permanent.
     * Renvoie la date d'expiration.
     */
    public static long grant(MinecraftServer s, UUID id, String name, String licence, int days) {
        PermisData d = PermisData.get(s);
        Holder h = d.holder(id);
        if (name != null && !name.isEmpty()) h.name = name;
        int dd = days < 0 ? PermisConfig.get().validityDays : days;
        long now = System.currentTimeMillis();
        long exp = dd <= 0 ? PermisData.PERMANENT : now + dd * 86_400_000L;
        h.licences.put(licence, exp);
        h.issued.put(licence, now);
        d.setDirty();
        PermisConfig.Licence def = PermisConfig.get().licence(licence);
        // Nouveau permis de conduire après un retrait à 0 point : on repart avec tous les points
        PermisConfig.CardGroup g = def == null ? null : PermisConfig.get().group(def.cardGroup);
        if (g != null && g.points && h.points == 0) h.points = -1;
        if (def != null) runCommands(s, def.onGrant, h.name, id, licence);
        return exp;
    }

    public static boolean revoke(MinecraftServer s, UUID id, String licence) {
        PermisData d = PermisData.get(s);
        Holder h = d.peek(id);
        if (h == null || h.licences.remove(licence) == null) return false;
        h.issued.remove(licence);
        d.setDirty();
        PermisConfig.Licence def = PermisConfig.get().licence(licence);
        if (def != null) runCommands(s, def.onRevoke, h.name, id, licence);
        return true;
    }

    /** Retire les licences expirées (appelé toutes les minutes). */
    public static void sweep(MinecraftServer s) {
        long now = System.currentTimeMillis();
        List<UUID> ids = new ArrayList<>();
        List<String> lics = new ArrayList<>();
        for (Map.Entry<UUID, Holder> e : PermisData.get(s).holders().entrySet()) {
            for (Map.Entry<String, Long> l : e.getValue().licences.entrySet()) {
                if (now > l.getValue()) {
                    ids.add(e.getKey());
                    lics.add(l.getKey());
                }
            }
            Iterator<Map.Entry<String, Long>> it = e.getValue().cooldowns.entrySet().iterator();
            while (it.hasNext()) if (it.next().getValue() < now) it.remove();
        }
        for (int i = 0; i < ids.size(); i++) {
            revoke(s, ids.get(i), lics.get(i));
            ServerPlayer p = s.getPlayerList().getPlayer(ids.get(i));
            PermisConfig.Licence def = PermisConfig.get().licence(lics.get(i));
            if (p != null) Msg.warn(p, "Ton " + (def != null ? def.name : lics.get(i)) + " a expiré.");
        }
    }

    // ------------------------------------------------------------------ points

    public static int points(MinecraftServer s, UUID id) {
        Holder h = PermisData.get(s).peek(id);
        int max = PermisConfig.get().maxPoints;
        return h == null || h.points < 0 ? max : Math.min(h.points, max);
    }

    public static int setPoints(MinecraftServer s, UUID id, int pts) {
        PermisData d = PermisData.get(s);
        int before = points(s, id);
        int v = Math.max(0, Math.min(PermisConfig.get().maxPoints, pts));
        d.holder(id).points = v;
        d.setDirty();
        if (v == 0 && before > 0 && PermisConfig.get().revokeAtZeroPoints) revokePointLicences(s, id);
        return v;
    }

    /** 0 point : retrait de tous les permis à points (voiture, poids lourd, moto…). */
    public static void revokePointLicences(MinecraftServer s, UUID id) {
        List<String> lost = new ArrayList<>();
        for (PermisConfig.CardGroup g : PermisConfig.get().cardGroups) {
            if (!g.points) continue;
            for (PermisConfig.Licence l : PermisConfig.get().inGroup(g.id)) {
                if (revoke(s, id, l.id)) lost.add(l.code.isEmpty() ? l.name : l.code + " (" + l.name + ")");
            }
        }
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p != null && !lost.isEmpty()) {
            Msg.err(p, "Tu n'as plus aucun point : ton permis de conduire est annulé (" + String.join(", ", lost) + ").");
            Msg.warn(p, "Tu devras repasser les examens pour conduire à nouveau.");
        }
        MinenorthPermis.LOG.info("[Permis] 0 point pour {} : permis retirés {}", id, lost);
    }

    // ------------------------------------------------------------------ cooldowns

    /** Millisecondes restantes de cooldown (0 si aucun). */
    public static long cooldown(MinecraftServer s, UUID id, String key) {
        Holder h = PermisData.get(s).peek(id);
        if (h == null) return 0;
        Long until = h.cooldowns.get(key);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public static void setCooldown(MinecraftServer s, UUID id, String key, int minutes) {
        if (minutes <= 0) return;
        PermisData d = PermisData.get(s);
        d.holder(id).cooldowns.put(key, System.currentTimeMillis() + minutes * 60_000L);
        d.setDirty();
    }

    public static String formatDuration(long ms) {
        long sec = (ms + 999) / 1000;
        if (sec >= 3600) return (sec / 3600) + " h " + String.format("%02d", (sec % 3600) / 60) + " min";
        if (sec >= 60) return (sec / 60) + " min " + String.format("%02d", sec % 60) + " s";
        return sec + " s";
    }

    // ------------------------------------------------------------------ commandes de config

    public static void runCommands(MinecraftServer s, List<String> cmds, String name, UUID id, String licence) {
        if (cmds == null) return;
        for (String c : cmds) {
            if (c == null || c.isBlank()) continue;
            String cmd = c.replace("{player}", name == null ? "" : name).replace("{uuid}", id.toString()).replace("{licence}", licence);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            try {
                s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), cmd);
            } catch (Exception e) {
                MinenorthPermis.LOG.warn("[Permis] Commande échouée : {}", cmd, e);
            }
        }
    }
}
