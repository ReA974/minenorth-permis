package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuActionPacket;
import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.MenuStatePacket.Entry;
import com.minenorth_permis.net.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Fiche d'un test de permis avec le bouton "Passer le test". */
@OnlyIn(Dist.CLIENT)
public class TestScreen extends MnScreen {
    private final String id;

    public TestScreen(MenuStatePacket state, MnScreen parent, String id) {
        super("Test", state, parent);
        this.id = id;
    }

    private boolean isTir(Entry e) {
        return e.test().equalsIgnoreCase("tir");
    }

    @Override
    protected String heading() {
        return "EXAMEN";
    }

    @Override
    protected void build() {
        Entry e = state.entry(id);
        if (e == null || isTir(e)) return;
        boolean can = e.ready() && !e.busy() && e.cooldown() <= 0 && !e.valid();
        addRenderableWidget(new MnButton(left + 16, top + 158, 268, 24, MnStyle.bold("Passer le test"), MnStyle.PINK, () -> {
            Network.CHANNEL.sendToServer(new MenuActionPacket(MenuActionPacket.Action.START_TEST, id));
            minecraft.setScreen(null);
        }).enabled(can));
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        Entry e = state.entry(id);
        if (e == null) return;
        g.fill(left + 16, top + 40, left + W - 16, top + 96, MnStyle.LIST);
        g.fill(left + 16, top + 40, left + 19, top + 96, MnStyle.PINK);
        MnStyle.item(g, MnStyle.cardIcon(isTir(e) ? "arme" : "permis"), left + 28, top + 50, 2.5f);
        MnStyle.scaled(g, MnStyle.bold(font.plainSubstrByWidth(e.name(), 130)), left + 76, top + 50, 1.3f, 0xFFFFFFFF);
        g.drawString(font, e.testName(), left + 76, top + 66, MnStyle.PINK, false);

        int y = top + 104;
        if (isTir(e)) {
            line(g, y, "Épreuve", "toucher " + state.tirHits + " fois la cible");
            line(g, y + 12, "Frais d'examen", state.examPrice <= 0 ? "gratuit" : MnStyle.euros(state.examPrice));
            line(g, y + 24, "Où", "au stand de tir, auprès du moniteur");
            return;
        }
        line(g, y, "Parcours", e.checkpoints() + " point" + (e.checkpoints() > 1 ? "s" : "") + " de contrôle (anneaux verts)");
        line(g, y + 12, "Temps limite", e.timeLimit() + " s après avoir posé le véhicule");
        line(g, y + 24, "Abandon", "maintenir sneak 3 secondes");
        String status;
        int col = MnStyle.WARN;
        if (e.valid()) {
            status = "Tu as déjà ce permis.";
            col = MnStyle.OK;
        } else if (!e.ready()) status = "Parcours pas encore configuré par le staff.";
        else if (e.cooldown() > 0) {
            status = "Échec récent : réessaie dans " + MnStyle.duration(e.cooldown()) + ".";
            col = MnStyle.ALERT;
        } else if (e.busy()) status = "Un autre candidat passe ce test, patiente un instant.";
        else {
            status = "Prêt ! Tu seras téléporté au départ.";
            col = MnStyle.OK;
        }
        g.drawString(font, status, left + 16, top + 144, col, false);
    }

    private void line(GuiGraphics g, int y, String k, String v) {
        g.drawString(font, k + " : ", left + 16, y, MnStyle.CYAN, false);
        g.drawString(font, font.plainSubstrByWidth(v, 200), left + 16 + font.width(k + " : "), y, 0xFFFFFFFF, false);
    }
}
