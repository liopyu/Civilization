package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.ai.core.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public abstract class ModeScopedGoal extends Goal {
    protected final Adventurer adv;
    private final ActionMode mode;

    protected ModeScopedGoal(Adventurer adv, ActionMode mode) {
        this.adv = adv;
        this.mode = mode;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return adv.getActionMode() == mode;
    }

    @Override
    public boolean canContinueToUse() {
        return adv.getActionMode() == mode;
    }

    @Override
    public void start() {
        onEnter(mode);
    }

    @Override
    public void stop() {
        onExit(mode);
    }

    protected void onEnter(ActionMode mode) {
    }

    protected void onExit(ActionMode mode) {
    }
}
