package net.liopyu.civilization.ai.util;

import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InvHelper {
    private InvHelper() {
    }

    /**
     * Merge `toAdd` into the given list (no entityData writes).
     */
    public static void insertInto(NonNullList<ItemStack> inv, ItemStack toAdd) {
        if (toAdd.isEmpty()) return;
        ItemStack adding = toAdd.copy();

        for (int i = 0; i < inv.size() && !adding.isEmpty(); i++) {
            ItemStack slot = inv.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, adding)) {
                int move = Math.min(adding.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (move > 0) {
                    slot.grow(move); adding.shrink(move);
                }
            }
        }
        for (int i = 0; i < inv.size() && !adding.isEmpty(); i++) {
            if (inv.get(i).isEmpty()) {
                int move = Math.min(adding.getCount(), adding.getMaxStackSize());
                ItemStack placed = adding.copy();
                placed.setCount(move);
                inv.set(i, placed);
                adding.shrink(move);
            }
        }
    }

    /**
     * Convenience wrappers that defer to Adventurer’s inventory utilities.
     */
    public static int count(Adventurer mob, net.minecraft.world.item.Item item) {
        return mob.countItem(item);
    }

    public static boolean consume(Adventurer mob, net.minecraft.world.item.Item item, int n) {
        return mob.consumeItems(item, n);
    }

    /**
     * Small helper for stick crafting math (2 planks -> 4 sticks).
     */
    public static boolean craftSticks(Adventurer mob) {
        if (!consume(mob, Items.OAK_PLANKS, 2)) return false;
        mob.addItemToInternal(new ItemStack(Items.STICK, 4));
        return true;
    }
}
