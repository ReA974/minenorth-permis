package com.minenorth_permis.client;

import com.minenorth_permis.MinenorthPermis;
import com.minenorth_permis.items.PermisCardItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Charte graphique MineNorth (reprise de l'ATM EuroBank). */
@OnlyIn(Dist.CLIENT)
public final class MnStyle {
    public static final int FRAME = 0xFF0E0E10;
    public static final int BLUE = 0xFF161048;
    public static final int LIST = 0xFF0E0A34;
    public static final int HOVER = 0xFF2E2480;
    public static final int CYAN = 0xFF20AAEB;
    public static final int DARK = 0xFF4A3CB4;
    public static final int PINK = 0xFFC83CF0;
    public static final int TEXT = 0xFFCFE3FF;
    public static final int WARN = 0xFFFFE066;
    public static final int ALERT = 0xFFFF6A9A;
    public static final int OK = 0xFF6CFF9A;
    public static final int MUTED = 0xFF8FA8E0;
    public static final ResourceLocation LOGO = new ResourceLocation(MinenorthPermis.MODID, "textures/gui/logo.png");

    private MnStyle() {}

    public static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 35);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 35);
        int b = Math.min(255, (c & 0xFF) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    public static String date(long ms) {
        if (ms == Long.MAX_VALUE) return "jamais";
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date(ms));
    }

    public static String euros(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (a / 100) + (a % 100 == 0 ? "" : "," + String.format("%02d", a % 100)) + " €";
    }

    public static String duration(long ms) {
        long sec = (ms + 999) / 1000;
        if (sec >= 3600) return (sec / 3600) + " h " + String.format("%02d", (sec % 3600) / 60);
        if (sec >= 60) return (sec / 60) + " min";
        return sec + " s";
    }

    /** Fond + cadre + logo + titre, comme les écrans EuroBank. */
    public static void panel(GuiGraphics g, int left, int top, int w, int h, String title) {
        Font font = Minecraft.getInstance().font;
        g.fill(left - 3, top - 3, left + w + 3, top + h + 3, FRAME);
        g.fill(left, top, left + w, top + h, BLUE);
        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 4, 24, 24, 0, 0, 96, 96, 96, 96);
        scaled(g, bold(title), left + 40, top + 11, 1.6f, 0xFFFFFFFF);
    }

    public static void scaled(GuiGraphics g, Component c, int x, int y, float s, int color) {
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        g.drawString(font, c, Math.round(x / s), Math.round(y / s), color, false);
        g.pose().popPose();
    }

    public static void item(GuiGraphics g, ItemStack st, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
    }

    public static ItemStack cardIcon(String style) {
        return PermisCardItem.preview(style);
    }

    /** Jauge de points en pastilles. */
    public static void points(GuiGraphics g, int x, int y, int pts, int max) {
        for (int i = 0; i < max; i++) {
            int c = i < pts ? (pts <= 3 ? ALERT : pts <= 6 ? WARN : OK) : 0xFF2A2468;
            g.fill(x + i * 9, y, x + i * 9 + 7, y + 7, c);
        }
    }
}
