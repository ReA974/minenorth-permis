package com.minenorth_permis.net;

import com.minenorth_permis.Shop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : action dans un écran (validée côté serveur). */
public class MenuActionPacket {
    public enum Action { BUY_CASH, BUY_CARD, START_TEST, CLOSE, PAY_TIR_CASH, PAY_TIR_CARD, DUP_CASH, DUP_CARD }

    public final Action action;
    public final String id;

    public MenuActionPacket(Action action, String id) {
        this.action = action;
        this.id = id;
    }

    public static void encode(MenuActionPacket m, FriendlyByteBuf b) {
        b.writeEnum(m.action);
        b.writeUtf(m.id);
    }

    public static MenuActionPacket decode(FriendlyByteBuf b) {
        return new MenuActionPacket(b.readEnum(Action.class), b.readUtf(64));
    }

    public static void handle(MenuActionPacket m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null) Shop.handle(p, m.action, m.id);
        });
        ctx.setPacketHandled(true);
    }
}
