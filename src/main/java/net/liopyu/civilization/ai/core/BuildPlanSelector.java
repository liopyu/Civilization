package net.liopyu.civilization.ai.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public final class BuildPlanSelector {
    public static Optional<ResourceLocation> selectFor(EntityType<?> type) {
        return BuildPlanCatalog.pick(type);
    }
}
