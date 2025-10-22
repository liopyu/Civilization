package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.ai.core.ExcavationRequest;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;

public final class ExcavationGoal extends ModeScopedGoal {
    private net.minecraft.core.BlockPos target;
    private int mineTicks;
    private int repathCooldown;
    private java.util.UUID currentId;

    public ExcavationGoal(Adventurer a) {
        super(a, ActionMode.EXCAVATE);
    }

    @Override
    protected void onEnter(ActionMode mode) {
        target = null; mineTicks = 0; repathCooldown = 0; currentId = null;
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop(); target = null; currentId = null;
    }

    @Override
    public boolean canUse() {
        if (adv.getActionMode() != ActionMode.EXCAVATE) return false;
        var req = adv.controller().getExcavation();
        return req != null;
    }

    @Override
    public void tick() {
        var req = adv.controller().getExcavation();
        if (req == null) return;
        currentId = req.id;

        if (target == null || !needsClearing(target)) {
            target = findObstruction(req);
            if (target == null) {
                adv.controller().clearExcavation(req.id);
                adv.controller().requestMode(ActionMode.BUILD_SHELTER, 900, 60, 20);
                return;
            }
        }

        double reach = adv.entityInteractionRange() + 1.25;
        if (!net.liopyu.civilization.ai.nav.UniversalReach.reach(
                adv, target, reach,
                net.liopyu.civilization.ai.nav.NavTask.ALLOW_TUNNEL | net.liopyu.civilization.ai.nav.NavTask.ALLOW_STAIRS,
                950, 20 * 20)) {
            mineTicks = 0; return;
        }

        adv.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (adv.tickCount % 6 == 0) adv.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        mineTicks++;
        if (mineTicks >= 30) {
            var lvl = adv.level();
            var st = lvl.getBlockState(target);
            if (!st.isAir() && !st.canBeReplaced()) {
                lvl.destroyBlock(target, true, adv);
                adv.controller().requestPickup(net.liopyu.civilization.ai.core.PickupRequest.any(adv.tickCount, 60, 8).withNear(target));
            }
            target = null;
            mineTicks = 0;
        }
    }

    private boolean needsClearing(net.minecraft.core.BlockPos p) {
        if (!adv.isValidWorkPos(p)) return false;
        if (adv.protectedBlocks().isProtected(p)) return false;
        var st = adv.level().getBlockState(p);
        return !(st.isAir() || st.canBeReplaced());
    }

    private net.minecraft.core.BlockPos findObstruction(ExcavationRequest req) {
        net.minecraft.core.BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        for (int y = req.min.getY(); y <= req.max.getY(); y++) {
            for (int x = req.min.getX(); x <= req.max.getX(); x++) {
                for (int z = req.min.getZ(); z <= req.max.getZ(); z++) {
                    var p = new net.minecraft.core.BlockPos(x, y, z);
                    if (!needsClearing(p)) continue;
                    double d2 = adv.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                    if (d2 < bestD2) {
                        bestD2 = d2; best = p.immutable();
                    }
                }
            }
        }
        return best;
    }
}
