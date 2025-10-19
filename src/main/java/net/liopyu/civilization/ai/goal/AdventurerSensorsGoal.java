package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.ai.core.AdventurerKeys;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

public final class AdventurerSensorsGoal extends Goal {
    private final Adventurer adv;
    private boolean flip;

    public AdventurerSensorsGoal(Adventurer adv) {
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
        adv.controller().set(AdventurerKeys.HAS_HOME, adv.hasHome());
        adv.controller().set(AdventurerKeys.WOOD_COUNT, adv.countItem(Items.OAK_LOG) + adv.countItem(Items.SPRUCE_LOG) + adv.countItem(Items.BIRCH_LOG) + adv.countItem(Items.JUNGLE_LOG) + adv.countItem(Items.ACACIA_LOG) + adv.countItem(Items.DARK_OAK_LOG) + adv.countItem(Items.MANGROVE_LOG) + adv.countItem(Items.CHERRY_LOG) + adv.countItem(Items.CRIMSON_STEM) + adv.countItem(Items.WARPED_STEM));
        adv.controller().set(AdventurerKeys.STONE_COUNT, adv.countItem(Items.COBBLESTONE) + adv.countItem(Items.COBBLED_DEEPSLATE));
        adv.controller().set(AdventurerKeys.HUNGRY, adv.getHealth() < adv.getMaxHealth() * 0.5f);
        adv.controller().set(AdventurerKeys.NIGHT, !adv.level().isDay());
        adv.controller().set(AdventurerKeys.UNDER_ATTACK, adv.getLastHurtByMob() != null);
        flip = !flip;
        adv.controller().set(AdventurerKeys.PULSE, flip);
    }
}
