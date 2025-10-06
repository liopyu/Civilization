package net.liopyu.civilization.ai.brain;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

public final class AdventurerMemories {
    public static final DeferredRegister<MemoryModuleType<?>> MEMORIES = DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, "civilization");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Object>> NEAREST_TREE = MEMORIES.register("nearest_tree", () -> new MemoryModuleType<>(Optional.empty()));
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Object>> MINING_TARGET = MEMORIES.register("mining_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Object>> TUNNELING = MEMORIES.register("tunneling", () -> new MemoryModuleType<>(Optional.empty()));
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Object>> MODE_ACTIVITY = MEMORIES.register("mode_activity", () -> new MemoryModuleType<>(Optional.empty()));

    private AdventurerMemories() {
    }
}
