package net.liopyu.civilization.ai.brain;

import com.mojang.datafixers.util.Pair;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.liopyu.civilization.entity.Adventurer;

import java.util.List;
import java.util.Set;

public final class AdventurerAi {
    public static List<MemoryModuleType<?>> memoryTypes() {
        return java.util.List.of(
                AdventurerMemories.NEAREST_TREE.get(),
                AdventurerMemories.MINING_TARGET.get(),
                AdventurerMemories.TUNNELING.get(),
                AdventurerMemories.MODE_ACTIVITY.get()
        );
    }

    public static final List<SensorType<? extends Sensor<? super Adventurer>>> SENSOR_TYPES = List.of();

    public static void makeBrain(Adventurer a, Brain<Adventurer> b) {
        // CORE: keep only always-on utility like auto-equip
        b.addActivity(
                Activity.CORE,
                0,
                ImmutableList.of(
                        net.liopyu.civilization.ai.brain.behavior.AutoEquipBehavior.create()
                )
        );

        // IDLE: selector that decides what to do next and sets ActionMode
        b.addActivity(
                Activity.IDLE,
                10,
                ImmutableList.of(
                        net.liopyu.civilization.ai.brain.ManageActionsBehavior.create(24, 6.0)
                )
        );

        // WORK: actual work behaviors, gated by MODE_ACTIVITY
        b.addActivityWithConditions(
                Activity.WORK,
                ImmutableList.of(
                        Pair.of(9, net.liopyu.civilization.ai.brain.NavigateToTargetPosBehavior.create(1.1)),
                        Pair.of(10, net.liopyu.civilization.ai.brain.NavigateToNearestTreeBehavior.create(1.1)),
                        Pair.of(11, net.liopyu.civilization.ai.brain.CutTreeBehavior.create(256)),
                        Pair.of(12, net.liopyu.civilization.ai.brain.MineBlockBehavior.create())
                ),
                Set.of(
                        Pair.of(
                                net.liopyu.civilization.ai.brain.AdventurerMemories.MODE_ACTIVITY.get(),
                                net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT
                        )
                )
        );

        b.setCoreActivities(Set.of(Activity.CORE));
        b.setDefaultActivity(Activity.IDLE);
        b.useDefaultActivity();
    }


    public static void updateActivity(Adventurer a) {
        var brain = a.getBrain();
        var mode = a.getActionMode();

        boolean working =
                mode == net.liopyu.civilization.ai.ActionMode.NAVIGATING_TO_NEAREST_TREE ||
                        mode == net.liopyu.civilization.ai.ActionMode.CUTTING_TREE ||
                        mode == net.liopyu.civilization.ai.ActionMode.MINING_PICKAXABLE ||
                        mode == net.liopyu.civilization.ai.ActionMode.MINING_ORES ||
                        mode == net.liopyu.civilization.ai.ActionMode.MINING_SHOVELABLE;

        if (working) {
            brain.setMemory(net.liopyu.civilization.ai.brain.AdventurerMemories.MODE_ACTIVITY.get(), mode);
            brain.setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.WORK);
        } else {
            brain.eraseMemory(net.liopyu.civilization.ai.brain.AdventurerMemories.MODE_ACTIVITY.get());
            brain.setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.IDLE);
        }
    }


    private AdventurerAi() {
    }
}
