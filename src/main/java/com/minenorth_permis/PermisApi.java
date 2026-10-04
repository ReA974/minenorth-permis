package com.minenorth_permis;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * API publique pour les autres mods MineNorth (garage, boutiques…). Thread serveur uniquement.
 * Ex. : if (!PermisApi.hasLicence(player, "permisconduire")) refuser l'achat.
 */
public final class PermisApi {
    private PermisApi() {}

    public static boolean hasLicence(ServerPlayer p, String licenceId) {
        return Licences.isValid(p.server, p.getUUID(), licenceId);
    }

    public static boolean hasLicence(MinecraftServer s, UUID player, String licenceId) {
        return Licences.isValid(s, player, licenceId);
    }

    /** Date d'expiration (epoch ms) ou null. Long.MAX_VALUE = permanent. */
    public static Long expiry(ServerPlayer p, String licenceId) {
        return Licences.isValid(p.server, p.getUUID(), licenceId) ? Licences.expiry(p.server, p.getUUID(), licenceId) : null;
    }

    public static int points(ServerPlayer p) {
        return Licences.points(p.server, p.getUUID());
    }

    /** Retire des points (police). Renvoie le nouveau total. */
    public static int removePoints(ServerPlayer p, int amount) {
        return Licences.setPoints(p.server, p.getUUID(), Licences.points(p.server, p.getUUID()) - amount);
    }

    public static void grant(ServerPlayer p, String licenceId, int days) {
        Licences.grant(p.server, p.getUUID(), p.getGameProfile().getName(), licenceId, days);
    }

    public static boolean revoke(ServerPlayer p, String licenceId) {
        return Licences.revoke(p.server, p.getUUID(), licenceId);
    }
}
