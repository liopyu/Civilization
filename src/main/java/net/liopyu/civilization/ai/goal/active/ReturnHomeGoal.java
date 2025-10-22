package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.core.ActionMode;
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
        // Track updates to the stored home position
        if (home == null || !home.equals(adv.getHomePos().orElse(null))) {
            home = adv.getHomePos().orElse(null);
        }
        if (home == null) return;

        final double rx = home.getX() + 0.5;
        final double ry = home.getY() + 0.5;
        final double rz = home.getZ() + 0.5;

        // Let UniversalReach handle pathing and tricky terrain. No terrain edits needed here.
        double reach = adv.entityInteractionRange() + 1.5;
        if (!net.liopyu.civilization.ai.nav.UniversalReach.reach(
                adv,
                home,
                reach,
                0,                // no pillar/tunnel/stairs for returning home
                500,
                20 * 12)) {
            return; // still traveling / adapting
        }

        // In reach: dwell briefly, then switch mode
        adv.getNavigation().stop();
        adv.getLookControl().setLookAt(rx, ry, rz);

        if (dwellTicks == 0) dwellTicks = 40;
        else {
            if (--dwellTicks <= 0) {
                if (!adv.level().isDay()) adv.controller().requestMode(ActionMode.SLEEP, 650, 80, 20);
                else adv.controller().requestMode(ActionMode.EXPLORE, 300, 80, 0);
            }
        }
    }

}
