package net.liopyu.civilization.ai.brain.behavior;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.function.Predicate;

public class AutoEquipBehavior extends Behavior<Adventurer> {
    public static AutoEquipBehavior create() {
        return new AutoEquipBehavior(Map.of(), 10, 20);
    }

    private AutoEquipBehavior(Map<MemoryModuleType<?>, net.minecraft.world.entity.ai.memory.MemoryStatus> req, int min, int max) {
        super(req, min, max);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Adventurer mob) {
        return !level.isClientSide();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Adventurer mob, long time) {
        return true;
    }

    @Override
    protected void tick(ServerLevel level, Adventurer mob, long gameTime) {
        var mode = mob.getActionMode();
        Predicate<ItemStack> want = wantedTool(mode);
        if (want != null) {
            ensureBest(mob, toolSampleFor(mode), want);
        } else {
            ensureBestAny(mob);
        }
    }

    private Predicate<ItemStack> wantedTool(ActionMode mode) {
        if (mode == ActionMode.CUTTING_TREE) return this::isAxe;
        if (mode == ActionMode.NAVIGATING_TO_NEAREST_TREE) return this::isAxe;
        if (mode == ActionMode.MINING_PICKAXABLE) return this::isPickaxe;
        if (mode == ActionMode.MINING_ORES) return this::isPickaxe;
        if (mode == ActionMode.MINING_SHOVELABLE) return this::isShovel;
        return null;
    }

    private BlockState toolSampleFor(ActionMode mode) {
        if (mode == ActionMode.CUTTING_TREE || mode == ActionMode.NAVIGATING_TO_NEAREST_TREE)
            return Blocks.OAK_LOG.defaultBlockState();
        if (mode == ActionMode.MINING_SHOVELABLE) return Blocks.DIRT.defaultBlockState();
        if (mode == ActionMode.MINING_ORES) return Blocks.IRON_ORE.defaultBlockState();
        if (mode == ActionMode.MINING_PICKAXABLE) return Blocks.STONE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    private void ensureBest(Adventurer mob, BlockState sample, Predicate<ItemStack> accept) {
        ItemStack hand = mob.getMainHandItem();
        float bestScore = (accept.test(hand) ? scoreTool(mob, sample, hand) : -1f);
        int bestIdx = -1;
        var inv = mob.getInternalInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (!accept.test(s)) continue;
            float sc = scoreTool(mob, sample, s);
            if (sc > bestScore) {
                bestScore = sc; bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            ItemStack take = inv.get(bestIdx);
            inv.set(bestIdx, hand.copy());
            mob.setInternalInventory(inv);
            mob.setItemSlot(EquipmentSlot.MAINHAND, take.copy());
        }
    }

    private void ensureBestAny(Adventurer mob) {
        ItemStack hand = mob.getMainHandItem();
        float curPick = isPickaxe(hand) ? scoreTool(mob, Blocks.STONE.defaultBlockState(), hand) : -1f;
        float curAxe = isAxe(hand) ? scoreTool(mob, Blocks.OAK_LOG.defaultBlockState(), hand) : -1f;
        float curShovel = isShovel(hand) ? scoreTool(mob, Blocks.DIRT.defaultBlockState(), hand) : -1f;
        float bestScore = Math.max(curPick, Math.max(curAxe, curShovel));
        int bestIdx = -1;
        var inv = mob.getInternalInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (!(isPickaxe(s) || isAxe(s) || isShovel(s))) continue;
            float sp = isPickaxe(s) ? scoreTool(mob, Blocks.STONE.defaultBlockState(), s) : -1f;
            float sa = isAxe(s) ? scoreTool(mob, Blocks.OAK_LOG.defaultBlockState(), s) : -1f;
            float ss = isShovel(s) ? scoreTool(mob, Blocks.DIRT.defaultBlockState(), s) : -1f;
            float sc = Math.max(sp, Math.max(sa, ss));
            if (sc > bestScore) {
                bestScore = sc; bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            ItemStack take = inv.get(bestIdx);
            inv.set(bestIdx, hand.copy());
            mob.setInternalInventory(inv);
            mob.setItemSlot(EquipmentSlot.MAINHAND, take.copy());
        }
    }

    private float scoreTool(Adventurer mob, BlockState sample, ItemStack tool) {
        float base = tool.getDestroySpeed(sample);
        int eff = getEnchantLevel(mob, tool, Enchantments.EFFICIENCY);
        float bonus = eff > 0 ? (eff * eff + 1) : 0f;
        return base + bonus;
    }

    private int getEnchantLevel(Adventurer mob, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key) {
        var lookup = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder = lookup.getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    private boolean isAxe(ItemStack s) {
        return s != null && !s.isEmpty() && (s.is(Items.WOODEN_AXE) || s.is(Items.STONE_AXE) || s.is(Items.IRON_AXE) || s.is(Items.GOLDEN_AXE) || s.is(Items.DIAMOND_AXE) || s.is(Items.NETHERITE_AXE));
    }

    private boolean isPickaxe(ItemStack s) {
        return s != null && !s.isEmpty() && (s.is(Items.WOODEN_PICKAXE) || s.is(Items.STONE_PICKAXE) || s.is(Items.IRON_PICKAXE) || s.is(Items.GOLDEN_PICKAXE) || s.is(Items.DIAMOND_PICKAXE) || s.is(Items.NETHERITE_PICKAXE));
    }

    private boolean isShovel(ItemStack s) {
        return s != null && !s.isEmpty() && (s.is(Items.WOODEN_SHOVEL) || s.is(Items.STONE_SHOVEL) || s.is(Items.IRON_SHOVEL) || s.is(Items.GOLDEN_SHOVEL) || s.is(Items.DIAMOND_SHOVEL) || s.is(Items.NETHERITE_SHOVEL));
    }
}
