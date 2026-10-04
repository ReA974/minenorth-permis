package com.minenorth_permis;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Messages chat au style MineNorth : préfixe [Permis] cyan. */
public final class Msg {
    private Msg() {}

    public static MutableComponent prefix() {
        return Component.literal("[Permis] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    public static void ok(ServerPlayer p, String s) {
        p.sendSystemMessage(prefix().append(Component.literal(s).withStyle(ChatFormatting.GREEN)));
    }

    public static void info(ServerPlayer p, String s) {
        p.sendSystemMessage(prefix().append(Component.literal(s).withStyle(ChatFormatting.WHITE)));
    }

    public static void warn(ServerPlayer p, String s) {
        p.sendSystemMessage(prefix().append(Component.literal(s).withStyle(ChatFormatting.YELLOW)));
    }

    public static void err(ServerPlayer p, String s) {
        p.sendSystemMessage(prefix().append(Component.literal(s).withStyle(ChatFormatting.RED)));
    }

    public static Component okc(String s) {
        return prefix().append(Component.literal(s).withStyle(ChatFormatting.GREEN));
    }

    public static Component errc(String s) {
        return prefix().append(Component.literal(s).withStyle(ChatFormatting.RED));
    }
}
