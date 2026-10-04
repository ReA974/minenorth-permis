package com.minenorth_permis.net;

import com.minenorth_permis.Licences;
import com.minenorth_permis.PermisConfig;
import com.minenorth_permis.data.PermisData;
import com.minenorth_permis.tests.DrivingTests;
import com.minenorth_permis.tests.ShootingTests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Serveur -> client (staff) : liste des joueurs + détail des permis du joueur sélectionné. */
public class AdminStatePacket {
    public record Player(UUID id, String name, boolean online, int valid) {}

    public record Lic(String id, String name, String code, String category, boolean valid, long expiry) {}

    public boolean open;
    public String message = "";
    public boolean messageOk = true;
    public final List<Player> players = new ArrayList<>();
    /** Détail (si selected != null). */
    public UUID selected;
    public String selName = "";
    public boolean selOnline, selInTest;
    public int points, maxPoints;
    public boolean showPoints;
    public int cooldowns;
    public int validityDays;
    public final List<Lic> lics = new ArrayList<>();

    public static AdminStatePacket compute(MinecraftServer s, UUID selected, boolean open, String message, boolean ok) {
        PermisConfig.Root cfg = PermisConfig.get();
        PermisData d = PermisData.get(s);
        AdminStatePacket m = new AdminStatePacket();
        m.open = open;
        m.message = message == null ? "" : message;
        m.messageOk = ok;
        m.maxPoints = cfg.maxPoints;
        m.showPoints = cfg.anyPoints();
        m.validityDays = cfg.validityDays;

        Map<UUID, String> names = new LinkedHashMap<>();
        d.holders().forEach((id, h) -> names.put(id, h.name == null || h.name.isEmpty() ? id.toString().substring(0, 8) : h.name));
        for (ServerPlayer p : s.getPlayerList().getPlayers()) names.put(p.getUUID(), p.getGameProfile().getName());
        names.forEach((id, n) -> {
            int valid = 0;
            for (PermisConfig.Licence l : cfg.licences) if (Licences.isValid(s, id, l.id)) valid++;
            m.players.add(new Player(id, n, s.getPlayerList().getPlayer(id) != null, valid));
        });
        m.players.sort(Comparator.comparing((Player p) -> !p.online()).thenComparing(p -> p.name().toLowerCase()));

        if (selected != null && names.containsKey(selected)) {
            m.selected = selected;
            m.selName = names.get(selected);
            m.selOnline = s.getPlayerList().getPlayer(selected) != null;
            m.selInTest = DrivingTests.inTest(selected) || ShootingTests.inTest(selected);
            m.points = Licences.points(s, selected);
            PermisData.Holder h = d.peek(selected);
            if (h != null) {
                long now = System.currentTimeMillis();
                m.cooldowns = (int) h.cooldowns.values().stream().filter(v -> v > now).count();
            }
            for (PermisConfig.Licence l : cfg.licences) {
                boolean v = Licences.isValid(s, selected, l.id);
                Long exp = v ? Licences.expiry(s, selected, l.id) : null;
                m.lics.add(new Lic(l.id, l.name, l.code, l.category, v, exp == null ? 0 : exp));
            }
        }
        return m;
    }

    public static void encode(AdminStatePacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.open);
        b.writeUtf(m.message);
        b.writeBoolean(m.messageOk);
        b.writeVarInt(m.players.size());
        for (Player p : m.players) {
            b.writeUUID(p.id);
            b.writeUtf(p.name);
            b.writeBoolean(p.online);
            b.writeVarInt(p.valid);
        }
        b.writeBoolean(m.selected != null);
        if (m.selected != null) {
            b.writeUUID(m.selected);
            b.writeUtf(m.selName);
            b.writeBoolean(m.selOnline);
            b.writeBoolean(m.selInTest);
            b.writeVarInt(m.points);
            b.writeVarInt(m.cooldowns);
            b.writeVarInt(m.lics.size());
            for (Lic l : m.lics) {
                b.writeUtf(l.id);
                b.writeUtf(l.name);
                b.writeUtf(l.code);
                b.writeUtf(l.category);
                b.writeBoolean(l.valid);
                b.writeLong(l.expiry);
            }
        }
        b.writeVarInt(m.maxPoints);
        b.writeBoolean(m.showPoints);
        b.writeVarInt(m.validityDays);
    }

    public static AdminStatePacket decode(FriendlyByteBuf b) {
        AdminStatePacket m = new AdminStatePacket();
        m.open = b.readBoolean();
        m.message = b.readUtf();
        m.messageOk = b.readBoolean();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) m.players.add(new Player(b.readUUID(), b.readUtf(), b.readBoolean(), b.readVarInt()));
        if (b.readBoolean()) {
            m.selected = b.readUUID();
            m.selName = b.readUtf();
            m.selOnline = b.readBoolean();
            m.selInTest = b.readBoolean();
            m.points = b.readVarInt();
            m.cooldowns = b.readVarInt();
            int k = b.readVarInt();
            for (int i = 0; i < k; i++) m.lics.add(new Lic(b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readBoolean(), b.readLong()));
        }
        m.maxPoints = b.readVarInt();
        m.showPoints = b.readBoolean();
        m.validityDays = b.readVarInt();
        return m;
    }

    public static void handle(AdminStatePacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_permis.client.ClientHooks.handleAdmin(m)));
        ctx.setPacketHandled(true);
    }
}
