package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;

public final class ReturnHomeGoal extends ModeScopedGoal {
    private BlockPos home;
    private int repathCooldown;
    private int dwellTicks;

    public ReturnHomeGoal(Adventurer a) {
        super(a, ActionMode.RETURN_HOME);
    }

    @Override
    protected void onEnter(ActionMode mode) {
        home = adv.getHomePos().orElse(null);
        repathCooldown = 0;
        dwellTicks = 0;
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        home = null;
        dwellTicks = 0;
    }

    @Override
    public void tick() {
        if (!adv.hasHome()) {
            adv.controller().requestMode(ActionMode.SET_HOME, 700, 100, 20);
            return;
        }
        if (home == null || !home.equals(adv.getHomePos().orElse(null))) {
            home = adv.getHomePos().orElse(null);
            repathCooldown = 0;
        }
        if (home == null) return;

        double rx = home.getX() + 0.5;
        double ry = home.getY() + 0.5;
        double rz = home.getZ() + 0.5;
        double r = adv.entityInteractionRange() + 1.5;
        double d2 = adv.distanceToSqr(rx, ry, rz);

        if (d2 <= r * r) {
            adv.getNavigation().stop();
            adv.getLookControl().setLookAt(rx, ry, rz);
            if (dwellTicks == 0) dwellTicks = 40;
            else {
                dwellTicks--;
                if (dwellTicks <= 0) {
                    if (!adv.level().isDay()) adv.controller().requestMode(ActionMode.SLEEP, 650, 80, 20);
                    else adv.controller().requestMode(ActionMode.EXPLORE, 300, 80, 0);
                }
            }
            return;
        }

        if (repathCooldown <= 0 || adv.getNavigation().isDone()) {
            adv.getNavigation().moveTo(rx, ry, rz, 1.1);
            repathCooldown = 10;
        } else {
            repathCooldown--;
        }
    }
}
