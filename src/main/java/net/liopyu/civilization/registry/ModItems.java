package net.liopyu.civilization.registry;

import net.liopyu.civilization.Civilization;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Civilization.MODID);

    public static final DeferredHolder<Item, Item> ADVENTURER_SPAWN_EGG =
            ITEMS.register("adventurer_spawn_egg", () ->
                    new DeferredSpawnEggItem(
                            ModEntities.ADVENTURER,
                            0x8E6E53,
                            0xC8BBA3,
                            new Item.Properties()
                    )
            );

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ModItems::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ADVENTURER_SPAWN_EGG.get());
        }
    }
}
