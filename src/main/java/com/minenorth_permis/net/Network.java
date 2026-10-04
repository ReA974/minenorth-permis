package com.minenorth_permis.net;

import com.minenorth_permis.Licences;
import com.minenorth_permis.MinenorthPermis;
import com.minenorth_permis.PermisConfig;
import com.minenorth_permis.items.PermisCardItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Network {
    private static final String PROTOCOL = "5";   // 5 : menu staff retiré (panneau minenorth_admin)
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MinenorthPermis.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Menus ouverts par le serveur (PNJ / commande) : joueur -> fin de validité (ms). Anti-triche sur les achats. */
    public static final Map<UUID, Long> MENU_SESSIONS = new ConcurrentHashMap<>();
    private static final long SESSION_MS = 10 * 60_000L;
    /** Zone de tir pour laquelle le joueur a ouvert l'écran de paiement de l'examen. */
    public static final Map<UUID, String> TIR_PENDING = new ConcurrentHashMap<>();

    private Network() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, MenuStatePacket.class, MenuStatePacket::encode, MenuStatePacket::decode, MenuStatePacket::handle);
        CHANNEL.registerMessage(id++, MenuActionPacket.class, MenuActionPacket::encode, MenuActionPacket::decode, MenuActionPacket::handle);
        CHANNEL.registerMessage(id++, HudPacket.class, HudPacket::encode, HudPacket::decode, HudPacket::handle);
        CHANNEL.registerMessage(id++, MarkersPacket.class, MarkersPacket::encode, MarkersPacket::decode, MarkersPacket::handle);
        CHANNEL.registerMessage(id++, CardPacket.class, CardPacket::encode, CardPacket::decode, CardPacket::handle);
    }

    public static void send(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static boolean hasSession(ServerPlayer p) {
        Long until = MENU_SESSIONS.get(p.getUUID());
        return until != null && until > System.currentTimeMillis();
    }

    /** Ouvre (ou rafraîchit) un écran pour le joueur, sur ses propres permis. */
    public static void openMenu(ServerPlayer p, byte screen, boolean open, String message, boolean ok) {
        if (screen != MenuStatePacket.MY) MENU_SESSIONS.put(p.getUUID(), System.currentTimeMillis() + SESSION_MS);
        send(p, MenuStatePacket.compute(p, p.getUUID(), p.getGameProfile().getName(), screen, open, message, ok));
    }

    /** Écran de paiement de l'épreuve de tir (zone peut être null : aucune zone trouvée). */
    public static void openTir(ServerPlayer p, String zone, boolean open, String message, boolean ok) {
        MENU_SESSIONS.put(p.getUUID(), System.currentTimeMillis() + SESSION_MS);
        if (zone != null) TIR_PENDING.put(p.getUUID(), zone);
        else TIR_PENDING.remove(p.getUUID());
        MenuStatePacket m = MenuStatePacket.compute(p, p.getUUID(), p.getGameProfile().getName(), MenuStatePacket.TIR, open, message, ok);
        m.tirZone = zone == null ? "" : zone;
        String why = com.minenorth_permis.tests.ShootingTests.blocker(p, zone);
        m.blocker = why == null ? "" : why;
        send(p, m);
    }

    /** Ferme l'écran du joueur s'il est encore sur ce type de menu. */
    public static void closeMenu(ServerPlayer p, byte screen) {
        MENU_SESSIONS.remove(p.getUUID());
        TIR_PENDING.remove(p.getUUID());
        MenuStatePacket m = MenuStatePacket.compute(p, p.getUUID(), p.getGameProfile().getName(), screen, false, "", true);
        m.close = true;
        send(p, m);
    }

    /** Ouvre "Mes permis" d'un autre joueur (staff, lecture seule). */
    public static void openMyLicences(ServerPlayer viewer, UUID target, String targetName) {
        send(viewer, MenuStatePacket.compute(viewer, target, targetName, MenuStatePacket.MY, true, "", true));
    }

    public static void sendMarkers(ServerPlayer p, String channel, int duration, List<MarkersPacket.Marker> list) {
        send(p, new MarkersPacket(channel, p.level().dimension().location().toString(), duration, list));
    }

    /** Envoie la vue d'une carte (données vérifiées en direct). */
    public static void sendCard(ServerPlayer to, ItemStack card, String shownBy) {
        CompoundTag t = card.getTag();
        UUID holder = PermisCardItem.holder(card);
        if (t == null || holder == null) return;
        PermisConfig.Root cfg = PermisConfig.get();
        CardPacket m = new CardPacket();
        m.style = t.getString("Style").isEmpty() ? "permis" : t.getString("Style");
        m.holder = holder;
        m.holderName = t.getString("HolderName");
        m.shownBy = shownBy;
        m.points = Licences.points(to.server, holder);
        m.maxPoints = cfg.maxPoints;
        m.cancelled = PermisCardItem.isCancelled(card, com.minenorth_permis.data.PermisData.get(to.server));
        PermisConfig.CardGroup g = cfg.group(t.getString("Group"));
        if (g != null) {
            // Carte groupée : une ligne par catégorie (B, C, A…), état vérifié en direct
            m.licenceName = g.name;
            m.style = g.style;
            m.showPoints = g.points;
            for (PermisConfig.Licence l : cfg.inGroup(g.id)) m.lines.add(line(to, holder, l.id, l.code, l.name));
        } else {
            String lic = t.getString("Licence");
            PermisConfig.Licence def = cfg.licence(lic);
            m.licenceName = def != null ? def.name : t.getString("LicenceName");
            m.showPoints = false;
            CardPacket.Line ln = line(to, holder, lic, "", m.licenceName);
            if (!ln.valid() && t.getLong("Expiry") > 0) ln = new CardPacket.Line("", m.licenceName, false, t.getLong("Issued"), t.getLong("Expiry"));
            m.lines.add(ln);
        }
        send(to, m);
    }

    private static CardPacket.Line line(ServerPlayer to, UUID holder, String lic, String code, String name) {
        boolean valid = Licences.isValid(to.server, holder, lic);
        Long exp = Licences.expiry(to.server, holder, lic);
        Long iss = Licences.issued(to.server, holder, lic);
        return new CardPacket.Line(code, name, valid, valid && iss != null ? iss : 0, valid && exp != null ? exp : 0);
    }
}
