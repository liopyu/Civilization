package net.liopyu.civilization.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.liopyu.civilization.entity.Adventurer;
import net.liopyu.civilization.ai.util.AiUtil;

import java.util.EnumSet;
import java.util.function.Predicate;

public final class UniversalReachGoal extends Goal {
    private final Adventurer adv;
    private NavTask current;
    private int repathCd;
    private int workCd;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTicks;

    public UniversalReachGoal(Adventurer a) {
        this.adv = a;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        current = NavRequests.peek(adv);
        return current != null;
    }

    @Override
    public boolean canContinueToUse() {
        return current != null && !NavRequests.isDone(adv, current.id);
    }

    @Override
    public void start() {
        repathCd = 0;
        workCd = 0;
        stuckTicks = 0;
        lastPos = adv.position();
    }

    @Override
    public void stop() {
        adv.getNavigation().stop();
        current = null;
        repathCd = 0;
        workCd = 0;
        stuckTicks = 0;
    }

    @Override
    public void tick() {
        if (current == null) {
            current = NavRequests.peek(adv);
            if (current == null) return;
        }

        BlockPos t = current.target;
        double r = current.reach;
        double cx = t.getX() + 0.5, cy = t.getY() + 0.5, cz = t.getZ() + 0.5;
        double d2 = adv.distanceToSqr(cx, cy, cz);
        if (d2 <= r * r) {
            NavRequests.complete(adv, current.id);
            current = null;
            return;
        }

        if (workCd > 0) {
            workCd--;
            return;
        }

        var nav = adv.getNavigation();
        if (needsStrategy(nav)) {
            if (tryClearLineBlocker(t, r)) {
                workCd = 6;
                return;
            }
            if ((current.flags & NavTask.ALLOW_PILLAR) != 0 && t.getY() > adv.blockPosition().getY() + 1) {
                if (AiUtil.peekHasPlaceableBlock(adv) && AiUtil.pillarUpOneBlock(adv, 4)) {
                    workCd = 8;
                    return;
                }
            }
            if ((current.flags & NavTask.ALLOW_STAIRS) != 0 && t.getY() < adv.blockPosition().getY() - 1) {
                if (digStairStepToward(t)) {
                    workCd = 8;
                    return;
                }
            }
            if ((current.flags & NavTask.ALLOW_TUNNEL) != 0) {
                if (tunnelToward(t)) {
                    workCd = 6;
                    return;
                }
            }
        }

        if (repathCd <= 0 || nav.isDone()) {
            nav.moveTo(cx, cy, cz, 1.1);
            repathCd = 10;
        } else {
            repathCd--;
        }

        if (adv.tickCount % 20 == 0) {
            Vec3 p = adv.position();
            if (p.distanceToSqr(lastPos) < 0.25) stuckTicks += 20;
            else {
                stuckTicks = 0; lastPos = p;
            }
        }
    }

    private boolean needsStrategy(PathNavigation nav) {
        if (!nav.isInProgress()) return true;
        if (stuckTicks >= 40) return true;
        return false;
    }

    private boolean tryClearLineBlocker(BlockPos target, double reach) {
        var pred = net.liopyu.civilization.ai.util.AiUtil.obstaclePredicateForMode(adv);
        var b = net.liopyu.civilization.ai.util.AiUtil.firstBlockingAlong(adv, target, pred);
        if (b == null) return false;
        double bx = b.getX() + 0.5, by = b.getY() + 0.5, bz = b.getZ() + 0.5;
        if (adv.distanceToSqr(bx, by, bz) <= reach * reach && !adv.protectedBlocks().isProtected(b)) {
            AiUtil.harvestBlockToInternal(adv, b);
            return true;
        }
        return false;
    }

    private boolean tunnelToward(BlockPos target) {
        Predicate<BlockState> pred = (net.minecraft.world.level.block.state.BlockState s) -> s != null && !s.isAir() && !s.canBeReplaced();
        var b = net.liopyu.civilization.ai.util.AiUtil.firstBlockingAlong(adv, target, pred);
        if (b == null) return false;
        if (adv.protectedBlocks().isProtected(b)) return false;
        return AiUtil.harvestBlockToInternal(adv, b);
    }

    private boolean digStairStepToward(BlockPos target) {
        BlockPos cur = adv.blockPosition();
        int dx = Integer.compare(target.getX(), cur.getX());
        int dz = Integer.compare(target.getZ(), cur.getZ());
        if (Math.abs(target.getX() - cur.getX()) >= Math.abs(target.getZ() - cur.getZ())) dz = 0;
        else dx = 0;
        BlockPos forward = cur.offset(dx, 0, dz);
        BlockPos step = forward.below();
        if (!adv.level().isLoaded(step)) return false;

        BlockPos h1 = step;
        BlockPos h2 = step.above();
        if (!adv.protectedBlocks().isProtected(h1)) AiUtil.harvestBlockToInternal(adv, h1);
        if (!adv.protectedBlocks().isProtected(h2)) AiUtil.harvestBlockToInternal(adv, h2);

        adv.getNavigation().moveTo(step.getX() + 0.5, step.getY() + 0.5, step.getZ() + 0.5, 1.0);
        return true;
    }
}
