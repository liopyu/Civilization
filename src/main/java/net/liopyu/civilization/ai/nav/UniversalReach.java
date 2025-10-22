package net.liopyu.civilization.ai.nav;

import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;

public final class UniversalReach {
    public static boolean reach(Adventurer adv, BlockPos target, double reach, int flags, int prio, int ttl) {
        double cx = target.getX() + 0.5, cy = target.getY() + 0.5, cz = target.getZ() + 0.5;
        if (adv.distanceToSqr(cx, cy, cz) <= reach * reach) return true;
        NavRequests.submit(adv, new NavTask(target, reach, flags, prio, adv.tickCount + ttl));
        return false;
    }

    private UniversalReach() {
    }
}
