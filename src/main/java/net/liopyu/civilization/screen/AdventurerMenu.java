package net.liopyu.civilization.screen;

import net.liopyu.civilization.entity.Adventurer;
import net.liopyu.civilization.registry.ModMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AdventurerMenu extends AbstractContainerMenu {
    public static final int INV_SIZE = 27;

    private final Adventurer adventurer;
    private final SimpleContainer container;

    /**
     * Static client factory — EXACTLY what MenuType expects in 1.21.x
     */
    public static AdventurerMenu clientCtor(int id, Inventory playerInv) {
        return new AdventurerMenu(id, playerInv, null, new SimpleContainer(INV_SIZE));
    }

    /**
     * Server-side ctor used when you open the menu (with the entity reference)
     */
    public AdventurerMenu(int id, Inventory playerInv, Adventurer adv) {
        this(id, playerInv, adv, serverContainerFrom(adv));
    }

    /**
     * Internal ctor shared by client/server
     */
    private AdventurerMenu(int id, Inventory playerInv, Adventurer adv, SimpleContainer backing) {
        super(ModMenus.ADVENTURER_MENU.get(), id);
        this.adventurer = adv;
        this.container = backing;

        int rows = INV_SIZE / 9;
        int x0 = 8;
        int chestY = 18;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, row * 9 + col, x0 + col * 18, chestY + row * 18));
            }
        }

        int i = (rows - 4) * 18;
        int invY = 103 + i;

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, x0 + c * 18, invY + r * 18));
            }
        }

        int hotbarY = invY + 58;
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(playerInv, c, x0 + c * 18, hotbarY));
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.adventurer != null) {
            NonNullList<ItemStack> latest = this.adventurer.getInternalInventory();
            int chestSlots = INV_SIZE;
            for (int i = 0; i < chestSlots; i++) {
                Slot slot = this.slots.get(i);
                ItemStack want = latest.get(i);
                ItemStack cur = slot.getItem();
                boolean same =
                        ItemStack.isSameItemSameComponents(cur, want) &&
                                cur.getCount() == want.getCount();
                if (!same) {
                    slot.set(want.copy());
                }
            }
        }
        super.broadcastChanges();
    }

    private static SimpleContainer serverContainerFrom(Adventurer adv) {
        SimpleContainer backing = new SimpleContainer(INV_SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                NonNullList<ItemStack> snap = NonNullList.withSize(INV_SIZE, ItemStack.EMPTY);
                for (int i = 0; i < INV_SIZE; i++) snap.set(i, this.getItem(i).copy());
                adv.setInternalInventory(snap);
            }

            @Override
            public boolean stillValid(Player p) {
                return adv.isAlive() && p.distanceTo(adv) < 16;
            }
        };
        NonNullList<ItemStack> list = adv.getInternalInventory();
        for (int i = 0; i < INV_SIZE; i++) backing.setItem(i, list.get(i).copy());
        return backing;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            int advEnd = INV_SIZE;
            if (index < advEnd) {
                if (!this.moveItemStackTo(stack, advEnd, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, 0, advEnd, false)) return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }
}
