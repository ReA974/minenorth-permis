package com.minenorth_permis.items;

import com.minenorth_permis.MinenorthPermis;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MinenorthPermis.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MinenorthPermis.MODID);

    public static final RegistryObject<PermisCardItem> CARD = ITEMS.register("permis_card", PermisCardItem::new);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minenorth_permis"))
                    .icon(() -> PermisCardItem.preview("permis"))
                    .displayItems((params, out) -> {
                        for (String s : PermisCardItem.STYLES) out.accept(PermisCardItem.preview(s));
                    })
                    .build());

    private ModItems() {}
}
