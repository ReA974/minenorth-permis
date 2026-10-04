package com.minenorth_permis.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Serveur -> client : marqueurs 3D à afficher (anneaux de checkpoint, boîtes de cibles, zones).
 * channel : "test" (test en cours) ou "preview" (aperçu admin). Liste vide = efface le canal.
 */
public class MarkersPacket {
    public static final byte RING = 0, BOX = 1;
    public static final int BEAM = 1, NAV = 2, FAINT = 4, WALL = 8;

    public record Marker(byte type, double x, double y, double z, double x2, double y2, double z2,
                         float radius, int color, String label, int flags) {
        public boolean has(int f) {
            return (flags & f) != 0;
        }

        public static Marker ring(double x, double y, double z, float radius, int color, String label, int flags) {
            return new Marker(RING, x, y, z, 0, 0, 0, radius, color, label, flags);
        }

        public static Marker box(double x, double y, double z, double x2, double y2, double z2, int color, String label, int flags) {
            return new Marker(BOX, x, y, z, x2, y2, z2, 0, color, label, flags);
        }
    }

    public final String channel, dim;
    public final int durationTicks;
    public final List<Marker> markers;

    public MarkersPacket(String channel, String dim, int durationTicks, List<Marker> markers) {
        this.channel = channel;
        this.dim = dim;
        this.durationTicks = durationTicks;
        this.markers = markers;
    }

    public static MarkersPacket clear(String channel) {
        return new MarkersPacket(channel, "", 0, new ArrayList<>());
    }

    public static void encode(MarkersPacket m, FriendlyByteBuf b) {
        b.writeUtf(m.channel);
        b.writeUtf(m.dim);
        b.writeVarInt(m.durationTicks);
        b.writeVarInt(m.markers.size());
        for (Marker k : m.markers) {
            b.writeByte(k.type);
            b.writeDouble(k.x);
            b.writeDouble(k.y);
            b.writeDouble(k.z);
            b.writeDouble(k.x2);
            b.writeDouble(k.y2);
            b.writeDouble(k.z2);
            b.writeFloat(k.radius);
            b.writeInt(k.color);
            b.writeUtf(k.label);
            b.writeVarInt(k.flags);
        }
    }

    public static MarkersPacket decode(FriendlyByteBuf b) {
        String ch = b.readUtf();
        String dim = b.readUtf();
        int dur = b.readVarInt();
        int n = b.readVarInt();
        List<Marker> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Marker(b.readByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readDouble(), b.readFloat(), b.readInt(), b.readUtf(), b.readVarInt()));
        }
        return new MarkersPacket(ch, dim, dur, list);
    }

    public static void handle(MarkersPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_permis.client.ClientState.setMarkers(m)));
        ctx.setPacketHandled(true);
    }
}
