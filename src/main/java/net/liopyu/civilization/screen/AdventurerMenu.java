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

    private final Adventurer adventurer;      // null on client
    private final SimpleContainer container;  // backing container

    /**
     * Static client factory — EXACTLY what MenuType expects in 1.21.x
     */
    public static AdventurerMenu clientCtor(int id, Inventory playerInv) {
        // client gets an empty container; stacks sync from server
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

        // Adventurer 3x9 (27 slots)
        int x0 = 8, y0 = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, row * 9 + col, x0 + col * 18, y0 + row * 18));
            }
        }

        // Player inventory (3 rows)
        int py = y0 + 58;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, x0 + c * 18, py + r * 18));
            }
        }

        // Hotbar
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(playerInv, c, x0 + c * 18, py + 58));
        }
    }

    private static SimpleContainer serverContainerFrom(Adventurer adv) {
        SimpleContainer backing = new SimpleContainer(INV_SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                // push changes into the entity on SERVER
                NonNullList<ItemStack> snap = NonNullList.withSize(INV_SIZE, ItemStack.EMPTY);
                for (int i = 0; i < INV_SIZE; i++) snap.set(i, this.getItem(i).copy());
                adv.setInternalInventory(snap);
            }

            @Override
            public boolean stillValid(Player p) {
                return adv.isAlive() && p.distanceTo(adv) < 16;
            }
        };
        // seed from entity inv
        NonNullList<ItemStack> list = adv.getInternalInventory();
        for (int i = 0; i < INV_SIZE; i++) backing.setItem(i, list.get(i).copy());
        return backing;
    }

    @Override
    public boolean stillValid(Player player) {
        // On client, adventurer is null; vanilla distance checks occur server-side.
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
