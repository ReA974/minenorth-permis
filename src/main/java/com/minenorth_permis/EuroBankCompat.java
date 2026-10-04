package com.minenorth_permis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Pont vers le mod d'économie minenorth_eurobank (espèces + carte), par réflexion :
 * aucune dépendance de compilation, et le mod reste chargeable sans EuroBank (paiements alors indisponibles).
 */
public final class EuroBankCompat {
    private static boolean init, ok;
    private static Method cashIn, takeAllCash, giveCash, check, charge, balance, hasAccount, payMessage, refund;

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
            return true;
        } catch (Exception e) {
            MinenorthPermis.LOG.error("[Permis] Paiement espèces échoué", e);
            return false;
        }
    }

    /** null si le paiement par carte est possible, sinon le message d'erreur. */
    public static String cardCheck(ServerPlayer p, long cents) {
        if (!available()) return "Paiement par carte indisponible.";
        try {
            Enum<?> r = (Enum<?>) check.invoke(null, p, cents);
            return r.name().equals("OK") ? null : (String) payMessage.invoke(r);
        } catch (Exception e) {
            return "Erreur bancaire.";
        }
    }

    /** null si payé, sinon le message d'erreur. */
    public static String payCard(ServerPlayer p, long cents) {
        if (!available()) return "Paiement par carte indisponible.";
        try {
            Enum<?> r = (Enum<?>) charge.invoke(null, p, cents);
            return r.name().equals("OK") ? null : (String) payMessage.invoke(r);
        } catch (Exception e) {
            MinenorthPermis.LOG.error("[Permis] Paiement carte échoué", e);
            return "Erreur bancaire.";
        }
    }

    /** Rembourse un paiement (espèces rendues ou compte recrédité). */
    public static void refund(ServerPlayer p, long cents, boolean card) {
        if (!available() || cents <= 0) return;
        try {
            if (card) refund.invoke(null, p, cents);
            else giveCash.invoke(null, p, cents);
        } catch (Exception e) {
            MinenorthPermis.LOG.error("[Permis] Remboursement échoué ({} centimes à {})", cents, p.getGameProfile().getName(), e);
        }
    }

    /** Solde du compte en centimes, -1 si pas de compte / EuroBank absent. */
    public static long balance(ServerPlayer p) {
        if (!available()) return -1;
        try {
            if (!(boolean) hasAccount.invoke(null, p)) return -1;
            return (long) balance.invoke(null, p);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String format(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (a / 100) + "," + String.format("%02d", a % 100) + " €";
    }
}
