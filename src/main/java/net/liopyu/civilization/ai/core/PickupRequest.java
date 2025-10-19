package net.liopyu.civilization.ai.core;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.Set;

public final class PickupRequest {
    public final int priority;
    public final int untilTick;
    public final int radius;
    public final Set<Item> items;
    public final Set<TagKey<Item>> tags;
    public final BlockPos nearHint;

    private PickupRequest(int priority, int untilTick, int radius, Set<Item> items, Set<TagKey<Item>> tags, BlockPos nearHint) {
        this.priority = priority;
        this.untilTick = untilTick;
        this.radius = radius;
        this.items = items;
        this.tags = tags;
        this.nearHint = nearHint;
    }

    public static PickupRequest any(int now, int ttl, int radius) {
        return new PickupRequest(0, now + ttl, radius, new HashSet<>(), new HashSet<>(), null);
    }

    public static PickupRequest ofItems(int now, int ttl, int radius, Set<Item> items) {
        return new PickupRequest(1, now + ttl, radius, new HashSet<>(items), new HashSet<>(), null);
    }

    public static PickupRequest ofTags(int now, int ttl, int radius, Set<TagKey<Item>> tags) {
        return new PickupRequest(1, now + ttl, radius, new HashSet<>(), new HashSet<>(tags), null);
    }

    public PickupRequest withNear(BlockPos p) {
        return new PickupRequest(this.priority, this.untilTick, this.radius, this.items, this.tags, p);
    }
}
