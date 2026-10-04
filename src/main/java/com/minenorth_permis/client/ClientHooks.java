package com.minenorth_permis.client;

import com.minenorth_permis.net.CardPacket;
import com.minenorth_permis.net.MenuStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {}

    public static void handleMenu(MenuStatePacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.close) {
            if (mc.screen instanceof MnScreen s && s.state.screen == m.screen) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof MnScreen s && s.state.screen == m.screen && !(m.open && m.screen == MenuStatePacket.MY)) {
            s.update(m);
            return;
        }
        if (!m.open) return;
        mc.setScreen(switch (m.screen) {
            case MenuStatePacket.TESTS -> new TestMenuScreen(m);
            case MenuStatePacket.MY -> new MyLicencesScreen(m, null);
            case MenuStatePacket.TIR -> new TirScreen(m);
            default -> new ShopHubScreen(m);
        });
    }

    public static void handleAdmin(com.minenorth_permis.net.AdminStatePacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminPermisScreen s) s.update(m);
        else if (m.open) mc.setScreen(new AdminPermisScreen(m));
    }

    public static void handleCard(CardPacket m) {
        Minecraft.getInstance().setScreen(new CardScreen(m));
    }
}
