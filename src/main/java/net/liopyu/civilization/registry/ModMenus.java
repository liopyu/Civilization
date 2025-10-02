package net.liopyu.civilization.registry;

import net.liopyu.civilization.Civilization;
import net.liopyu.civilization.screen.AdventurerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Civilization.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AdventurerMenu>> ADVENTURER_MENU =
            MENUS.register("adventurer_menu",
                    () -> new MenuType<>(AdventurerMenu::clientCtor, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
