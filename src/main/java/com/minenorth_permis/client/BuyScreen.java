package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuActionPacket;
import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.MenuStatePacket.Cat;
import com.minenorth_permis.net.MenuStatePacket.Entry;
import com.minenorth_permis.net.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Confirmation d'achat : espèces ou carte bancaire EuroBank. */
@OnlyIn(Dist.CLIENT)
public class BuyScreen extends MnScreen {
    private final String id;

    public BuyScreen(MenuStatePacket state, MnScreen parent, String id) {
        super("Achat", state, parent);
        this.id = id;
    }

    private Entry entry() {
        return state.entry(id);
    }

    @Override
    protected String heading() {
        return "ACHAT";
    }

    @Override
    protected void onUpdate() {
        Entry e = entry();
        if (e != null && e.valid() && parent != null) {
            // Acheté : retour à la liste avec le message de confirmation
            parent.message = message;
            parent.messageOk = messageOk;
            minecraft.setScreen(parent);
        }
    }

    private void send(MenuActionPacket.Action a) {
        message = "";
        Network.CHANNEL.sendToServer(new MenuActionPacket(a, id));
    }

    @Override
    protected void build() {
        Entry e = entry();
        if (e == null) return;
        if (e.price() == 0) {
            addRenderableWidget(new MnButton(left + 16, top + 150, 268, 22, Component.literal("Obtenir gratuitement"), MnStyle.CYAN,
                    () -> send(MenuActionPacket.Action.BUY_CASH)));
            return;
        }
        boolean cashOk = state.cashAllowed && state.cash >= e.price();
        boolean cardOk = state.cardAllowed && state.balance >= e.price();
        addRenderableWidget(new MnButton(left + 16, top + 150, 130, 22, Component.literal("Payer en espèces"), MnStyle.CYAN,
                () -> send(MenuActionPacket.Action.BUY_CASH)).enabled(cashOk));
        addRenderableWidget(new MnButton(left + 154, top + 150, 130, 22, Component.literal("Payer par carte"), MnStyle.PINK,
                () -> send(MenuActionPacket.Action.BUY_CARD)).enabled(cardOk));
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        Entry e = entry();
        if (e == null) return;
        String style = "licence";
        for (Cat c : state.cats) if (c.id().equals(e.category())) style = c.style();
        g.fill(left + 16, top + 40, left + W - 16, top + 100, MnStyle.LIST);
        MnStyle.item(g, MnStyle.cardIcon(style), left + 26, top + 50, 2.5f);
        MnStyle.scaled(g, MnStyle.bold(font.plainSubstrByWidth(e.name(), 120)), left + 76, top + 52, 1.3f, 0xFFFFFFFF);
        g.drawString(font, "Prix : ", left + 76, top + 72, MnStyle.CYAN, false);
        g.drawString(font, MnStyle.bold(e.price() == 0 ? "gratuit" : MnStyle.euros(e.price())), left + 76 + font.width("Prix : "), top + 72, 0xFFFFFFFF, false);
        g.drawString(font, state.validityDays > 0 ? "Validité : " + state.validityDays + " jours après achat" : "Validité : permanente", left + 76, top + 84, MnStyle.MUTED, false);

        wallet(g, top + 112);
        String cardLine;
        int col;
        if (!state.cardAllowed) {
            cardLine = "Paiement par carte désactivé.";
            col = MnStyle.MUTED;
        } else if (state.balance < 0) {
            cardLine = "Carte : pas de compte bancaire (ATM).";
            col = MnStyle.WARN;
        } else if (state.balance < e.price()) {
            cardLine = "Carte : solde insuffisant.";
            col = MnStyle.WARN;
        } else {
            cardLine = "Carte : ta carte bancaire doit être sur toi.";
            col = MnStyle.TEXT;
        }
        g.drawString(font, cardLine, left + 16, top + 128, col, false);
        if (state.cash >= 0 && state.cash < e.price()) {
            g.drawString(font, "Il te manque " + MnStyle.euros(e.price() - state.cash) + " en espèces.", left + 16, top + 138, MnStyle.WARN, false);
        }
    }
}
