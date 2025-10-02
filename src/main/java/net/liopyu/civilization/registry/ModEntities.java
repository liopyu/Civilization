package net.liopyu.civilization.registry;

import net.liopyu.civilization.Civilization;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    // ENTITY TYPE REGISTER
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Civilization.MODID);

    // DeferredHolder<R, T> where R is the registry base type and T is the concrete type
    public static final DeferredHolder<EntityType<?>, EntityType<Adventurer>> ADVENTURER =
            ENTITIES.register("adventurer", () ->
                    EntityType.Builder.<Adventurer>of(Adventurer::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .build(ResourceLocation.fromNamespaceAndPath(Civilization.MODID, "adventurer").toString())
            );

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(ModEntities::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ADVENTURER.get(), Adventurer.createAttributes().build());
    }
}
