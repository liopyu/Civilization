package net.liopyu.civilization.ai.goal.active;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public final class SetHomeGoal extends ModeScopedGoal {
    private BlockPos target;
    private int repathCooldown;
    private int dwellTicks;

    public SetHomeGoal(Adventurer a) {
        super(a, ActionMode.SET_HOME);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    protected void onEnter(ActionMode mode) {
        target = null;
        repathCooldown = 0;
        dwellTicks = 0;
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        target = null;
        dwellTicks = 0;
    }

    @Override
    public void tick() {
        if (adv.hasHome()) {
            adv.controller().requestMode(ActionMode.RETURN_HOME, 750, 80, 20);
            return;
        }
        if (target == null) {
            target = findCandidateHome(adv, 6);
            if (target == null) {
                if (adv.tickCount % 60 == 0) adv.controller().requestMode(ActionMode.EXPLORE, 300, 80, 20);
                return;
            }
        }
        double rx = target.getX() + 0.5;
        double ry = target.getY() + 0.02;
        double rz = target.getZ() + 0.5;
        double r = adv.entityInteractionRange() + 1.25;
        double d2 = adv.distanceToSqr(rx, ry, rz);

        if (d2 > r * r) {
            if (repathCooldown <= 0 || adv.getNavigation().isDone()) {
                adv.getNavigation().moveTo(rx, ry, rz, 1.1);
                repathCooldown = 10;
            } else repathCooldown--;
            return;
        }

        adv.getNavigation().stop();
        if (!isSafeSpot(adv.level(), target)) {
            target = null;
            return;
        }
        double reach = adv.entityInteractionRange() + 1.25;
        if (!net.liopyu.civilization.ai.nav.UniversalReach.reach(adv, target, reach,
                0, 600, 20 * 8)) return;

        if (dwellTicks == 0) dwellTicks = 20;
        else {
            dwellTicks--;
            if (dwellTicks <= 0) {
                LogUtils.getLogger().info("Setting home pos to: " + target.toString());
                adv.setHomePos(target);
                adv.level().playSound(null, adv.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.NEUTRAL, 0.6f, 1.2f);
                adv.controller().requestMode(ActionMode.RETURN_HOME, 800, 80, 20);
            }
        }
    }

    private static BlockPos findCandidateHome(Adventurer a, int radius) {
        BlockPos o = a.blockPosition();
        Level lvl = a.level();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        int r = Math.max(1, radius);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 2; y >= -2; y--) {
                    BlockPos p = o.offset(x, y, z);
                    if (!a.isValidWorkPos(p)) continue;
                    if (!isSafeSpot(lvl, p)) continue;
                    double d2 = a.distanceToSqr(p.getX() + 0.5, p.getY() + 0.02, p.getZ() + 0.5);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p.immutable();
                    }
                }
            }
        }
        if (best == null && isSafeSpot(lvl, o)) return o.immutable();
        return best;
    }

    private static boolean isSafeSpot(Level lvl, BlockPos p) {
        if (!lvl.isLoaded(p)) return false;
        BlockState below = lvl.getBlockState(p.below());
        if (!below.isSolidRender(lvl, p.below())) return false;
        BlockState here = lvl.getBlockState(p);
        BlockState above = lvl.getBlockState(p.above());
        return here.isAir() && above.isAir();
    }
}
