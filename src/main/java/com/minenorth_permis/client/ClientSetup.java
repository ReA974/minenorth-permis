package com.minenorth_permis.client;

import com.minenorth_permis.MinenorthPermis;
import com.minenorth_permis.items.ModItems;
import com.minenorth_permis.items.PermisCardItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Enregistrements côté client uniquement. */
public final class ClientSetup {
    private ClientSetup() {}

    @Mod.EventBusSubscriber(modid = MinenorthPermis.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> ItemProperties.register(ModItems.CARD.get(),
                    new ResourceLocation(MinenorthPermis.MODID, "style"),
                    (stack, level, entity, seed) -> PermisCardItem.styleIndex(stack) / 10f));
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("permis_hud", HudOverlay.INSTANCE);
        }
    }

    @Mod.EventBusSubscriber(modid = MinenorthPermis.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent e) {
            WorldMarkers.render(e);
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientState.clear();
        }
    }
}
