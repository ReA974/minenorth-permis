package com.minenorth_permis.net;

import com.minenorth_permis.EuroBankCompat;
import com.minenorth_permis.Licences;
import com.minenorth_permis.PermisConfig;
import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.tests.DrivingTests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Serveur -> client : tout ce qu'il faut pour les écrans boutique / tests / mes permis. */
public class MenuStatePacket {
    public static final byte SHOP = 0, TESTS = 1, MY = 2, TIR = 3;

    public record Cat(String id, String name, int color, String style) {}

    public record Entry(String id, String name, String category, long price, String test, String testName,
                        boolean valid, long expiry, long cooldown, boolean ready, boolean busy,
                        int timeLimit, int checkpoints) {
        public boolean isTest() {
            return !test.isEmpty();
        }

        /** Obtenu uniquement au stand de tir (permis d'armes) : pas proposé dans la boutique. */
        public boolean atRange() {
            return "tir".equalsIgnoreCase(test);
        }
    }

    public byte screen;
    public boolean open;
    public String targetName = "";
    public String message = "";
    public boolean messageOk;
    public int points, maxPoints, validityDays;
    public long cash, balance;
    public boolean cashAllowed, cardAllowed;
    /** Points affichés seulement s'il existe une carte à points (permis de conduire). */
    public boolean showPoints;
    public String pointsLabel = "";
    /** Ferme l'écran côté client. */
    public boolean close;
    /** Écran TIR : zone, raison de blocage, frais d'examen et règles. */
    public String tirZone = "", blocker = "";
    public long examPrice;
    public int tirHits, tirTime, tirDist;
    public boolean tirAmmoFail;
    /** Duplicatas : prix et cartes que le joueur peut refaire (clé, nom, style, déjà sur lui). */
    public long dupPrice;
    public record Dup(String key, String name, String style, boolean onHand) {}
    public final List<Dup> dups = new ArrayList<>();
    public final List<Cat> cats = new ArrayList<>();
    public final List<Entry> entries = new ArrayList<>();

    public Entry entry(String id) {
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    public static MenuStatePacket compute(ServerPlayer viewer, UUID target, String targetName, byte screen,
                                          boolean open, String message, boolean ok) {
        PermisConfig.Root cfg = PermisConfig.get();
        MenuStatePacket m = new MenuStatePacket();
        m.screen = screen;
        m.open = open;
        m.targetName = targetName;
        m.message = message == null ? "" : message;
        m.messageOk = ok;
        m.points = Licences.points(viewer.server, target);
        m.maxPoints = cfg.maxPoints;
        m.validityDays = cfg.validityDays;
        m.cash = EuroBankCompat.cash(viewer);
        m.balance = EuroBankCompat.balance(viewer);
        m.cashAllowed = cfg.allowCash;
        m.cardAllowed = cfg.allowCard;
        m.showPoints = cfg.anyPoints();
        for (PermisConfig.CardGroup g : cfg.cardGroups) if (g.points) { m.pointsLabel = g.name; break; }
        m.examPrice = com.minenorth_permis.tests.ShootingTests.examPrice();
        m.tirHits = cfg.shooting.hitsRequired;
        m.tirTime = cfg.shooting.timeLimitSeconds;
        m.tirDist = (int) cfg.shooting.maxDistance;
        m.tirAmmoFail = cfg.shooting.failWhenOutOfAmmo;
        m.dupPrice = Math.round(cfg.duplicatePrice * 100);
        if (target.equals(viewer.getUUID())) {
            com.minenorth_permis.Shop.duplicable(viewer).forEach((key, name) -> {
                String style;
                if (key.startsWith("g:")) {
                    PermisConfig.CardGroup g = cfg.group(key.substring(2));
                    style = g == null ? "permis" : g.style;
                } else style = com.minenorth_permis.items.PermisCardItem.styleFor(cfg.licence(key.substring(2)));
                m.dups.add(new Dup(key, name, style, com.minenorth_permis.items.PermisCardItem.hasActiveCard(viewer, key)));
            });
        }
        for (PermisConfig.Category c : cfg.categories) m.cats.add(new Cat(c.id, c.name, c.argb(), c.cardStyle == null ? "licence" : c.cardStyle));
        PermisData data = PermisData.get(viewer.server);
        for (PermisConfig.Licence l : cfg.licences) {
            boolean valid = Licences.isValid(viewer.server, target, l.id);
            Long exp = valid ? Licences.expiry(viewer.server, target, l.id) : null;
            String testName = "";
            long cd = 0;
            boolean ready = false, busy = false;
            int tl = 0, cps = 0;
            if ("tir".equalsIgnoreCase(l.test)) {
                testName = cfg.shooting.name;
                cd = Licences.cooldown(viewer.server, target, "tir");
                ready = !data.ranges().isEmpty();
                tl = cfg.shooting.timeLimitSeconds;
            } else if (!l.test.isEmpty()) {
                PermisConfig.DrivingTest t = cfg.test(l.test);
                if (t != null) {
                    testName = t.name;
                    cd = Licences.cooldown(viewer.server, target, "test:" + t.id);
                    PermisData.Course c = data.course(t.id);
                    ready = c != null && c.start != null && !c.checkpoints.isEmpty();
                    cps = c == null ? 0 : c.checkpoints.size();
                    tl = c != null && c.timeLimit > 0 ? c.timeLimit : t.defaultTimeLimit;
                    busy = DrivingTests.isBusy(t.id);
                }
            }
            m.entries.add(new Entry(l.id, l.name, l.category, l.priceCents(), l.test, testName, valid,
                    exp == null ? 0 : exp, cd, ready, busy, tl, cps));
        }
        return m;
    }

    public static void encode(MenuStatePacket m, FriendlyByteBuf b) {
        b.writeByte(m.screen);
        b.writeBoolean(m.open);
        b.writeUtf(m.targetName);
        b.writeUtf(m.message);
        b.writeBoolean(m.messageOk);
        b.writeVarInt(m.points);
        b.writeVarInt(m.maxPoints);
        b.writeVarInt(m.validityDays);
        b.writeLong(m.cash);
        b.writeLong(m.balance);
        b.writeBoolean(m.cashAllowed);
        b.writeBoolean(m.cardAllowed);
        b.writeBoolean(m.showPoints);
        b.writeUtf(m.pointsLabel);
        b.writeBoolean(m.close);
        b.writeUtf(m.tirZone);
        b.writeUtf(m.blocker);
        b.writeLong(m.examPrice);
        b.writeVarInt(m.tirHits);
        b.writeVarInt(m.tirTime);
        b.writeVarInt(m.tirDist);
        b.writeBoolean(m.tirAmmoFail);
        b.writeLong(m.dupPrice);
        b.writeVarInt(m.dups.size());
        for (Dup d : m.dups) {
            b.writeUtf(d.key);
            b.writeUtf(d.name);
            b.writeUtf(d.style);
            b.writeBoolean(d.onHand);
        }
        b.writeVarInt(m.cats.size());
        for (Cat c : m.cats) {
            b.writeUtf(c.id);
            b.writeUtf(c.name);
            b.writeInt(c.color);
            b.writeUtf(c.style);
        }
        b.writeVarInt(m.entries.size());
        for (Entry e : m.entries) {
            b.writeUtf(e.id);
            b.writeUtf(e.name);
            b.writeUtf(e.category);
            b.writeLong(e.price);
            b.writeUtf(e.test);
            b.writeUtf(e.testName);
            b.writeBoolean(e.valid);
            b.writeLong(e.expiry);
            b.writeLong(e.cooldown);
            b.writeBoolean(e.ready);
            b.writeBoolean(e.busy);
            b.writeVarInt(e.timeLimit);
            b.writeVarInt(e.checkpoints);
        }
    }

    public static MenuStatePacket decode(FriendlyByteBuf b) {
        MenuStatePacket m = new MenuStatePacket();
        m.screen = b.readByte();
        m.open = b.readBoolean();
        m.targetName = b.readUtf();
        m.message = b.readUtf();
        m.messageOk = b.readBoolean();
        m.points = b.readVarInt();
        m.maxPoints = b.readVarInt();
        m.validityDays = b.readVarInt();
        m.cash = b.readLong();
        m.balance = b.readLong();
        m.cashAllowed = b.readBoolean();
        m.cardAllowed = b.readBoolean();
        m.showPoints = b.readBoolean();
        m.pointsLabel = b.readUtf();
        m.close = b.readBoolean();
        m.tirZone = b.readUtf();
        m.blocker = b.readUtf();
        m.examPrice = b.readLong();
        m.tirHits = b.readVarInt();
        m.tirTime = b.readVarInt();
        m.tirDist = b.readVarInt();
        m.tirAmmoFail = b.readBoolean();
        m.dupPrice = b.readLong();
        int nd = b.readVarInt();
        for (int i = 0; i < nd; i++) m.dups.add(new Dup(b.readUtf(), b.readUtf(), b.readUtf(), b.readBoolean()));
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) m.cats.add(new Cat(b.readUtf(), b.readUtf(), b.readInt(), b.readUtf()));
        n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            m.entries.add(new Entry(b.readUtf(), b.readUtf(), b.readUtf(), b.readLong(), b.readUtf(), b.readUtf(),
                    b.readBoolean(), b.readLong(), b.readLong(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt()));
        }
        return m;
    }

    public static void handle(MenuStatePacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_permis.client.ClientHooks.handleMenu(m)));
        ctx.setPacketHandled(true);
    }
}
