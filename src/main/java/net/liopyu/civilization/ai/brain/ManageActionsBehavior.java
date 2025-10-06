package net.liopyu.civilization.ai.brain;

import com.google.common.collect.ImmutableMap;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class ManageActionsBehavior extends Behavior<Adventurer> {
    private final int radius;
    private final double avoid;

    private ManageActionsBehavior(int r, double a) {
        super(ImmutableMap.<MemoryModuleType<?>, MemoryStatus>of(), 10, 10);
        this.radius = r;
        this.avoid = a;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Adventurer a, long time) {
        return true;
    }

    public static ManageActionsBehavior create(int r, double a) {
        return new ManageActionsBehavior(r, a);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Adventurer a) {
        return true;
    }

    @Override
    protected void tick(ServerLevel level, Adventurer a, long gameTime) {
        var mode = a.getActionMode();
        if (mode == ActionMode.IDLE) {
            BlockPos log = net.liopyu.civilization.ai.util.AiUtil.findNearestLogAvoidingOthers(a, radius, avoid);
            if (log != null) a.setActionMode(ActionMode.NAVIGATING_TO_NEAREST_TREE);
        } else if (mode == ActionMode.NAVIGATING_TO_NEAREST_TREE) {
            BlockPos log = net.liopyu.civilization.ai.util.AiUtil.findNearestLogAvoidingOthers(a, radius, avoid);
            if (log == null) a.setActionMode(ActionMode.IDLE);
        }
        net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
    }

}
