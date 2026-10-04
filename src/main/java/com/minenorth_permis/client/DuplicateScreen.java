package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuActionPacket;
import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.MenuStatePacket.Dup;
import com.minenorth_permis.net.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Papiers perdus : refaire une carte (duplicata payant), l'ancienne carte est annulée. */
@OnlyIn(Dist.CLIENT)
public class DuplicateScreen extends MnScreen {
    private static final int ROWS = 4, ROW_H = 24, LIST_Y = 58;
    private String selected;
    private int offset;

    public DuplicateScreen(MenuStatePacket state, MnScreen parent) {
        super("Duplicata", state, parent);
    }

    @Override
    protected String heading() {
        return "PAPIERS PERDUS";
    }

    private Dup sel() {
        for (Dup d : state.dups) if (d.key().equals(selected)) return d;
        return null;
    }

    private void send(MenuActionPacket.Action a) {
        message = "";
        Network.CHANNEL.sendToServer(new MenuActionPacket(a, selected));
    }

    @Override
    protected void build() {
        offset = Math.max(0, Math.min(offset, state.dups.size() - ROWS));
        for (int i = 0; i < ROWS && offset + i < state.dups.size(); i++) {
            Dup d = state.dups.get(offset + i);
            int y = top + LIST_Y + i * ROW_H;
            addRenderableWidget(new MnButton(left + 16, y, W - 32, ROW_H - 2, Component.literal(d.name()), MnStyle.DARK, () -> {
                selected = d.key();
                rebuildWidgets();
            }) {
                @Override
                public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    boolean on = d.key().equals(selected);
                    g.fill(getX(), getY(), getX() + width, getY() + height, on ? MnStyle.HOVER : isHoveredOrFocused() ? 0xFF221A66 : MnStyle.LIST);
                    if (on) g.fill(getX(), getY(), getX() + 3, getY() + height, MnStyle.CYAN);
                    MnStyle.item(g, MnStyle.cardIcon(d.style()), getX() + 8, getY() + 3, 1f);
                    g.drawString(font, MnStyle.bold(d.name()), getX() + 30, getY() + 3, 0xFFFFFFFF, false);
                    g.drawString(font, d.onHand() ? "Déjà sur toi" : "Carte introuvable : à refaire", getX() + 30, getY() + 13,
                            d.onHand() ? MnStyle.MUTED : MnStyle.WARN, false);
                }
            });
        }
        Dup d = sel();
        boolean can = d != null && !d.onHand();
        long price = state.dupPrice;
        if (price <= 0) {
            addRenderableWidget(new MnButton(left + 16, top + 168, 268, 20, Component.literal("Refaire la carte"), MnStyle.CYAN,
                    () -> send(MenuActionPacket.Action.DUP_CASH)).enabled(can));
            return;
        }
        addRenderableWidget(new MnButton(left + 16, top + 168, 130, 20, Component.literal("Payer en espèces"), MnStyle.CYAN,
                () -> send(MenuActionPacket.Action.DUP_CASH)).enabled(can && state.cashAllowed && state.cash >= price));
        addRenderableWidget(new MnButton(left + 154, top + 168, 130, 20, Component.literal("Payer par carte"), MnStyle.PINK,
                () -> send(MenuActionPacket.Action.DUP_CARD)).enabled(can && state.cardAllowed && state.balance >= price));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        offset -= (int) Math.signum(delta);
        rebuildWidgets();
        return true;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        g.drawString(font, "Choisis la carte à refaire. Frais : " + (state.dupPrice <= 0 ? "gratuit" : MnStyle.euros(state.dupPrice)),
                left + 16, top + 34, MnStyle.TEXT, false);
        g.drawString(font, "L'ancienne carte sera annulée (perte ou vol).", left + 16, top + 45, MnStyle.MUTED, false);
        if (state.dups.isEmpty()) g.drawString(font, "Tu n'as aucun permis ni licence valide.", left + 16, top + LIST_Y + 6, MnStyle.WARN, false);
        if (state.dups.size() > ROWS) g.drawString(font, "(molette : " + state.dups.size() + " cartes)", left + W - 16 - font.width("(molette : " + state.dups.size() + " cartes)"), top + 45, MnStyle.MUTED, false);
        wallet(g, top + 156);
    }
}
