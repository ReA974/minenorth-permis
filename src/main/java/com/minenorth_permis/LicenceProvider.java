package com.minenorth_permis;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Fournit les permis et licences aux autres mods via MineNorth API. */
final class LicenceProvider implements fr.minenorth.api.LicenceService {
    @Override public boolean has(MinecraftServer s, UUID player, String licence) { return Licences.isValid(s, player, licence); }

    @Override
    public Long expiry(MinecraftServer s, UUID player, String licence) {
        return Licences.isValid(s, player, licence) ? Licences.expiry(s, player, licence) : null;
    }

    @Override public int points(MinecraftServer s, UUID player) { return Licences.points(s, player); }

    @Override
    public int removePoints(MinecraftServer s, UUID player, int amount) {
        return Licences.setPoints(s, player, Licences.points(s, player) - amount);
    }

    @Override public void grant(ServerPlayer p, String licence, int days) { PermisApi.grant(p, licence, days); }

    @Override public boolean revoke(MinecraftServer s, UUID player, String licence) { return Licences.revoke(s, player, licence); }
}
