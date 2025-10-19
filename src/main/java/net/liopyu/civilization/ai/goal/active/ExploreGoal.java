package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class ExploreGoal extends ModeScopedGoal {
    private BlockPos target;
    private int repathCooldown;
    private int waitTicks;
    private Vec3 lastPos;
    private int stuckTicks;

    public ExploreGoal(Adventurer a) {
        super(a, ActionMode.EXPLORE);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public void onEnter(ActionMode mode) {
        target = null;
        repathCooldown = 0;
        waitTicks = 0;
        lastPos = adv.position();
        stuckTicks = 0;
    }

    @Override
    public void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        target = null;
    }

    @Override
    public void tick() {
        if (waitTicks > 0) {
            waitTicks--;
            if (adv.tickCount % 10 == 0)
                adv.getLookControl().setLookAt(adv.getX() + adv.getRandom().nextGaussian(), adv.getEyeY(), adv.getZ() + adv.getRandom().nextGaussian());
            return;
        }
        if (target == null || reached(target)) {
            chooseTarget();
            if (target == null) return;
            adv.getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1.1);
            return;
        }
        if (repathCooldown <= 0 && adv.getNavigation().isDone()) {
            adv.getNavigation().moveTo(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1.1);
            repathCooldown = 10;
        } else if (repathCooldown > 0) {
            repathCooldown--;
        }
        if (adv.tickCount % 20 == 0) {
            Vec3 p = adv.position();
            if (p.distanceToSqr(lastPos) < 0.25) {
                stuckTicks += 20;
            } else {
                stuckTicks = 0;
                lastPos = p;
            }
            if (stuckTicks >= 60) {
                chooseTarget();
                stuckTicks = 0;
            }
        }
    }

    private boolean reached(BlockPos p) {
        double r = adv.entityInteractionRange() + 1.5;
        return adv.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= r * r;
    }

    private void chooseTarget() {
        Vec3 v = DefaultRandomPos.getPos(adv, 12, 6);
        if (v == null) v = DefaultRandomPos.getPos(adv, 6, 3);
        if (v != null) {
            target = BlockPos.containing(v).immutable();
            repathCooldown = 0;
        } else {
            target = null;
            waitTicks = 20 + adv.getRandom().nextInt(20);
        }
        if (v != null) adv.getLookControl().setLookAt(v);
    }
}
