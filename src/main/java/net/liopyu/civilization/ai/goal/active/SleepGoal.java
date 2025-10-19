package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.ActionMode;
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

        if (d2 > r * r) {
            if (repathCooldown <= 0 || adv.getNavigation().isDone()) {
                adv.getNavigation().moveTo(rx, ry, rz, 1.1);
                repathCooldown = 10;
            } else repathCooldown--;
            return;
        }

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
