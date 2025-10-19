package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;

public final class MineStoneGoal extends ModeScopedGoal {
    private BlockPos target;
    private int mineTicks;
    private int repathCooldown;
    private int searchCooldown;

    public MineStoneGoal(Adventurer a) {
        super(a, ActionMode.MINE_STONE);
    }

    @Override
    protected void onEnter(ActionMode mode) {
        target = null;
        mineTicks = 0;
        repathCooldown = 0;
        searchCooldown = 0;
    }

    @Override
    protected void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        target = null;
    }

    @Override
    public void tick() {
        if (target == null || !isMineableStone(adv.level(), target)) {
            if (searchCooldown <= 0) {
                target = findNearestStone(adv, 12);
                searchCooldown = 20;
            } else {
                searchCooldown--;
            }
            if (target == null) {
                if (adv.tickCount % 80 == 0) adv.controller().requestMode(ActionMode.EXPLORE, 200, 80, 20);
                return;
            }
        }

        double r = adv.entityInteractionRange() + 1.25;
        double cx = target.getX() + 0.5, cy = target.getY() + 0.5, cz = target.getZ() + 0.5;
        double d2 = adv.distanceToSqr(cx, cy, cz);

        if (d2 > r * r) {
            if (repathCooldown <= 0) {
                adv.getNavigation().moveTo(cx, cy, cz, 1.1);
                repathCooldown = 10;
            } else {
                repathCooldown--;
            }
            mineTicks = 0;
            return;
        }

        adv.getNavigation().stop();
        adv.getLookControl().setLookAt(cx, cy, cz);

        if (!isMineableStone(adv.level(), target)) {
            target = null;
            mineTicks = 0;
            return;
        }

        if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
        mineTicks++;

        if (mineTicks >= 34) {
            Level lvl = adv.level();
            BlockPos p = target;
            target = null;
            mineTicks = 0;
            BlockState st = lvl.getBlockState(p);
            if (isMineableStoneState(st)) {
                lvl.destroyBlock(p, true, adv);
                adv.controller().requestPickup(net.liopyu.civilization.ai.core.PickupRequest.any(adv.tickCount, 60, 8).withNear(p));
            }
        }
    }

    private static boolean isMineableStone(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        return isMineableStoneState(level.getBlockState(pos));
    }

    private static boolean isMineableStoneState(BlockState st) {
        if (!st.is(BlockTags.MINEABLE_WITH_PICKAXE)) return false;
        return st.is(Blocks.STONE)
                || st.is(Blocks.DEEPSLATE)
                || st.is(Blocks.COBBLESTONE)
                || st.is(Blocks.COBBLED_DEEPSLATE)
                || st.is(Blocks.ANDESITE)
                || st.is(Blocks.DIORITE)
                || st.is(Blocks.GRANITE)
                || st.is(Blocks.TUFF)
                || st.is(Blocks.CALCITE);
    }

    private static BlockPos findNearestStone(Adventurer a, int radius) {
        BlockPos origin = a.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        int r = Math.max(1, radius);
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (!a.isValidWorkPos(p)) continue;
                    if (!isMineableStone(a.level(), p)) continue;
                    double d2 = a.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }
}
