package net.liopyu.civilization.ai.core;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class ValueKey<T> {
    private final ResourceLocation id;
    private final Class<T> type;

    public ValueKey(ResourceLocation id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    public static <T> ValueKey<T> of(String namespace, String path, Class<T> type) {
        return new ValueKey<>(ResourceLocation.fromNamespaceAndPath(namespace, path), type);
    }

    public ResourceLocation id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValueKey<?> k)) return false;
        return id.equals(k.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
