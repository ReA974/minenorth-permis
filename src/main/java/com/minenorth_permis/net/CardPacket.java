package com.minenorth_permis.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Serveur -> client : affiche une carte de permis (état vérifié en direct côté serveur). */
public class CardPacket {
    /** Une catégorie / licence affichée sur la carte. */
    public record Line(String code, String name, boolean valid, long issued, long expiry) {}

    public String licenceName = "", style = "permis", holderName = "", shownBy = "";
    public UUID holder = new UUID(0, 0);
    public final List<Line> lines = new ArrayList<>();
    public boolean showPoints;
    /** Carte remplacée par un duplicata (perdue / volée). */
    public boolean cancelled;
    public int points, maxPoints;

    public boolean anyValid() {
        for (Line l : lines) if (l.valid) return true;
        return false;
    }

    public static void encode(CardPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.licenceName);
        b.writeUtf(m.style);
        b.writeUtf(m.holderName);
        b.writeUtf(m.shownBy);
        b.writeUUID(m.holder);
        b.writeVarInt(m.lines.size());
        for (Line l : m.lines) {
            b.writeUtf(l.code);
            b.writeUtf(l.name);
            b.writeBoolean(l.valid);
            b.writeLong(l.issued);
            b.writeLong(l.expiry);
        }
        b.writeBoolean(m.showPoints);
        b.writeBoolean(m.cancelled);
        b.writeVarInt(m.points);
        b.writeVarInt(m.maxPoints);
    }

    public static CardPacket decode(FriendlyByteBuf b) {
        CardPacket m = new CardPacket();
        m.licenceName = b.readUtf();
        m.style = b.readUtf();
        m.holderName = b.readUtf();
        m.shownBy = b.readUtf();
        m.holder = b.readUUID();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) m.lines.add(new Line(b.readUtf(), b.readUtf(), b.readBoolean(), b.readLong(), b.readLong()));
        m.showPoints = b.readBoolean();
        m.cancelled = b.readBoolean();
        m.points = b.readVarInt();
        m.maxPoints = b.readVarInt();
        return m;
    }

    public static void handle(CardPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_permis.client.ClientHooks.handleCard(m)));
        ctx.setPacketHandled(true);
    }
}
