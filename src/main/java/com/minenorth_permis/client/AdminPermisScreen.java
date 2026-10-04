package com.minenorth_permis.client;

import com.minenorth_permis.net.AdminActionPacket;
import com.minenorth_permis.net.AdminActionPacket.Op;
import com.minenorth_permis.net.AdminStatePacket;
import com.minenorth_permis.net.AdminStatePacket.Lic;
import com.minenorth_permis.net.AdminStatePacket.Player;
import com.minenorth_permis.net.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Locale;

/** Menu staff : liste des joueurs à gauche, permis / points / cartes du joueur sélectionné à droite. */
@OnlyIn(Dist.CLIENT)
public class AdminPermisScreen extends Screen {
    private static final int W = 420, H = 240;
    private static final int LEFT_W = 124, P_ROWS = 12, P_ROW = 13, P_Y = 56;
    private static final int L_ROWS = 8, L_ROW = 14, L_Y = 86;

    private AdminStatePacket state;
    private int left, top, pOffset, lOffset;
    private String search = "", days = "";
    private EditBox searchBox, daysBox;
    private String message = "";
    private boolean messageOk = true;

    public AdminPermisScreen(AdminStatePacket state) {
        super(Component.literal("Gestion des permis"));
        this.state = state;
    }

    public void update(AdminStatePacket m) {
        boolean selChanged = m.selected == null ? state.selected != null : !m.selected.equals(state.selected);
        state = m;
        if (selChanged) lOffset = 0;
        if (!m.message.isEmpty()) {
            message = m.message;
            messageOk = m.messageOk;
        }
        boolean sf = searchBox != null && searchBox.isFocused(), df = daysBox != null && daysBox.isFocused();
        rebuildWidgets();
        if (sf) setFocused(searchBox);
        else if (df && daysBox != null) setFocused(daysBox);
    }

    private void act(Op op, String licence, int value) {
        Network.CHANNEL.sendToServer(new AdminActionPacket(op, state.selected, licence, value));
    }

    private List<Player> filtered() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return state.players;
        return state.players.stream().filter(p -> p.name().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    /** -1 = durée de la config, sinon nombre de jours (0 = permanent). */
    private int daysValue() {
        try {
            String d = days.trim();
            return d.isEmpty() ? -1 : Math.max(0, Integer.parseInt(d));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new MnButton(left + W - 96, top + 10, 82, 16, Component.literal("Fermer"), MnButton.GHOST, this::onClose));

        searchBox = new EditBox(font, left + 12, top + 38, LEFT_W, 14, Component.literal("Recherche"));
        searchBox.setHint(Component.literal("Rechercher..."));
        searchBox.setValue(search);
        searchBox.setResponder(s -> {
            search = s;
            pOffset = 0;
        });
        addRenderableWidget(searchBox);

        if (state.selected == null) return;
        int rx = left + LEFT_W + 22, rw = W - LEFT_W - 34;

        // points
        if (state.showPoints) {
            int by = top + 50;
            addRenderableWidget(new MnButton(rx + rw - 86, by - 2, 24, 12, Component.literal("-1"), MnStyle.DARK,
                    () -> act(Op.POINTS_SET, "", state.points - 1)).enabled(state.points > 0));
            addRenderableWidget(new MnButton(rx + rw - 59, by - 2, 24, 12, Component.literal("+1"), MnStyle.DARK,
                    () -> act(Op.POINTS_SET, "", state.points + 1)).enabled(state.points < state.maxPoints));
            addRenderableWidget(new MnButton(rx + rw - 32, by - 2, 32, 12, Component.literal("Max"), MnStyle.CYAN,
                    () -> act(Op.POINTS_SET, "", state.maxPoints)).enabled(state.points < state.maxPoints));
        }

        // durée pour "Donner"
        daysBox = new EditBox(font, rx + 100, top + 64, 34, 12, Component.literal("Jours"));
        daysBox.setHint(Component.literal(String.valueOf(state.validityDays)));
        daysBox.setValue(days);
        daysBox.setFilter(s -> s.matches("\\d{0,4}"));
        daysBox.setResponder(s -> days = s);
        addRenderableWidget(daysBox);

        // permis du joueur
        lOffset = Math.max(0, Math.min(lOffset, state.lics.size() - L_ROWS));
        for (int i = 0; i < L_ROWS && lOffset + i < state.lics.size(); i++) {
            Lic l = state.lics.get(lOffset + i);
            int y = top + L_Y + i * L_ROW + 1;
            if (l.valid()) {
                addRenderableWidget(new MnButton(rx + rw - 86, y, 38, 12, Component.literal("Carte"), MnStyle.DARK,
                        () -> act(Op.CARD, l.id(), 0)).enabled(state.selOnline));
                addRenderableWidget(new MnButton(rx + rw - 44, y, 44, 12, Component.literal("Retirer"), 0xFFB8304F,
                        () -> act(Op.REVOKE, l.id(), 0)));
            } else {
                addRenderableWidget(new MnButton(rx + rw - 44, y, 44, 12, Component.literal("Donner"), MnStyle.CYAN,
                        () -> act(Op.GIVE, l.id(), daysValue())));
            }
        }

        int by = top + L_Y + L_ROWS * L_ROW + 6;
        addRenderableWidget(new MnButton(rx, by, 108, 16, Component.literal("Vider les délais"), MnStyle.DARK,
                () -> act(Op.CLEAR_COOLDOWNS, "", 0)).enabled(state.cooldowns > 0));
        addRenderableWidget(new MnButton(rx + 114, by, rw - 114, 16, Component.literal("Arrêter l'épreuve"), MnStyle.PINK,
                () -> act(Op.STOP_TEST, "", 0)).enabled(state.selInTest));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        List<Player> list = filtered();
        for (int i = 0; i < P_ROWS; i++) {
            int idx = pOffset + i;
            int y = top + P_Y + i * P_ROW;
            if (idx < list.size() && mx >= left + 12 && mx < left + 12 + LEFT_W && my >= y && my < y + P_ROW) {
                message = "";
                Network.CHANNEL.sendToServer(new AdminActionPacket(Op.SELECT, list.get(idx).id(), "", 0));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int d = -(int) Math.signum(delta);
        if (mx < left + 12 + LEFT_W) {
            pOffset = Math.max(0, Math.min(Math.max(0, filtered().size() - P_ROWS), pOffset + d));
        } else {
            lOffset = Math.max(0, Math.min(Math.max(0, state.lics.size() - L_ROWS), lOffset + d));
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MnStyle.panel(g, left, top, W, H, "GESTION DES PERMIS");

        // ---- joueurs
        List<Player> list = filtered();
        pOffset = Math.max(0, Math.min(pOffset, Math.max(0, list.size() - P_ROWS)));
        int lx = left + 12;
        g.fill(lx, top + P_Y - 1, lx + LEFT_W, top + P_Y + P_ROWS * P_ROW + 1, MnStyle.LIST);
        for (int i = 0; i < P_ROWS; i++) {
            int idx = pOffset + i;
            if (idx >= list.size()) break;
            Player p = list.get(idx);
            int y = top + P_Y + i * P_ROW;
            boolean sel = p.id().equals(state.selected);
            if (sel) g.fill(lx, y, lx + LEFT_W, y + P_ROW, MnStyle.CYAN);
            else if (mx >= lx && mx < lx + LEFT_W && my >= y && my < y + P_ROW) g.fill(lx, y, lx + LEFT_W, y + P_ROW, MnStyle.HOVER);
            g.fill(lx + 3, y + 5, lx + 6, y + 8, p.online() ? MnStyle.OK : 0xFF5A5A90);
            g.drawString(font, font.plainSubstrByWidth(p.name(), LEFT_W - 30), lx + 10, y + 3, 0xFFFFFFFF, false);
            String n = String.valueOf(p.valid());
            g.drawString(font, n, lx + LEFT_W - 4 - font.width(n), y + 3, sel ? 0xFFFFFFFF : MnStyle.MUTED, false);
        }
        if (list.isEmpty()) g.drawString(font, "Aucun joueur.", lx + 4, top + P_Y + 3, MnStyle.MUTED, false);
        g.drawString(font, list.size() + " joueur(s)", lx, top + P_Y + P_ROWS * P_ROW + 4, MnStyle.MUTED, false);

        // ---- détail
        int rx = left + LEFT_W + 22, rw = W - LEFT_W - 34;
        g.fill(rx - 8, top + 34, rx - 7, top + H - 16, 0xFF2A2468);
        if (state.selected == null) {
            g.drawString(font, "Sélectionne un joueur à gauche.", rx, top + 40, MnStyle.TEXT, false);
        } else {
            g.drawString(font, MnStyle.bold(font.plainSubstrByWidth(state.selName, 150)), rx, top + 37, 0xFFFFFFFF, false);
            String st = state.selInTest ? "en épreuve" : state.selOnline ? "en ligne" : "hors ligne";
            int sc = state.selInTest ? MnStyle.PINK : state.selOnline ? MnStyle.OK : MnStyle.MUTED;
            g.drawString(font, st, rx + rw - font.width(st), top + 37, sc, false);

            if (state.showPoints) {
                int pc = state.points == 0 ? MnStyle.ALERT : state.points <= 3 ? MnStyle.ALERT : state.points <= 6 ? MnStyle.WARN : MnStyle.OK;
                g.drawString(font, "Points permis : ", rx, top + 50, MnStyle.TEXT, false);
                g.drawString(font, MnStyle.bold(state.points + "/" + state.maxPoints), rx + font.width("Points permis : "), top + 50, pc, false);
            }
            g.drawString(font, "Durée pour Donner :", rx, top + 66, MnStyle.CYAN, false);
            g.drawString(font, "j  (vide=" + state.validityDays + ", 0=perm.)", rx + 138, top + 66, MnStyle.MUTED, false);

            g.fill(rx - 2, top + L_Y - 1, rx + rw + 2, top + L_Y + L_ROWS * L_ROW + 1, MnStyle.LIST);
            for (int i = 0; i < L_ROWS && lOffset + i < state.lics.size(); i++) {
                Lic l = state.lics.get(lOffset + i);
                int y = top + L_Y + i * L_ROW;
                g.fill(rx - 2, y, rx, y + L_ROW, l.valid() ? MnStyle.OK : 0xFF3A3470);
                String nm = (l.code().isEmpty() ? "" : l.code() + " · ") + l.name();
                g.drawString(font, font.plainSubstrByWidth(nm, 106), rx + 3, y + 3, l.valid() ? 0xFFFFFFFF : MnStyle.MUTED, false);
                String right = l.valid() ? (l.expiry() == Long.MAX_VALUE ? "permanent" : "→ " + MnStyle.date(l.expiry())) : "—";
                g.drawString(font, right, rx + rw - 92 - font.width(right), y + 3, l.valid() ? MnStyle.OK : 0xFF5A5A90, false);
            }
            if (state.lics.size() > L_ROWS) {
                String pg = (lOffset + 1) + "-" + Math.min(state.lics.size(), lOffset + L_ROWS) + "/" + state.lics.size();
                g.drawString(font, pg, rx + rw - font.width(pg), top + 76, MnStyle.MUTED, false);
            }
            if (state.cooldowns > 0) g.drawString(font, state.cooldowns + " délai(s) après échec", rx, top + 76, MnStyle.WARN, false);
        }
        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, W - 24), left + 12, top + H - 12, messageOk ? MnStyle.OK : MnStyle.WARN, false);
        }
        super.render(g, mx, my, pt);
    }
}
