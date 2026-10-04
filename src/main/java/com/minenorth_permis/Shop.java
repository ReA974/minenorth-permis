package com.minenorth_permis;

import com.minenorth_permis.items.PermisCardItem;
import com.minenorth_permis.net.MenuActionPacket.Action;
import com.minenorth_permis.net.MenuStatePacket;
import com.minenorth_permis.net.Network;
import com.minenorth_permis.tests.DrivingTests;
import com.minenorth_permis.tests.ShootingTests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Achats de permis/licences et lancement des tests depuis les écrans. */
public final class Shop {
    private Shop() {}

    public static void handle(ServerPlayer p, Action action, String id) {
        if (action == Action.CLOSE) {
            Network.MENU_SESSIONS.remove(p.getUUID());
            Network.TIR_PENDING.remove(p.getUUID());
            return;
        }
        if (!Network.hasSession(p)) {
            Msg.err(p, "Passe par le PNJ pour accéder à ce menu.");
            return;
        }
        if (action == Action.DUP_CASH || action == Action.DUP_CARD) {
            duplicate(p, id, action == Action.DUP_CARD);
            return;
        }
        if (action == Action.PAY_TIR_CASH || action == Action.PAY_TIR_CARD) {
            String zone = Network.TIR_PENDING.get(p.getUUID());
            ShootingTests.payAndStart(p, zone, action == Action.PAY_TIR_CARD);
            return;
        }
        PermisConfig.Root cfg = PermisConfig.get();
        PermisConfig.Licence l = cfg.licence(id);
        if (l == null) return;

        if (action == Action.START_TEST) {
            if ("tir".equalsIgnoreCase(l.test)) {
                Msg.info(p, "L'épreuve de tir se passe au stand de tir : rends-toi sur place.");
                return;
            }
            PermisConfig.DrivingTest t = cfg.test(l.test);
            if (t == null) return;
            Network.MENU_SESSIONS.remove(p.getUUID());
            DrivingTests.start(p, t.id);
            return;
        }

        // ---------------- achat direct
        if (!l.test.isEmpty()) {
            reply(p, "Ce permis s'obtient en passant le test.", false);
            return;
        }
        if (Licences.isValid(p.server, p.getUUID(), l.id)) {
            reply(p, "Tu as déjà : " + l.name + ".", false);
            return;
        }
        long price = l.priceCents();
        String err = pay(p, price, action == Action.BUY_CARD);
        if (err != null) {
            reply(p, err, false);
            return;
        }
        obtain(p, l.id);
        String exp = cfg.validityDays > 0 ? " Valide " + cfg.validityDays + " jours." : "";
        Msg.ok(p, l.name + " acheté pour " + EuroBankCompat.format(price) + " !" + exp);
        reply(p, l.name + " obtenu !", true);
    }

    /** Encaisse un paiement (espèces ou carte EuroBank). Renvoie null si payé, sinon le message d'erreur. */
    public static String pay(ServerPlayer p, long price, boolean card) {
        if (price <= 0) return null;
        PermisConfig.Root cfg = PermisConfig.get();
        if (!card) {
            if (!cfg.allowCash || !EuroBankCompat.available()) return "Paiement en espèces indisponible.";
            long cash = EuroBankCompat.cash(p);
            if (cash < price) return "Il te manque " + EuroBankCompat.format(price - Math.max(0, cash)) + " en espèces.";
            return EuroBankCompat.payCash(p, price) ? null : "Paiement refusé.";
        }
        if (!cfg.allowCard) return "Paiement par carte indisponible.";
        return EuroBankCompat.payCard(p, price);
    }

    /** Cartes que le joueur peut refaire : "g:conduire" si au moins une catégorie est valide, sinon chaque licence valide hors groupe. */
    public static java.util.LinkedHashMap<String, String> duplicable(ServerPlayer p) {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        PermisConfig.Root cfg = PermisConfig.get();
        for (PermisConfig.CardGroup g : cfg.cardGroups) {
            for (PermisConfig.Licence l : cfg.inGroup(g.id)) {
                if (Licences.isValid(p.server, p.getUUID(), l.id)) {
                    out.put("g:" + g.id, g.name);
                    break;
                }
            }
        }
        for (PermisConfig.Licence l : cfg.licences) {
            if (cfg.group(l.cardGroup) == null && Licences.isValid(p.server, p.getUUID(), l.id)) out.put("l:" + l.id, l.name);
        }
        return out;
    }

    /** Duplicata payant : nouvelle carte, l'ancienne (perdue / volée) est annulée. */
    public static void duplicate(ServerPlayer p, String key, boolean card) {
        if (!duplicable(p).containsKey(key)) {
            reply(p, "Tu n'as pas de papier valide de ce type.", false);
            return;
        }
        if (PermisCardItem.hasActiveCard(p, key)) {
            reply(p, "Tu as déjà cette carte sur toi.", false);
            return;
        }
        long price = Math.round(PermisConfig.get().duplicatePrice * 100);
        String err = pay(p, price, card);
        if (err != null) {
            reply(p, err, false);
            return;
        }
        String licence;
        if (key.startsWith("g:")) {
            licence = null;
            for (PermisConfig.Licence l : PermisConfig.get().inGroup(key.substring(2))) {
                if (Licences.isValid(p.server, p.getUUID(), l.id)) {
                    licence = l.id;
                    break;
                }
            }
        } else licence = key.substring(2);
        ItemStack c = PermisCardItem.create(p, licence);
        if (!p.getInventory().add(c)) p.drop(c, false);
        Msg.ok(p, "Duplicata émis" + (price > 0 ? " pour " + EuroBankCompat.format(price) : "") + ". Ton ancienne carte est annulée.");
        reply(p, "Duplicata remis : l'ancienne carte est annulée.", true);
    }

    public static void refund(ServerPlayer p, long price, boolean card) {
        EuroBankCompat.refund(p, price, card);
    }

    /** Donne la licence + la carte physique (achat ou réussite d'un test). */
    public static void obtain(ServerPlayer p, String licence) {
        obtain(p, licence, -1);
    }

    /** days &lt; 0 : durée de la config ; 0 : permanent. */
    public static void obtain(ServerPlayer p, String licence, int days) {
        Licences.grant(p.server, p.getUUID(), p.getGameProfile().getName(), licence, days);
        if (PermisConfig.get().giveCard) giveCard(p, licence);
    }

    /**
     * Carte physique. Pour une licence d'une carte groupée (permis de conduire), la catégorie est ajoutée
     * sur la carte que le joueur possède déjà : pas de nouvelle carte.
     */
    public static void giveCard(ServerPlayer p, String licence) {
        PermisConfig.Licence def = PermisConfig.get().licence(licence);
        PermisConfig.CardGroup g = def == null ? null : PermisConfig.get().group(def.cardGroup);
        if (g != null) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (PermisCardItem.isGroupCard(st, g.id, p.getUUID()) && !PermisCardItem.isCancelled(st, com.minenorth_permis.data.PermisData.get(p.server))) {
                    PermisCardItem.refresh(st, p);
                    Msg.ok(p, "Catégorie " + (def.code.isEmpty() ? def.name : def.code + " (" + def.name + ")") + " ajoutée sur ton " + g.name + ".");
                    return;
                }
            }
        }
        ItemStack card = PermisCardItem.create(p, licence);
        if (!p.getInventory().add(card)) p.drop(card, false);
    }

    private static void reply(ServerPlayer p, String msg, boolean ok) {
        Network.openMenu(p, MenuStatePacket.SHOP, false, msg, ok);
    }
}
