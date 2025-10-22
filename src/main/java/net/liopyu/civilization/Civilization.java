package net.liopyu.civilization;

import com.mojang.logging.LogUtils;

import net.liopyu.civilization.ai.core.BuildPlanCatalog;
import net.liopyu.civilization.ai.core.ModAdventurerAI;
import net.liopyu.civilization.client.BuildPreviewRenderer;
import net.liopyu.civilization.net.Net;
import net.liopyu.civilization.registry.ModEntities;
import net.liopyu.civilization.registry.ModItems;
import net.liopyu.civilization.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(Civilization.MODID)
public class Civilization {
    public static final String MODID = "civilization";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Civilization(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModMenus.register(modEventBus);
        ModAdventurerAI.bootstrap();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
    }

    private void commonSetup(final FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            BuildPlanCatalog.register(ModEntities.ADVENTURER.get(), ResourceLocation.fromNamespaceAndPath("civilization", "test"));
        });
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent e) {
        Net.register(e);
    }
}
