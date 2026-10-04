package com.minenorth_permis.util;

import com.minenorth_permis.MinenorthPermis;
import com.minenorth_permis.data.PermisData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class Util {
    /** Tag posé sur les objets prêtés pendant une épreuve (retirés à la fin, non jetables). */
    public static final String TEST_TAG = "MinenorthPermisTest";

    private Util() {}

    public static String dim(ServerPlayer p) {
        return p.level().dimension().location().toString();
    }

    public static PermisData.Loc loc(ServerPlayer p) {
        return new PermisData.Loc(dim(p), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
    }

    public static ServerLevel level(MinecraftServer s, String dim) {
        for (ServerLevel l : s.getAllLevels()) if (l.dimension().location().toString().equals(dim)) return l;
        return null;
    }

    /** Téléporte (même entre dimensions / mondes Multiverse). */
    public static boolean teleport(ServerPlayer p, PermisData.Loc l) {
        if (l == null) return false;
        ServerLevel lvl = level(p.server, l.dim());
        if (lvl == null) return false;
        p.stopRiding();
        p.teleportTo(lvl, l.x(), l.y(), l.z(), l.yaw(), l.pitch());
        return true;
    }

    /** "mod:item", "mod:item{nbt}", "mod:item*3" ou "mod:item{nbt}*3". null si invalide. */
    public static ItemStack parseItem(String spec, String extraNbt) {
        if (spec == null || spec.isBlank()) return null;
        try {
            String s = spec.trim();
            int count = 1;
            int star = s.lastIndexOf('*');
            if (star > 0 && star > s.lastIndexOf('}')) {
                count = Integer.parseInt(s.substring(star + 1).trim());
                s = s.substring(0, star).trim();
            }
            String nbt = extraNbt == null ? "" : extraNbt.trim();
            int brace = s.indexOf('{');
            if (brace > 0) {
                nbt = s.substring(brace);
                s = s.substring(0, brace);
            }
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(s.trim()));
            if (item == null || item == Items.AIR) {
                MinenorthPermis.LOG.warn("[Permis] Item inconnu : {}", s);
                return null;
            }
            ItemStack st = new ItemStack(item, Math.max(1, count));
            if (!nbt.isEmpty()) {
                CompoundTag t = TagParser.parseTag(nbt);
                st.setTag(t);
            }
            return st;
        } catch (Exception e) {
            MinenorthPermis.LOG.warn("[Permis] Item invalide : {}", spec, e);
            return null;
        }
    }

    public static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
    }

    public static boolean isTestItem(ItemStack s) {
        return !s.isEmpty() && s.hasTag() && s.getTag().getBoolean(TEST_TAG);
    }

    public static ItemStack tagTest(ItemStack s) {
        s.getOrCreateTag().putBoolean(TEST_TAG, true);
        return s;
    }

    /** Retire de l'inventaire (et du curseur) tous les objets qui correspondent. */
    public static int removeAll(ServerPlayer p, Predicate<ItemStack> match) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (match.test(inv.getItem(i))) {
                n += inv.getItem(i).getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (match.test(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        return n;
    }

    /** Retire au plus `count` items de ce type. */
    public static void removeSome(ServerPlayer p, Item item, int count) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                int k = Math.min(count, s.getCount());
                s.shrink(k);
                count -= k;
            }
        }
    }

    public static Entity findEntity(MinecraftServer s, UUID id) {
        if (id == null) return null;
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    public static boolean matchesType(Entity e, List<String> types) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (k == null) return false;
        String key = k.toString();
        for (String t : types) {
            if (t.endsWith(":*") ? k.getNamespace().equals(t.substring(0, t.length() - 2)) : key.equals(t)) return true;
        }
        return false;
    }

    public static double horizontalDist(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
