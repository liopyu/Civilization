package net.liopyu.civilization.ai.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.*;

public final class BuildPlanCatalog {
    private static final Map<EntityType<?>, List<ResourceLocation>> CATALOG = new HashMap<>();
    private static final Random RNG = new Random();

    public static void register(EntityType<?> type, ResourceLocation planId) {
        CATALOG.computeIfAbsent(type, k -> new ArrayList<>()).add(planId);
    }

    public static Optional<ResourceLocation> pick(EntityType<?> type) {
        List<ResourceLocation> list = CATALOG.get(type);
        if (list == null || list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(RNG.nextInt(list.size())));
    }

    public static List<ResourceLocation> list(EntityType<?> type) {
        List<ResourceLocation> list = CATALOG.get(type);
        return list == null ? List.of() : List.copyOf(list);
    }
}
