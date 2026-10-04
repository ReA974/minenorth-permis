package com.minenorth_permis.client;

import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.MenuStatePacket.Cat;
import com.minenorth_permis.net.MenuStatePacket.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** "Mes permis & licences" : consultation en lecture seule + points de permis. */
@OnlyIn(Dist.CLIENT)
public class MyLicencesScreen extends MnScreen {
    private static final int ROWS = 8, ROW_H = 16, LIST_Y = 62;
    private int offset;

    public MyLicencesScreen(MenuStatePacket state, MnScreen parent) {
        super("Mes permis", state, parent);
    }

    @Override
    protected String heading() {
        boolean self = minecraft != null && minecraft.player != null && minecraft.player.getGameProfile().getName().equalsIgnoreCase(state.targetName);
        return self ? "MES PERMIS" : "PERMIS : " + state.targetName.toUpperCase();
    }

    /** Permis valides d'abord, dans l'ordre de la config. */
    private List<Entry> sorted() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : state.entries) if (e.valid()) out.add(e);
        for (Entry e : state.entries) if (!e.valid()) out.add(e);
        return out;
    }

    @Override
    protected void build() {}

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, state.entries.size() - ROWS);
        offset = Math.max(0, Math.min(max, offset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
        g.drawString(font, "Titulaire : ", left + 16, top + 34, MnStyle.CYAN, false);
        g.drawString(font, MnStyle.bold(state.targetName), left + 16 + font.width("Titulaire : "), top + 34, 0xFFFFFFFF, false);
        if (state.showPoints) {
            // Les points ne concernent que le permis de conduire
            String lbl = "Points " + (state.pointsLabel.isEmpty() ? "de permis" : "(" + state.pointsLabel + ")") + " : " + state.points + "/" + state.maxPoints;
            g.drawString(font, lbl, left + 16, top + 47, MnStyle.TEXT, false);
            MnStyle.points(g, left + 22 + font.width(lbl), top + 47, state.points, Math.min(state.maxPoints, 15));
        }

        List<Entry> list = sorted();
        offset = Math.max(0, Math.min(offset, Math.max(0, list.size() - ROWS)));
        g.fill(left + 14, top + LIST_Y - 1, left + W - 14, top + LIST_Y + ROWS * ROW_H + 1, MnStyle.LIST);
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= list.size()) break;
            Entry e = list.get(idx);
            int y = top + LIST_Y + i * ROW_H;
            String style = "licence";
            for (Cat c : state.cats) if (c.id().equals(e.category())) style = c.style();
            MnStyle.item(g, MnStyle.cardIcon(style), left + 18, y + 2, 0.75f);
            g.drawString(font, (e.valid() ? "✔ " : "✘ ") + font.plainSubstrByWidth(e.name(), 140), left + 34, y + 4,
                    e.valid() ? 0xFFFFFFFF : MnStyle.MUTED, false);
            String right = e.valid() ? (e.expiry() == Long.MAX_VALUE ? "Permanent" : "Expire le " + MnStyle.date(e.expiry())) : "Non obtenu";
            g.drawString(font, right, left + W - 18 - font.width(right), y + 4, e.valid() ? MnStyle.OK : 0xFF5A5A90, false);
        }
        if (list.size() > ROWS) {
            g.drawString(font, (offset + 1) + "-" + Math.min(list.size(), offset + ROWS) + " / " + list.size() + "  (molette)",
                    left + 16, top + LIST_Y + ROWS * ROW_H + 4, MnStyle.MUTED, false);
        }
    }
}
