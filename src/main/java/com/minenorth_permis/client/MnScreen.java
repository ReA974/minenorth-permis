package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuActionPacket;
import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/** Base des écrans permis : panneau MineNorth, état serveur partagé, navigation parent/enfant. */
@OnlyIn(Dist.CLIENT)
public abstract class MnScreen extends Screen {
    protected static final int W = 300, H = 216;
    protected MenuStatePacket state;
    @Nullable
    protected final MnScreen parent;
    protected int left, top;
    protected String message = "";
    protected boolean messageOk = true;

    protected MnScreen(String title, MenuStatePacket state, @Nullable MnScreen parent) {
        super(Component.literal(title));
        this.state = state;
        this.parent = parent;
    }

    /** Nouvel état serveur : propage aux parents puis reconstruit l'écran. */
    public void update(MenuStatePacket m) {
        for (MnScreen s = this; s != null; s = s.parent) s.state = m;
        if (!m.message.isEmpty()) {
            message = m.message;
            messageOk = m.messageOk;
        }
        onUpdate();
        rebuildWidgets();
    }

    protected void onUpdate() {}

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new MnButton(left + W - 100, top + 10, 86, 16,
                Component.literal(parent != null ? "Retour" : "Fermer"), MnButton.GHOST, this::onClose));
        build();
    }

    protected abstract void build();

    protected abstract String heading();

    @Override
    public void onClose() {
        if (parent != null) {
            parent.message = message;
            parent.messageOk = messageOk;
            minecraft.setScreen(parent);
            return;
        }
        if (state.screen != MenuStatePacket.MY) Network.CHANNEL.sendToServer(new MenuActionPacket(MenuActionPacket.Action.CLOSE, ""));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MnStyle.panel(g, left, top, W, H, heading());
        renderContent(g, mx, my, pt);
        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, W - 32), left + 16, top + H - 14, messageOk ? MnStyle.OK : MnStyle.WARN, false);
        }
        super.render(g, mx, my, pt);
    }

    protected abstract void renderContent(GuiGraphics g, int mx, int my, float pt);

    protected void wallet(GuiGraphics g, int y) {
        String cash = state.cash < 0 ? "—" : MnStyle.euros(state.cash);
        String card = state.balance < 0 ? "pas de compte" : MnStyle.euros(state.balance);
        g.drawString(font, "Espèces : ", left + 16, y, MnStyle.CYAN, false);
        g.drawString(font, cash, left + 16 + font.width("Espèces : "), y, 0xFFFFFFFF, false);
        int x2 = left + 150;
        g.drawString(font, "Compte : ", x2, y, MnStyle.CYAN, false);
        g.drawString(font, card, x2 + font.width("Compte : "), y, 0xFFFFFFFF, false);
    }
}
