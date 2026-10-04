package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.MenuStatePacket.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Menu du PNJ moniteur : un bouton par test de conduite. */
@OnlyIn(Dist.CLIENT)
public class TestMenuScreen extends MnScreen {
    public TestMenuScreen(MenuStatePacket state) {
        super("Tests de permis", state, null);
    }

    private List<Entry> tests() {
        return state.entries.stream().filter(e -> e.isTest() && !e.test().equalsIgnoreCase("tir")).toList();
    }

    @Override
    protected String heading() {
        return "TESTS DE PERMIS";
    }

    @Override
    protected void build() {
        List<Entry> list = tests();
        for (int i = 0; i < list.size() && i < 5; i++) {
            Entry e = list.get(i);
            int y = top + 50 + i * 30;
            addRenderableWidget(new Row(left + 16, y, e));
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        g.drawString(font, "Choisis le test à passer, " + state.targetName + ".", left + 16, top + 36, MnStyle.TEXT, false);
        if (tests().isEmpty()) g.drawString(font, "Aucun test configuré.", left + 16, top + 56, MnStyle.WARN, false);
    }

    private class Row extends MnButton {
        private final Entry e;

        Row(int x, int y, Entry e) {
            super(x, y, W - 32, 26, Component.literal(e.name()), MnStyle.PINK,
                    () -> minecraft.setScreen(new TestScreen(state, TestMenuScreen.this, e.id())));
            this.e = e;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY();
            boolean hov = isHoveredOrFocused();
            g.fill(x, y, x + width, y + height, hov ? MnStyle.HOVER : MnStyle.LIST);
            g.fill(x, y, x + 3, y + height, e.valid() ? MnStyle.OK : MnStyle.PINK);
            MnStyle.item(g, MnStyle.cardIcon("permis"), x + 8, y + 5, 1f);
            g.drawString(font, MnStyle.bold(e.name()), x + 30, y + 4, 0xFFFFFFFF, false);
            String sub = e.valid() ? "✔ Déjà obtenu" : e.cooldown() > 0 ? "Échec récent : " + MnStyle.duration(e.cooldown())
                    : !e.ready() ? "Parcours non configuré" : e.busy() ? "Occupé" : e.checkpoints() + " points · " + e.timeLimit() + " s";
            int col = e.valid() ? MnStyle.OK : e.cooldown() > 0 ? MnStyle.ALERT : !e.ready() || e.busy() ? MnStyle.WARN : MnStyle.TEXT;
            g.drawString(font, sub, x + 30, y + 15, col, false);
            g.drawString(font, "›", x + width - 12, y + 9, hov ? 0xFFFFFFFF : MnStyle.MUTED, false);
        }
    }
}
