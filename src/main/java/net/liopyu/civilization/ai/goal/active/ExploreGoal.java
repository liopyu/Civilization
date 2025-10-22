package net.liopyu.civilization.ai.goal.active;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class ExploreGoal extends ModeScopedGoal {
    private static final Logger LOG = LogUtils.getLogger();

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
        LOG.info("[Explore] {} enter", adv.getStringUUID());
    }

    @Override
    public void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        target = null;
        LOG.info("[Explore] {} exit", adv.getStringUUID());
    }

    @Override
    public void tick() {
        if (waitTicks > 0) {
            waitTicks--;
            if (adv.tickCount % 10 == 0)
                adv.getLookControl().setLookAt(
                        adv.getX() + adv.getRandom().nextGaussian(),
                        adv.getEyeY(),
                        adv.getZ() + adv.getRandom().nextGaussian());
            if (adv.tickCount % 20 == 0)
                LOG.info("[Explore] {} waiting ticks={}", adv.getStringUUID(), waitTicks);
            return;
        }

        if (target == null || reached(target)) {
            if (target != null) LOG.info("[Explore] {} reached {}", adv.getStringUUID(), target);
            chooseTarget();
            if (target == null) return;
            LOG.info("[Explore] {} new target {}", adv.getStringUUID(), target);
        }

        // Let UniversalReach do all movement/problem-solving.
        double reach = adv.entityInteractionRange() + 1.5;
        boolean atTarget = net.liopyu.civilization.ai.nav.UniversalReach.reach(
                adv,
                target,
                reach,
                // Exploring can lightly adapt terrain if needed:
                net.liopyu.civilization.ai.nav.NavTask.ALLOW_STAIRS
                        | net.liopyu.civilization.ai.nav.NavTask.ALLOW_TUNNEL
                        | net.liopyu.civilization.ai.nav.NavTask.ALLOW_PILLAR,
                300,                 // budget
                20 * 8               // TTL
        );

        if (!atTarget) {
            // Lightweight “stuck” detection: if we aren’t making progress for a while,
            // pick a fresh wander target.
            if (adv.tickCount % 20 == 0) {
                Vec3 p = adv.position();
                if (p.distanceToSqr(lastPos) < 0.25) {
                    stuckTicks += 20;
                    LOG.info("[Explore] {} possibly stuck {} ticks, target={}", adv.getStringUUID(), stuckTicks, target);
                } else {
                    stuckTicks = 0;
                    lastPos = p;
                }
                if (stuckTicks >= 60) {
                    LOG.info("[Explore] {} stuck, picking new target", adv.getStringUUID());
                    chooseTarget();
                    stuckTicks = 0;
                }
            }
            if (adv.tickCount % 40 == 0)
                LOG.info("[Explore] {} enroute to {} wait={}", adv.getStringUUID(), target, waitTicks);
            return;
        }

        // We arrived within reach; linger a moment so Explore feels “alive”.
        if (adv.tickCount % 20 == 0)
            LOG.info("[Explore] {} reached {}", adv.getStringUUID(), target);
        waitTicks = 10 + adv.getRandom().nextInt(10);
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
            adv.getLookControl().setLookAt(v);
            LOG.info("[Explore] {} chose target {}", adv.getStringUUID(), target);
        } else {
            target = null;
            waitTicks = 20 + adv.getRandom().nextInt(20);
            LOG.info("[Explore] {} no target found, waiting {}", adv.getStringUUID(), waitTicks);
        }
    }
}
