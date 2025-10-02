package net.liopyu.civilization.events.client;

import net.liopyu.civilization.client.render.AdventurerRenderer;
import net.liopyu.civilization.registry.ModEntities;
import net.liopyu.civilization.registry.ModMenus;
import net.liopyu.civilization.screen.AdventurerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ADVENTURER.get(), AdventurerRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent e) {
        e.register(ModMenus.ADVENTURER_MENU.get(), AdventurerScreen::new);
    }
}
