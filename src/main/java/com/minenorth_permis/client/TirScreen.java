package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuActionPacket;
import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Moniteur du stand de tir : règles de l'épreuve + paiement des frais d'examen (espèces ou carte). */
@OnlyIn(Dist.CLIENT)
public class TirScreen extends MnScreen {
    public TirScreen(MenuStatePacket state) {
        super("Épreuve de tir", state, null);
        if (!state.message.isEmpty()) {
            message = state.message;
            messageOk = state.messageOk;
        }
    }

    @Override
    protected String heading() {
        return "ÉPREUVE DE TIR";
    }

    private void pay(MenuActionPacket.Action a) {
        message = "";
        Network.CHANNEL.sendToServer(new MenuActionPacket(a, ""));
    }

    @Override
    protected void build() {
        boolean ok = state.blocker.isEmpty();
        long price = state.examPrice;
        if (price <= 0) {
            addRenderableWidget(new MnButton(left + 16, top + 168, 268, 22, MnStyle.bold("Commencer l'épreuve"), MnStyle.PINK,
                    () -> pay(MenuActionPacket.Action.PAY_TIR_CASH)).enabled(ok));
            return;
        }
        boolean cashOk = ok && state.cashAllowed && state.cash >= price;
        boolean cardOk = ok && state.cardAllowed && state.balance >= price;
        addRenderableWidget(new MnButton(left + 16, top + 168, 130, 22, Component.literal("Payer en espèces"), MnStyle.CYAN,
                () -> pay(MenuActionPacket.Action.PAY_TIR_CASH)).enabled(cashOk));
        addRenderableWidget(new MnButton(left + 154, top + 168, 130, 22, Component.literal("Payer par carte"), MnStyle.PINK,
                () -> pay(MenuActionPacket.Action.PAY_TIR_CARD)).enabled(cardOk));
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        g.fill(left + 16, top + 40, left + W - 16, top + 92, MnStyle.LIST);
        g.fill(left + 16, top + 40, left + 19, top + 92, 0xFFE04E6A);
        MnStyle.item(g, MnStyle.cardIcon("arme"), left + 28, top + 48, 2.5f);
        MnStyle.scaled(g, MnStyle.bold("Permis Arme"), left + 76, top + 48, 1.3f, 0xFFFFFFFF);
        g.drawString(font, "Frais d'examen : ", left + 76, top + 66, MnStyle.CYAN, false);
        g.drawString(font, MnStyle.bold(state.examPrice <= 0 ? "gratuit" : MnStyle.euros(state.examPrice)),
                left + 76 + font.width("Frais d'examen : "), top + 66, 0xFFFFFFFF, false);
        if (!state.tirZone.isEmpty()) g.drawString(font, "Stand : " + state.tirZone, left + 76, top + 78, MnStyle.MUTED, false);

        int y = top + 100;
        line(g, y, "Objectif", "toucher " + state.tirHits + " fois la cible");
        line(g, y + 11, "Distance", "rester à moins de " + state.tirDist + " blocs");
        line(g, y + 22, "Temps", state.tirTime > 0 ? state.tirTime + " secondes" : "illimité");
        if (state.tirAmmoFail) line(g, y + 33, "Munitions", "chargeur vide = épreuve ratée");
        wallet(g, top + 146);
        if (!state.blocker.isEmpty()) g.drawString(font, font.plainSubstrByWidth(state.blocker, W - 32), left + 16, top + 157, MnStyle.ALERT, false);
        else if (state.examPrice > 0 && state.cash < state.examPrice && state.balance < state.examPrice)
            g.drawString(font, "Fonds insuffisants (espèces et compte).", left + 16, top + 157, MnStyle.WARN, false);
    }

    private void line(GuiGraphics g, int y, String k, String v) {
        g.drawString(font, k + " : ", left + 16, y, MnStyle.CYAN, false);
        g.drawString(font, v, left + 16 + font.width(k + " : "), y, 0xFFFFFFFF, false);
    }
}
