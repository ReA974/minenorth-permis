package com.minenorth_permis.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : panneau HUD d'un test en cours (titre, progression, chrono). active=false le cache. */
public class HudPacket {
    public final boolean active;
    public final String title, line, hint;
    public final int progress, total, seconds, accent;

    public HudPacket(boolean active, String title, String line, int progress, int total, int seconds, String hint, int accent) {
        this.active = active;
        this.title = title;
        this.line = line;
        this.progress = progress;
        this.total = total;
        this.seconds = seconds;
        this.hint = hint;
        this.accent = accent;
    }

    public static HudPacket hide() {
        return new HudPacket(false, "", "", 0, 0, -1, "", 0);
    }

    public static void encode(HudPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.active);
        b.writeUtf(m.title);
        b.writeUtf(m.line);
        b.writeVarInt(m.progress);
        b.writeVarInt(m.total);
        b.writeInt(m.seconds);
        b.writeUtf(m.hint);
        b.writeInt(m.accent);
    }

    public static HudPacket decode(FriendlyByteBuf b) {
        return new HudPacket(b.readBoolean(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readInt(), b.readUtf(), b.readInt());
    }

    public static void handle(HudPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_permis.client.ClientState.setHud(m)));
        ctx.setPacketHandled(true);
    }
}
