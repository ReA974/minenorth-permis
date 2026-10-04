package com.minenorth_permis.client;

import com.minenorth_permis.net.HudPacket;
import com.minenorth_permis.net.MarkersPacket;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Panneau en haut de l'écran pendant un test : progression, chrono, flèche + distance vers le prochain anneau. */
@OnlyIn(Dist.CLIENT)
public final class HudOverlay implements IGuiOverlay {
    public static final HudOverlay INSTANCE = new HudOverlay();
    private static final int PW = 212;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int sw, int sh) {
        HudPacket h = ClientState.hud;
        Minecraft mc = Minecraft.getInstance();
        if (h == null || mc.player == null || mc.level == null || mc.options.hideGui) return;
        Font font = mc.font;
        int x = (sw - PW) / 2, y = 6;
        MarkersPacket.Marker nav = null;
        for (MarkersPacket.Marker m : ClientState.visible(mc.level.dimension().location().toString())) {
            if (m.has(MarkersPacket.NAV)) {
                nav = m;
                break;
            }
        }
        int ph = nav != null ? 64 : 52;

        g.fill(x - 2, y - 2, x + PW + 2, y + ph + 2, 0xC00E0E10);
        g.fill(x, y, x + PW, y + ph, 0xE0161048);
        g.fill(x, y, x + PW, y + 2, h.accent);
        g.drawString(font, MnStyle.bold(font.plainSubstrByWidth(h.title, 140)), x + 8, y + 7, 0xFFFFFFFF, false);

        // chrono
        if (h.seconds >= 0) {
            long elapsed = (System.currentTimeMillis() - ClientState.hudReceived) / 1000;
            int s = (int) Math.max(0, h.seconds - elapsed);
            String txt = (s / 60) + ":" + String.format("%02d", s % 60);
            boolean urgent = s <= 15;
            int col = urgent ? ((System.currentTimeMillis() / 400) % 2 == 0 ? MnStyle.ALERT : 0xFFFFFFFF) : 0xFFFFFFFF;
            g.pose().pushPose();
            g.pose().translate(x + PW - 8 - font.width(txt) * 1.5f, y + 5, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.drawString(font, MnStyle.bold(txt), 0, 0, col, false);
            g.pose().popPose();
        }

        g.drawString(font, font.plainSubstrByWidth(h.line, PW - 16), x + 8, y + 21, MnStyle.TEXT, false);

        // progression
        if (h.total > 0) {
            int bx = x + 8, bw = PW - 16, by = y + 33;
            if (h.total <= 24) {
                int gap = 2, seg = (bw - gap * (h.total - 1)) / h.total;
                for (int i = 0; i < h.total; i++) {
                    int c = i < h.progress ? MnStyle.OK : i == h.progress ? h.accent : 0xFF2A2468;
                    g.fill(bx + i * (seg + gap), by, bx + i * (seg + gap) + seg, by + 5, c);
                }
            } else {
                g.fill(bx, by, bx + bw, by + 5, 0xFF2A2468);
                g.fill(bx, by, bx + bw * h.progress / h.total, by + 5, MnStyle.OK);
            }
        }

        // navigation vers l'anneau
        if (nav != null) {
            double dx = nav.x() - mc.player.getX(), dz = nav.z() - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees(target - mc.player.getYRot());
            int ax = x + 16, ay = y + 49;
            g.pose().pushPose();
            g.pose().translate(ax, ay, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            g.pose().scale(1.6f, 1.6f, 1f);
            g.drawString(font, "▲", -font.width("▲") / 2, -4, nav.color(), false);
            g.pose().popPose();
            String d = dist < 1000 ? (int) dist + " m" : String.format("%.1f km", dist / 1000);
            g.drawString(font, MnStyle.bold(d), x + 30, y + 45, 0xFFFFFFFF, false);
            String lbl = nav.label().isEmpty() ? "" : "→ " + nav.label();
            g.drawString(font, lbl, x + 30 + font.width(MnStyle.bold(d)) + 6, y + 45, nav.color(), false);
        }

        if (!h.hint.isEmpty()) {
            int hy = y + ph - 11;
            g.drawString(font, font.plainSubstrByWidth(h.hint, PW - 16), x + PW - 8 - Math.min(PW - 16, font.width(h.hint)), hy, MnStyle.MUTED, false);
        }
    }
}
