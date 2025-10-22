package net.liopyu.civilization.ai.nav;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public final class NavTask {
    public static final int ALLOW_PILLAR = 1;
    public static final int ALLOW_STAIRS = 2;
    public static final int ALLOW_TUNNEL = 4;

    public final UUID id = UUID.randomUUID();
    public final BlockPos target;
    public final double reach;
    public final int flags;
    public final int priority;
    public final int untilTick;

    public NavTask(BlockPos target, double reach, int flags, int priority, int untilTick) {
        this.target = target.immutable();
        this.reach = Math.max(0.5, reach);
        this.flags = flags;
        this.priority = priority;
        this.untilTick = untilTick;
    }
}
