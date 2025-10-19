package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class EatGoal extends ModeScopedGoal {
    private int eatTicks;
    private ItemStack chosen;

    public EatGoal(Adventurer a) {
        super(a, ActionMode.EAT);
    }

    @Override
    protected void onEnter(ActionMode mode) {
        eatTicks = 0;
        chosen = ItemStack.EMPTY;
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        chosen = ItemStack.EMPTY;
        eatTicks = 0;
    }

    @Override
    public void tick() {
        if (adv.getHealth() >= adv.getMaxHealth() * 0.9f) {
            nextMode();
            return;
        }
        if (chosen.isEmpty()) {
            chosen = findFood();
            if (chosen.isEmpty()) {
                adv.controller().requestMode(ActionMode.EXPLORE, 300, 80, 0);
                return;
            }
            eatTicks = 20;
            adv.playSound(SoundEvents.PLAYER_BURP, 0.4f, 1.0f);
        }
        if (eatTicks > 0) {
            eatTicks--;
            if (eatTicks == 0) {
                consume(chosen);
                chosen = ItemStack.EMPTY;
            }
        }
    }

    private ItemStack findFood() {
        for (ItemStack s : adv.getInternalInventory())
            if (!s.isEmpty() && s.getFoodProperties(adv) != null) return s.copyWithCount(1);
        return ItemStack.EMPTY;
    }

    private void consume(ItemStack food) {
        FoodProperties f = food.getFoodProperties(adv);
        if (f != null) adv.heal(Math.max(1f, f.nutrition() * 0.5f));
        adv.consumeItems(food.getItem(), 1);
    }

    private void nextMode() {
        Level lvl = adv.level();
        if (!lvl.isDay() && adv.hasHome()) adv.controller().requestMode(ActionMode.RETURN_HOME, 400, 80, 20);
        else adv.controller().requestMode(ActionMode.EXPLORE, 300, 80, 0);
    }
}
