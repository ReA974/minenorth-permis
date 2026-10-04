package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.MenuStatePacket.Cat;
import com.minenorth_permis.net.MenuStatePacket.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Liste des permis/licences d'une catégorie : possédé (vert), à acheter (prix) ou à passer en test. */
@OnlyIn(Dist.CLIENT)
public class CategoryScreen extends MnScreen {
    private static final int ROWS = 5, ROW_H = 24, LIST_Y = 48;
    private final String category;
    private int offset;

    public CategoryScreen(MenuStatePacket state, MnScreen parent, String category) {
        super("Catégorie", state, parent);
        this.category = category;
    }

    private Cat cat() {
        for (Cat c : state.cats) if (c.id().equals(category)) return c;
        return new Cat(category, category, MnStyle.CYAN, "licence");
    }

    private List<Entry> list() {
        return state.entries.stream().filter(e -> e.category().equals(category) && !e.atRange()).toList();
    }

    @Override
    protected String heading() {
        return cat().name().toUpperCase();
    }

    @Override
    protected void build() {
        List<Entry> list = list();
        offset = Math.max(0, Math.min(offset, Math.max(0, list.size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= list.size()) break;
            Entry e = list.get(idx);
            int y = top + LIST_Y + i * ROW_H + 3;
            MnButton b;
            if (e.valid()) {
                b = new MnButton(left + W - 96, y, 70, 17, Component.literal("✔ Possédé"), MnStyle.DARK, () -> {}).enabled(false);
            } else if (e.isTest()) {
                b = new MnButton(left + W - 96, y, 70, 17, Component.literal("Passer le test"), MnStyle.PINK,
                        () -> minecraft.setScreen(new TestScreen(state, this, e.id())));
            } else {
                b = new MnButton(left + W - 96, y, 70, 17, Component.literal("Acheter"), MnStyle.CYAN,
                        () -> minecraft.setScreen(new BuyScreen(state, this, e.id())));
            }
            addRenderableWidget(b);
        }
        if (list.size() > ROWS) {
            addRenderableWidget(new MnButton(left + W - 22, top + LIST_Y, 10, ROWS * ROW_H / 2 - 1, Component.literal("^"), MnStyle.DARK, () -> scroll(-1)));
            addRenderableWidget(new MnButton(left + W - 22, top + LIST_Y + ROWS * ROW_H / 2 + 1, 10, ROWS * ROW_H / 2 - 1, Component.literal("v"), MnStyle.DARK, () -> scroll(1)));
        }
    }

    private void scroll(int d) {
        offset += d;
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll(-(int) Math.signum(delta));
        return true;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        Cat c = cat();
        g.drawString(font, "Choisis un permis ou une licence.", left + 16, top + 34, MnStyle.TEXT, false);
        List<Entry> list = list();
        g.fill(left + 14, top + LIST_Y - 1, left + W - 26, top + LIST_Y + ROWS * ROW_H + 1, MnStyle.LIST);
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= list.size()) break;
            Entry e = list.get(idx);
            int y = top + LIST_Y + i * ROW_H;
            if (mx >= left + 14 && mx < left + W - 26 && my >= y && my < y + ROW_H) g.fill(left + 14, y, left + W - 26, y + ROW_H, MnStyle.HOVER);
            g.fill(left + 14, y, left + 16, y + ROW_H, e.valid() ? MnStyle.OK : c.color());
            MnStyle.item(g, MnStyle.cardIcon(c.style()), left + 20, y + 4, 1f);
            g.drawString(font, font.plainSubstrByWidth(e.name(), 150), left + 40, y + 4, 0xFFFFFFFF, false);
            String sub;
            int col;
            if (e.valid()) {
                sub = "Valide jusqu'au " + MnStyle.date(e.expiry());
                col = MnStyle.OK;
            } else if (e.isTest()) {
                sub = e.cooldown() > 0 ? "Échec récent : attends " + MnStyle.duration(e.cooldown()) : "Obtenu en réussissant le test";
                col = e.cooldown() > 0 ? MnStyle.ALERT : MnStyle.PINK;
            } else {
                sub = "Prix : " + (e.price() == 0 ? "gratuit" : MnStyle.euros(e.price()));
                col = MnStyle.CYAN;
            }
            g.drawString(font, font.plainSubstrByWidth(sub, 160), left + 40, y + 14, col, false);
        }
        if (list.isEmpty()) g.drawString(font, "Rien dans cette catégorie.", left + 20, top + LIST_Y + 6, MnStyle.TEXT, false);
        wallet(g, top + H - 32);
    }
}
