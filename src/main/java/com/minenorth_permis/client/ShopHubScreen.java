package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.MenuStatePacket.Cat;
import com.minenorth_permis.net.MenuStatePacket.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Accueil de la boutique : une tuile par catégorie (Permis, Licences, Permis Arme, Licence Pêche). */
@OnlyIn(Dist.CLIENT)
public class ShopHubScreen extends MnScreen {
    private static final int TW = 130, TH = 58;

    public ShopHubScreen(MenuStatePacket state) {
        super("Permis & Licences", state, null);
    }

    @Override
    protected String heading() {
        return "PERMIS & LICENCES";
    }

    @Override
    protected void build() {
        int i = 0;
        for (Cat c : state.cats) {
            if (i >= 4) break;
            // catégorie vide une fois le permis d'armes retiré (il se passe au stand de tir) : pas de tuile
            if (state.entries.stream().noneMatch(e -> e.category().equals(c.id()) && !e.atRange())) continue;
            int x = left + 16 + (i % 2) * (TW + 8);
            int y = top + 50 + (i / 2) * (TH + 8);
            addRenderableWidget(new Tile(x, y, c));
            i++;
        }
        addRenderableWidget(new MnButton(left + 16, top + H - 44, 100, 18, Component.literal("Mes permis"), MnStyle.DARK,
                () -> minecraft.setScreen(new MyLicencesScreen(state, this))));
        addRenderableWidget(new MnButton(left + 122, top + H - 44, 100, 18, Component.literal("Papiers perdus ?"), MnStyle.DARK,
                () -> minecraft.setScreen(new DuplicateScreen(state, this))));
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        g.drawString(font, "Bienvenue " + state.targetName + ", choisis une catégorie.", left + 16, top + 36, MnStyle.TEXT, false);
        wallet(g, top + H - 38);
    }

    private class Tile extends MnButton {
        private final Cat cat;

        Tile(int x, int y, Cat cat) {
            super(x, y, TW, TH, Component.literal(cat.name()), cat.color(),
                    () -> minecraft.setScreen(new CategoryScreen(state, ShopHubScreen.this, cat.id())));
            this.cat = cat;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY();
            boolean hov = isHoveredOrFocused();
            g.fill(x, y, x + width, y + height, hov ? MnStyle.HOVER : MnStyle.LIST);
            g.fill(x, y, x + 4, y + height, cat.color());
            if (hov) g.fill(x, y + height - 2, x + width, y + height, cat.color());
            MnStyle.item(g, MnStyle.cardIcon(cat.style()), x + 10, y + 13, 2f);
            g.drawString(font, MnStyle.bold(font.plainSubstrByWidth(cat.name(), 82)), x + 46, y + 14, 0xFFFFFFFF, false);
            int total = 0, owned = 0;
            for (Entry e : state.entries) {
                if (!e.category().equals(cat.id()) || e.atRange()) continue;
                total++;
                if (e.valid()) owned++;
            }
            g.drawString(font, total + " disponible" + (total > 1 ? "s" : ""), x + 46, y + 28, MnStyle.TEXT, false);
            g.drawString(font, owned + " possédé" + (owned > 1 ? "s" : ""), x + 46, y + 39, owned > 0 ? MnStyle.OK : MnStyle.MUTED, false);
        }
    }
}
