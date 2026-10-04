package com.minenorth_permis.net;

import com.minenorth_permis.admin.AdminService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client (staff) -> serveur : action sur les permis d'un joueur. Réservé aux ops. */
public class AdminActionPacket {
    public enum Op { SELECT, GIVE, REVOKE, CARD, POINTS_SET, CLEAR_COOLDOWNS, STOP_TEST, REFRESH }

    public final Op op;
    public final UUID target;
    public final String licence;
    public final int value;

    public AdminActionPacket(Op op, UUID target, String licence, int value) {
        this.op = op;
        this.target = target == null ? new UUID(0, 0) : target;
        this.licence = licence == null ? "" : licence;
        this.value = value;
    }

    public static void encode(AdminActionPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.op);
        b.writeUUID(m.target);
        b.writeUtf(m.licence);
        b.writeInt(m.value);
    }

    public static AdminActionPacket decode(FriendlyByteBuf b) {
        return new AdminActionPacket(b.readEnum(Op.class), b.readUUID(), b.readUtf(64), b.readInt());
    }

    public static void handle(AdminActionPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null && p.hasPermissions(2)) AdminService.handle(p, m);
        });
        ctx.setPacketHandled(true);
    }
}
