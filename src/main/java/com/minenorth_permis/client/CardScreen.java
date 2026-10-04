package com.minenorth_permis.client;

import com.minenorth_permis.net.CardPacket;
import com.minenorth_permis.net.CardPacket.Line;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Vue "carte d'identité" : photo du titulaire, état vérifié en direct.
 * Carte groupée (permis de conduire) : une ligne par catégorie B / C / A, avec les points.
 */
@OnlyIn(Dist.CLIENT)
public class CardScreen extends Screen {
    private static final int W = 260, ROW = 13;
    private final CardPacket c;

    public CardScreen(CardPacket c) {
        super(Component.literal("Carte"));
        this.c = c;
    }

    private boolean grouped() {
        return c.lines.size() > 1 || (c.lines.size() == 1 && !c.lines.get(0).code().isEmpty());
    }

    private int height() {
        int body = grouped() ? 30 + c.lines.size() * ROW : 70;
        return 40 + Math.max(78, body) + (c.showPoints ? 22 : 8);
    }

    private int base() {
        return switch (c.style) {
            case "licence" -> 0xFFC9DDF7;
            case "arme" -> 0xFFF2C4BD;
            case "peche" -> 0xFFC2EEDF;
            default -> 0xFFF6C9D8;
        };
    }

    private int band() {
        return switch (c.style) {
            case "licence" -> MnStyle.DARK;
            case "arme" -> 0xFF9E2236;
            case "peche" -> 0xFF168A74;
            default -> 0xFF1F3C9C;
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int H = height();
        int l = (width - W) / 2, t = (height - H) / 2;
        if (!c.shownBy.isEmpty()) g.drawCenteredString(font, c.shownBy + " te présente sa carte", width / 2, t - 16, MnStyle.WARN);

        g.fill(l + 2, t, l + W - 2, t + H, base());
        g.fill(l, t + 2, l + W, t + H - 2, base());
        g.fill(l + 2, t, l + W - 2, t + 30, band());
        g.fill(l, t + 2, l + W, t + 30, band());
        RenderSystem.enableBlend();
        g.blit(MnStyle.LOGO, l + 8, t + 4, 22, 22, 0, 0, 96, 96, 96, 96);
        g.drawString(font, MnStyle.bold("MINENORTH RP"), l + 36, t + 6, 0xFFFFFFFF, false);
        g.drawString(font, font.plainSubstrByWidth(c.licenceName.toUpperCase(), W - 50), l + 36, t + 17, 0xFFDDE8FF, false);

        // photo
        int px = l + 12, py = t + 40;
        g.fill(px - 2, py - 2, px + 58, py + 58, 0xFF3A3A48);
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin(c.holder);
        if (minecraft != null && minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(c.holder);
            if (info != null) skin = info.getSkinLocation();
        }
        PlayerFaceRenderer.draw(g, skin, px, py, 56);

        int x = l + 80, y = t + 40, dark = 0xFF1A1A2A, label = 0xFF55557A;
        g.drawString(font, "1. TITULAIRE", x, y, label, false);
        g.drawString(font, MnStyle.bold(c.holderName), x, y + 10, dark, false);

        if (grouped()) {
            // tableau des catégories
            int ty = y + 26;
            g.drawString(font, "9. CATÉGORIES", x, ty, label, false);
            g.drawString(font, "VALABLE JUSQU'AU", l + W - 12 - font.width("VALABLE JUSQU'AU"), ty, label, false);
            for (int i = 0; i < c.lines.size(); i++) {
                Line ln = c.lines.get(i);
                int ry = ty + 11 + i * ROW;
                int badge = ln.valid() ? band() : 0xFFA8A8B8;
                String code = ln.code().isEmpty() ? "•" : ln.code();
                g.fill(x, ry - 1, x + 14, ry + 10, badge);
                g.drawCenteredString(font, MnStyle.bold(code), x + 7, ry + 1, 0xFFFFFFFF);
                g.drawString(font, font.plainSubstrByWidth(ln.name(), 96), x + 18, ry + 1, ln.valid() ? dark : 0xFF8A8A9A, false);
                String right = ln.valid() ? MnStyle.date(ln.expiry()) : "—";
                g.drawString(font, right, l + W - 12 - font.width(right), ry + 1, ln.valid() ? 0xFF1E7A45 : 0xFF8A8A9A, false);
            }
        } else if (!c.lines.isEmpty()) {
            Line ln = c.lines.get(0);
            g.drawString(font, "2. DÉLIVRÉ LE", x, y + 26, label, false);
            g.drawString(font, ln.issued() == 0 ? "—" : MnStyle.date(ln.issued()), x, y + 36, dark, false);
            g.drawString(font, "3. EXPIRE LE", x + 84, y + 26, label, false);
            g.drawString(font, ln.expiry() == 0 ? "—" : MnStyle.date(ln.expiry()), x + 84, y + 36, dark, false);
        }

        // statut global
        String st = c.cancelled ? "ANNULÉE" : c.anyValid() ? "VALIDE" : "INVALIDE";
        int sc = c.cancelled ? 0xFFE07A1F : c.anyValid() ? 0xFF1E9E52 : 0xFFD8263F;
        if (c.cancelled) g.drawCenteredString(font, "Carte annulée : déclarée perdue, un duplicata a été émis.", width / 2, t + H + 8, 0xFFFF9A4A);
        int sw = font.width(st) + 12;
        int sy = t + 102;
        g.fill(l + 12, sy, l + 12 + sw, sy + 14, sc);
        g.drawString(font, MnStyle.bold(st), l + 18, sy + 3, 0xFFFFFFFF, false);

        if (c.showPoints) {
            g.drawString(font, "Points : " + c.points + "/" + c.maxPoints, l + 12, t + H - 22, dark, false);
            MnStyle.points(g, l + 90, t + H - 22, c.points, Math.min(c.maxPoints, 15));
        }
        g.fill(l + 2, t + H - 6, l + W - 2, t + H - 4, band());
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
