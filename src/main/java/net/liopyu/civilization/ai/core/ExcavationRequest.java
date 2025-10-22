package net.liopyu.civilization.ai.core;

import net.minecraft.core.BlockPos;

public final class ExcavationRequest {
    public final java.util.UUID id;
    public final net.minecraft.core.BlockPos min;
    public final net.minecraft.core.BlockPos max;
    public final int priority;
    public final int untilTick;

    public ExcavationRequest(java.util.UUID id, BlockPos min, BlockPos max, int priority, int untilTick) {
        this.id = id; this.min = min.immutable(); this.max = max.immutable(); this.priority = priority;
        this.untilTick = untilTick;
    }


    public static ExcavationRequest of(net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b, int priority, int now, int ttl) {
        net.minecraft.core.BlockPos min = new net.minecraft.core.BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        net.minecraft.core.BlockPos max = new net.minecraft.core.BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        return new ExcavationRequest(java.util.UUID.randomUUID(), min, max, priority, now + ttl);
    }
}