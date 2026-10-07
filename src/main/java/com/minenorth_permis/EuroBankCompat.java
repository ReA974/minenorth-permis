package com.minenorth_permis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Paiements du mod Permis. Carte : MineNorth API (banque + trésor public). Espèces : billets EuroBank, par réflexion.
 * Tout ce qui est encaissé part au trésor public sous la source "permis" ; un remboursement l'en reprend.
 */
public final class EuroBankCompat {
    private static boolean init, ok;
    private static Method cashIn, takeAllCash, giveCash, check, charge, balance, hasAccount, payMessage, refund;

    private static final String SOURCE = "permis";

    private EuroBankCompat() {}

    public static boolean available() {
        if (!init) {
            init = true;
            if (!ModList.get().isLoaded("minenorth_eurobank")) return ok = false;
            try {
                Class<?> money = Class.forName("com.minenorth_eurobank.Money");
                Class<?> api = Class.forName("com.minenorth_eurobank.api.BankApi");
                Class<?> res = Class.forName("com.minenorth_eurobank.api.PayResult");
                cashIn = money.getMethod("cashIn", Player.class);
                takeAllCash = money.getMethod("takeAllCash", Player.class);
                giveCash = money.getMethod("giveCash", Player.class, long.class);
                check = api.getMethod("check", ServerPlayer.class, long.class);
                charge = api.getMethod("charge", ServerPlayer.class, long.class);
                balance = api.getMethod("balance", ServerPlayer.class);
                hasAccount = api.getMethod("hasAccount", ServerPlayer.class);
                refund = api.getMethod("refund", ServerPlayer.class, long.class);
                payMessage = res.getMethod("message");
                ok = true;
            } catch (Exception e) {
                MinenorthPermis.LOG.error("[Permis] API EuroBank introuvable, paiements désactivés", e);
                ok = false;
            }
        }
        return ok;
    }

    /** Espèces sur le joueur, en centimes (-1 si EuroBank absent). */
    public static long cash(Player p) {
        if (!available()) return -1;
        try {
            return (long) cashIn.invoke(null, p);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Paie en espèces (rend la monnaie). */
    public static boolean payCash(Player p, long cents) {
        if (!available()) return false;
        try {
            if ((long) cashIn.invoke(null, p) < cents) return false;
            long taken = (long) takeAllCash.invoke(null, p);
            long change = taken - cents;
            if (change > 0) giveCash.invoke(null, p, change);
            if (p instanceof ServerPlayer sp) fr.minenorth.api.MineNorth.treasury().collect(sp.server, cents, SOURCE);
            return true;
        } catch (Exception e) {
            MinenorthPermis.LOG.error("[Permis] Paiement espèces échoué", e);
            return false;
        }
    }

    /** null si le paiement par carte est possible, sinon le message d'erreur. */
    public static String cardCheck(ServerPlayer p, long cents) {
        fr.minenorth.api.PayResult r = fr.minenorth.api.MineNorth.bank().check(p, cents);
        return r.ok() ? null : r.message();
    }

    /** null si payé, sinon le message d'erreur. */
    public static String payCard(ServerPlayer p, long cents) {
        fr.minenorth.api.PayResult r = fr.minenorth.api.MineNorth.bank().charge(p, cents, SOURCE);
        return r.ok() ? null : r.message();
    }

    /** Rembourse un paiement (espèces rendues ou compte recrédité). */
    public static void refund(ServerPlayer p, long cents, boolean card) {
        if (cents <= 0) return;
        if (card) {
            fr.minenorth.api.MineNorth.bank().refund(p.server, p.getUUID(), cents, SOURCE);
            return;
        }
        if (!available()) return;
        try {
            giveCash.invoke(null, p, cents);
            fr.minenorth.api.MineNorth.treasury().collect(p.server, -cents, SOURCE);
        } catch (Exception e) {
            MinenorthPermis.LOG.error("[Permis] Remboursement échoué ({} centimes à {})", cents, p.getGameProfile().getName(), e);
        }
    }

    /** Solde du compte en centimes, -1 si pas de compte / EuroBank absent. */
    public static long balance(ServerPlayer p) {
        var bank = fr.minenorth.api.MineNorth.bank();
        return bank.hasAccount(p.server, p.getUUID()) ? bank.balance(p.server, p.getUUID()) : -1;
    }

    public static String format(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (a / 100) + "," + String.format("%02d", a % 100) + " €";
    }
}
