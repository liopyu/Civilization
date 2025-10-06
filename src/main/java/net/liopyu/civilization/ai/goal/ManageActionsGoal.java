package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.world.entity.ai.goal.Goal;

import static net.liopyu.civilization.ai.util.AiUtil.aiLogger;

public class ManageActionsGoal extends Goal {
    private final Adventurer mob;
    private int cooldown;

    public ManageActionsGoal(Adventurer adventurer) {
        this.mob = adventurer;
        setFlags(java.util.EnumSet.noneOf(Flag.class)); // <- no flags
    }

    @Override
    public boolean canUse() {
        return !mob.level().isClientSide;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void start() {
        aiLogger("Start: ManageActionsGoal");
        cooldown = 0;
    }

    @Override
    public void stop() {
        super.stop();
        aiLogger("Stop: ManageActionsGoal");
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--; return;
        }

        var mode = mob.getActionMode();

        if (mode == net.liopyu.civilization.ai.ActionMode.IDLE) {
            var log = net.liopyu.civilization.ai.util.AiUtil.findNearestLogAvoidingOthers(mob, 24, 6.0);
            if (log != null) {
                mob.setActionMode(net.liopyu.civilization.ai.ActionMode.NAVIGATING_TO_NEAREST_TREE);
                cooldown = 10;
                return;
            }
        }

        if (mode == net.liopyu.civilization.ai.ActionMode.NAVIGATING_TO_NEAREST_TREE) {
            var log = net.liopyu.civilization.ai.util.AiUtil.findNearestLogAvoidingOthers(mob, 24, 6.0);
            if (log == null) {
                mob.setActionMode(net.liopyu.civilization.ai.ActionMode.IDLE);
                cooldown = 10;
                return;
            }
        }

        if (mode == net.liopyu.civilization.ai.ActionMode.CUTTING_TREE) {
            cooldown = 5;
            return;
        }

        cooldown = 5;
    }

}
