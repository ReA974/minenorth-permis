package com.minenorth_permis;

import com.minenorth_permis.items.ModItems;
import com.minenorth_permis.net.Network;
import com.minenorth_permis.tests.ShootingTests;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Permis & Licences MineNorthRP : boutique de permis/licences, tests de conduite (véhicules MTS),
 * épreuve de tir (TACZ), cartes de permis et points. Remplace permis_shop.sk, permis_test.sk et permistir.sk.
 */
@Mod(MinenorthPermis.MODID)
public class MinenorthPermis {
    public static final String MODID = "minenorth_permis";
    public static final Logger LOG = LogUtils.getLogger();

    public MinenorthPermis() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        bus.addListener(this::commonSetup);
        PermisConfig.load();
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            Network.register();
            ShootingTests.hookTacz();
        });
    }
}
