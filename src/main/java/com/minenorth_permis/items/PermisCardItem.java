package com.minenorth_permis.items;

import com.minenorth_permis.Licences;
import com.minenorth_permis.Msg;
import com.minenorth_permis.PermisConfig;
import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.net.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Carte de permis / licence physique (façon carte d'identité).
 * Clic droit : affiche la carte. Clic droit sur un joueur : la lui montre (contrôle de police).
 */
public class PermisCardItem extends Item {
    public static final String[] STYLES = {"permis", "licence", "arme", "peche"};

    public PermisCardItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static int styleIndex(ItemStack s) {
        CompoundTag t = s.getTag();
        String st = t == null ? "permis" : t.getString("Style");
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(st)) return i;
        return 0;
    }

    public static ItemStack preview(String style) {
        ItemStack s = new ItemStack(ModItems.CARD.get());
        s.getOrCreateTag().putString("Style", style);
        return s;
    }

    public static String styleFor(PermisConfig.Licence l) {
        if (l == null) return "licence";
        PermisConfig.Category c = PermisConfig.get().category(l.category);
        return c != null && c.cardStyle != null ? c.cardStyle : "licence";
    }

    /** Crée la carte d'un joueur pour une licence (ou la carte groupée dont elle fait partie). */
    public static ItemStack create(ServerPlayer p, String licence) {
        PermisConfig.Licence def = PermisConfig.get().licence(licence);
        PermisConfig.CardGroup g = def == null ? null : PermisConfig.get().group(def.cardGroup);
        if (g != null) {
            ItemStack s = new ItemStack(ModItems.CARD.get());
            CompoundTag t = s.getOrCreateTag();
            t.putString("Group", g.id);
            t.putString("Style", g.style);
            t.putUUID("Holder", p.getUUID());
            t.putString("HolderName", p.getGameProfile().getName());
            t.putInt("Serial", nextSerial(p, "g:" + g.id));
            refresh(s, p);
            return s;
        }
        ItemStack s = new ItemStack(ModItems.CARD.get());
        CompoundTag t = s.getOrCreateTag();
        t.putString("Licence", licence);
        t.putString("LicenceName", def != null ? def.name : licence);
        t.putString("Style", styleFor(def));
        t.putUUID("Holder", p.getUUID());
        t.putString("HolderName", p.getGameProfile().getName());
        Long iss = Licences.issued(p.server, p.getUUID(), licence);
        Long exp = Licences.expiry(p.server, p.getUUID(), licence);
        t.putLong("Issued", iss != null ? iss : System.currentTimeMillis());
        t.putLong("Expiry", exp != null ? exp : 0);
        t.putInt("Serial", nextSerial(p, "l:" + licence));
        s.setHoverName(Component.literal(def != null ? def.name : licence).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .withStyle(st -> st.withItalic(false)));
        return s;
    }

    /** Clé du type de carte : "g:conduire" (carte groupée) ou "l:parme". */
    public static String cardKey(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null) return "";
        return t.getString("Group").isEmpty() ? "l:" + t.getString("Licence") : "g:" + t.getString("Group");
    }

    /** Émet un nouveau numéro de carte : toutes les cartes précédentes de ce type deviennent annulées. */
    private static int nextSerial(ServerPlayer p, String key) {
        PermisData d = PermisData.get(p.server);
        PermisData.Holder h = d.holder(p.getUUID());
        int n = h.cardSerials.getOrDefault(key, 0) + 1;
        h.cardSerials.put(key, n);
        d.setDirty();
        return n;
    }

    /** Carte annulée : un duplicata plus récent a été émis (papiers déclarés perdus). */
    public static boolean isCancelled(ItemStack s, PermisData d) {
        UUID holder = holder(s);
        if (holder == null) return false;
        PermisData.Holder h = d.peek(holder);
        int current = h == null ? 0 : h.cardSerials.getOrDefault(cardKey(s), 0);
        return s.getTag().getInt("Serial") < current;
    }

    /** Le joueur a-t-il sur lui une carte non annulée de ce type ? */
    public static boolean hasActiveCard(ServerPlayer p, String key) {
        PermisData d = PermisData.get(p.server);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof PermisCardItem && p.getUUID().equals(holder(st)) && key.equals(cardKey(st)) && !isCancelled(st, d)) return true;
        }
        return false;
    }

    public static boolean isGroupCard(ItemStack s, String group, UUID holder) {
        CompoundTag t = s.getTag();
        return s.getItem() instanceof PermisCardItem && t != null && group.equalsIgnoreCase(t.getString("Group"))
                && t.hasUUID("Holder") && holder.equals(t.getUUID("Holder"));
    }

    /** Met à jour le nom et la liste des catégories (affichée dans l'infobulle) d'une carte groupée. */
    public static void refresh(ItemStack s, ServerPlayer p) {
        CompoundTag t = s.getOrCreateTag();
        PermisConfig.CardGroup g = PermisConfig.get().group(t.getString("Group"));
        if (g == null) return;
        StringBuilder codes = new StringBuilder();
        for (PermisConfig.Licence l : PermisConfig.get().inGroup(g.id)) {
            if (!Licences.isValid(p.server, p.getUUID(), l.id)) continue;
            if (codes.length() > 0) codes.append(" · ");
            codes.append(l.code.isEmpty() ? l.name : l.code);
        }
        t.putString("Codes", codes.toString());
        if (!t.contains("Issued")) t.putLong("Issued", System.currentTimeMillis());
        s.setHoverName(Component.literal(g.name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .withStyle(st -> st.withItalic(false)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (!s.hasTag() || !s.getTag().hasUUID("Holder")) {
                Msg.warn(sp, "Carte vierge.");
            } else {
                if (!s.getTag().getString("Group").isEmpty() && sp.getUUID().equals(s.getTag().getUUID("Holder"))) refresh(s, sp);
                Network.sendCard(sp, s, "");
            }
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack s, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ServerPlayer other) || !(player instanceof ServerPlayer sp)) {
            return target instanceof Player ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!s.hasTag() || !s.getTag().hasUUID("Holder")) return InteractionResult.PASS;
        Network.sendCard(other, s, sp.getGameProfile().getName());
        Msg.info(sp, "Tu montres ta carte à " + other.getGameProfile().getName() + ".");
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = s.getTag();
        if (t == null || !t.hasUUID("Holder")) {
            tip.add(Component.literal("Carte vierge").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!t.getString("Group").isEmpty()) {
            tip.add(Component.literal("―――――――――――――").withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.literal("Titulaire : ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(t.getString("HolderName")).withStyle(ChatFormatting.YELLOW)));
            String codes = t.getString("Codes");
            tip.add(Component.literal("Catégories : ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(codes.isEmpty() ? "aucune" : codes).withStyle(ChatFormatting.YELLOW)));
            tip.add(Component.literal("―――――――――――――").withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.literal("Clic droit : voir le permis et tes points").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Clic droit sur un joueur : le lui montrer").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        long exp = t.getLong("Expiry");
        tip.add(Component.literal("―――――――――――――").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.literal("Titulaire : ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(t.getString("HolderName")).withStyle(ChatFormatting.YELLOW)));
        tip.add(Component.literal("Délivré le : ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(Licences.formatDate(t.getLong("Issued"))).withStyle(ChatFormatting.YELLOW)));
        tip.add(Component.literal("Expire le : ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(exp == PermisData.PERMANENT ? "jamais" : Licences.formatDate(exp)).withStyle(ChatFormatting.YELLOW)));
        tip.add(Component.literal("―――――――――――――").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.literal("Clic droit : voir la carte").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Clic droit sur un joueur : la lui montrer").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** UUID du titulaire, ou null. */
    public static UUID holder(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.hasUUID("Holder") ? t.getUUID("Holder") : null;
    }
}
