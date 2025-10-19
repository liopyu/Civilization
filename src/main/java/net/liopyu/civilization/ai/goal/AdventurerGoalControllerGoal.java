package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public final class AdventurerGoalControllerGoal extends Goal {
    private final Adventurer adv;

    public AdventurerGoalControllerGoal(Adventurer adv) {
        this.adv = adv;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {
        adv.controller().tick();
    }
}
