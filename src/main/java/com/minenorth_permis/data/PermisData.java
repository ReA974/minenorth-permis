package com.minenorth_permis.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Permis des joueurs, points, cooldowns, parcours de conduite et zones de tir. Sauvegardé avec le monde. */
public class PermisData extends SavedData {
    private static final String NAME = "minenorth_permis";
    public static final long PERMANENT = Long.MAX_VALUE;

    private final Map<UUID, Holder> holders = new HashMap<>();
    private final Map<String, Course> courses = new LinkedHashMap<>();
    private final Map<String, Range> ranges = new LinkedHashMap<>();

    public static PermisData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PermisData::load, PermisData::new, NAME);
    }

    // ------------------------------------------------------------------ joueurs

    public static class Holder {
        public String name = "";
        /** licence -> expiration (epoch ms), PERMANENT si sans fin. */
        public final Map<String, Long> licences = new HashMap<>();
        /** licence -> date d'obtention (epoch ms). */
        public final Map<String, Long> issued = new HashMap<>();
        /** -1 = valeur par défaut (max). */
        public int points = -1;
        /** clé (ex. "test:conduire", "tir") -> fin du cooldown (epoch ms). */
        public final Map<String, Long> cooldowns = new HashMap<>();
        /** Numéro de la dernière carte émise par type de carte : une carte plus ancienne est annulée (perdue / volée). */
        public final Map<String, Integer> cardSerials = new HashMap<>();
        /** Position où renvoyer le joueur s'il s'est déconnecté pendant un test. */
        public Loc pendingReturn;
    }

    public Holder holder(UUID id) {
        return holders.computeIfAbsent(id, k -> new Holder());
    }

    public Holder peek(UUID id) {
        return holders.get(id);
    }

    public Map<UUID, Holder> holders() {
        return holders;
    }

    // ------------------------------------------------------------------ parcours & zones

    public static class Course {
        public Loc start;
        public final List<Vec3> checkpoints = new ArrayList<>();
        /** 0 = valeurs par défaut du test. */
        public int timeLimit;
        public double radius;
    }

    public static class Range {
        public String dim;
        public BlockPos min, max;

        public boolean contains(String d, BlockPos p) {
            return dim.equals(d) && p.getX() >= min.getX() && p.getX() <= max.getX()
                    && p.getY() >= min.getY() && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
        }

        public Vec3 center() {
            return new Vec3((min.getX() + max.getX() + 1) / 2.0, (min.getY() + max.getY() + 1) / 2.0, (min.getZ() + max.getZ() + 1) / 2.0);
        }
    }

    public Course course(String test) {
        return courses.get(test);
    }

    public Course courseOrCreate(String test) {
        return courses.computeIfAbsent(test, k -> new Course());
    }

    public void removeCourse(String test) {
        courses.remove(test);
    }

    public Map<String, Range> ranges() {
        return ranges;
    }

    // ------------------------------------------------------------------ position sérialisable

    public record Loc(String dim, double x, double y, double z, float yaw, float pitch) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dim);
            t.putDouble("x", x);
            t.putDouble("y", y);
            t.putDouble("z", z);
            t.putFloat("yaw", yaw);
            t.putFloat("pitch", pitch);
            return t;
        }

        static Loc load(CompoundTag t) {
            return new Loc(t.getString("dim"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
        }

        public Vec3 vec() {
            return new Vec3(x, y, z);
        }
    }

    // ------------------------------------------------------------------ NBT

    public static PermisData load(CompoundTag tag) {
        PermisData d = new PermisData();
        ListTag hl = tag.getList("holders", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) {
            CompoundTag t = hl.getCompound(i);
            Holder h = new Holder();
            h.name = t.getString("name");
            h.points = t.contains("points") ? t.getInt("points") : -1;
            CompoundTag lic = t.getCompound("licences");
            for (String k : lic.getAllKeys()) h.licences.put(k, lic.getLong(k));
            CompoundTag iss = t.getCompound("issued");
            for (String k : iss.getAllKeys()) h.issued.put(k, iss.getLong(k));
            CompoundTag cd = t.getCompound("cooldowns");
            for (String k : cd.getAllKeys()) h.cooldowns.put(k, cd.getLong(k));
            if (t.contains("return")) h.pendingReturn = Loc.load(t.getCompound("return"));
            CompoundTag cs = t.getCompound("cardSerials");
            for (String k : cs.getAllKeys()) h.cardSerials.put(k, cs.getInt(k));
            d.holders.put(t.getUUID("id"), h);
        }
        CompoundTag cs = tag.getCompound("courses");
        for (String k : cs.getAllKeys()) {
            CompoundTag t = cs.getCompound(k);
            Course c = new Course();
            if (t.contains("start")) c.start = Loc.load(t.getCompound("start"));
            c.timeLimit = t.getInt("timeLimit");
            c.radius = t.getDouble("radius");
            ListTag cp = t.getList("checkpoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < cp.size(); i++) {
                CompoundTag p = cp.getCompound(i);
                c.checkpoints.add(new Vec3(p.getDouble("x"), p.getDouble("y"), p.getDouble("z")));
            }
            d.courses.put(k, c);
        }
        CompoundTag rs = tag.getCompound("ranges");
        for (String k : rs.getAllKeys()) {
            CompoundTag t = rs.getCompound(k);
            Range r = new Range();
            r.dim = t.getString("dim");
            r.min = NbtUtils.readBlockPos(t.getCompound("min"));
            r.max = NbtUtils.readBlockPos(t.getCompound("max"));
            d.ranges.put(k, r);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag hl = new ListTag();
        holders.forEach((id, h) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", h.name);
            if (h.points >= 0) t.putInt("points", h.points);
            CompoundTag lic = new CompoundTag();
            h.licences.forEach(lic::putLong);
            t.put("licences", lic);
            CompoundTag iss = new CompoundTag();
            h.issued.forEach(iss::putLong);
            t.put("issued", iss);
            CompoundTag cd = new CompoundTag();
            h.cooldowns.forEach(cd::putLong);
            t.put("cooldowns", cd);
            if (h.pendingReturn != null) t.put("return", h.pendingReturn.save());
            CompoundTag cs = new CompoundTag();
            h.cardSerials.forEach(cs::putInt);
            t.put("cardSerials", cs);
            hl.add(t);
        });
        tag.put("holders", hl);

        CompoundTag cs = new CompoundTag();
        courses.forEach((k, c) -> {
            CompoundTag t = new CompoundTag();
            if (c.start != null) t.put("start", c.start.save());
            t.putInt("timeLimit", c.timeLimit);
            t.putDouble("radius", c.radius);
            ListTag cp = new ListTag();
            for (Vec3 v : c.checkpoints) {
                CompoundTag p = new CompoundTag();
                p.putDouble("x", v.x);
                p.putDouble("y", v.y);
                p.putDouble("z", v.z);
                cp.add(p);
            }
            t.put("checkpoints", cp);
            cs.put(k, t);
        });
        tag.put("courses", cs);

        CompoundTag rs = new CompoundTag();
        ranges.forEach((k, r) -> {
            CompoundTag t = new CompoundTag();
            t.putString("dim", r.dim);
            t.put("min", NbtUtils.writeBlockPos(r.min));
            t.put("max", NbtUtils.writeBlockPos(r.max));
            rs.put(k, t);
        });
        tag.put("ranges", rs);
        return tag;
    }
}
