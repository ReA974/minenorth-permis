package com.minenorth_permis.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Bouton plat MineNorth (même rendu que les boutons de l'ATM EuroBank). */
@OnlyIn(Dist.CLIENT)
public class MnButton extends AbstractButton {
    public static final int GHOST = 0;

    private final Runnable action;
    private final int color;

    public MnButton(int x, int y, int w, int h, Component label, int color, Runnable action) {
        super(x, y, w, h, label);
        this.color = color;
        this.action = action;
    }

    public MnButton enabled(boolean on) {
        this.active = on;
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        boolean hov = isHoveredOrFocused();
        int ty = y + (height - 8) / 2;
        if (color == GHOST) {
            int tc = !active ? 0xFF8FA8E0 : hov ? 0xFFFFFFFF : MnStyle.TEXT;
            int tx = x + width - font.width(getMessage());
            g.drawString(font, getMessage(), tx, ty, tc, false);
            if (hov && active) g.fill(tx, ty + 9, x + width, ty + 10, tc);
            return;
        }
        int bg = !active ? 0xFF2A2468 : hov ? MnStyle.lighten(color) : color;
        g.fill(x, y, x + width, y + height, bg);
        g.drawCenteredString(font, getMessage(), x + width / 2, ty, active ? 0xFFFFFFFF : 0xFF8FA8E0);
    }
}
