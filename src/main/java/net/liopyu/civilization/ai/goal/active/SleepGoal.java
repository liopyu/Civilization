package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;

public final class SleepGoal extends ModeScopedGoal {
    private BlockPos home;
    private int repathCooldown;
    private int sleepTicks;
    private boolean startedSound;

    public SleepGoal(Adventurer a) {
        super(a, ActionMode.SLEEP);
    }

    @Override
    protected void onEnter(ActionMode mode) {
        home = adv.getHomePos().orElse(null);
        repathCooldown = 0;
        sleepTicks = 0;
        startedSound = false;
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        home = null;
        sleepTicks = 0;
        startedSound = false;
    }

    @Override
    public void tick() {
        if (adv.level().isDay()) {
            adv.controller().requestMode(ActionMode.EXPLORE, 400, 80, 0);
            return;
        }
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

        // Approach using UniversalReach. No terrain edits needed.
        double reach = adv.entityInteractionRange() + 1.5;
        if (!net.liopyu.civilization.ai.nav.UniversalReach.reach(
                adv,
                home,
                reach,
                0,                // no pillar/tunnel/stairs to get into bed spot
                600,
                20 * 12)) {
            return; // still traveling / adapting
        }

        // In reach: look, play start sound once, then "sleep" (heal over time)
        adv.getNavigation().stop();
        adv.getLookControl().setLookAt(rx, ry, rz);

        if (!startedSound) {
            adv.playSound(SoundEvents.FOX_SNIFF, 0.3f, 0.8f);
            startedSound = true;
        }

        sleepTicks++;
        if (sleepTicks % 40 == 0) adv.heal(1.5f);
        if (adv.level().isDay() || sleepTicks >= 200) {
            adv.controller().requestMode(ActionMode.EXPLORE, 500, 100, 0);
        }
    }

}
