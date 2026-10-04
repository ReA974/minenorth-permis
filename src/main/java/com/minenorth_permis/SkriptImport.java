package com.minenorth_permis;

import com.minenorth_permis.data.PermisData;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import (best effort) des permis stockés par les anciens scripts dans plugins/Skript/variables.csv :
 * {<licence>.<uuid ou pseudo>} = true  et  {permispoints.<uuid>} = nombre.
 * Les dates d'expiration Skript ne sont pas lisibles : chaque permis importé repart sur la durée de la config.
 */
public final class SkriptImport {
    private static final Pattern LINE = Pattern.compile("^(\"(?:[^\"]|\"\")*\"|[^,]*),\\s*([^,]+),\\s*(.*)$");

    private SkriptImport() {}

    public static String run(MinecraftServer s) {
        Path f = s.getServerDirectory().toPath().resolve("plugins").resolve("Skript").resolve("variables.csv");
        if (!Files.exists(f)) return "Fichier introuvable : " + f;
        List<String> lines;
        try {
            lines = Files.readAllLines(f, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Lecture impossible : " + e.getMessage();
        }
        PermisData data = PermisData.get(s);
        int licences = 0, points = 0, unknown = 0;
        for (String raw : lines) {
            if (raw.isBlank() || raw.startsWith("#")) continue;
            Matcher m = LINE.matcher(raw.trim());
            if (!m.matches()) continue;
            String name = m.group(1);
            if (name.startsWith("\"")) name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
            String type = m.group(2).trim();
            String value = m.group(3).trim();
            int dot = name.indexOf('.');
            if (dot <= 0 || name.contains("::")) continue;
            String key = name.substring(0, dot);
            String who = name.substring(dot + 1);

            if (key.equals("permispoints")) {
                Long v = number(type, value);
                Optional<UUID> id = id(s, who);
                if (v != null && id.isPresent()) {
                    data.holder(id.get()).points = (int) Math.max(0, Math.min(PermisConfig.get().maxPoints, v));
                    points++;
                }
                continue;
            }
            PermisConfig.Licence def = PermisConfig.get().licence(key);
            if (def == null || !type.equals("boolean") || !value.equalsIgnoreCase("01")) continue;
            Optional<UUID> id = id(s, who);
            if (id.isEmpty()) {
                unknown++;
                continue;
            }
            if (Licences.expiry(s, id.get(), def.id) != null) continue;   // déjà présent
            String pseudo = who.length() == 36 ? s.getProfileCache() != null
                    ? s.getProfileCache().get(id.get()).map(GameProfile::getName).orElse("") : "" : who;
            Licences.grant(s, id.get(), pseudo, def.id, -1);
            licences++;
        }
        data.setDirty();
        return "Import Skript : " + licences + " permis/licences, " + points + " soldes de points"
                + (unknown > 0 ? ", " + unknown + " joueur(s) introuvable(s)" : "") + ".";
    }

    private static Optional<UUID> id(MinecraftServer s, String who) {
        try {
            if (who.length() == 36) return Optional.of(UUID.fromString(who));
        } catch (IllegalArgumentException ignored) {
        }
        if (s.getProfileCache() == null) return Optional.empty();
        return s.getProfileCache().get(who).map(GameProfile::getId);
    }

    /** Valeurs numériques Skript : hexadécimal big-endian (long 8 octets, integer 4, double 8). */
    private static Long number(String type, String hex) {
        try {
            byte[] b = new byte[hex.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(b);
            return switch (type) {
                case "long" -> b.length >= 8 ? buf.getLong() : null;
                case "integer" -> b.length >= 4 ? (long) buf.getInt() : null;
                case "double" -> b.length >= 8 ? Math.round(buf.getDouble()) : null;
                case "float" -> b.length >= 4 ? (long) Math.round(buf.getFloat()) : null;
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
