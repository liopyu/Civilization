package net.liopyu.civilization.ai.craft;

import net.liopyu.civilization.ai.util.InvHelper;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

public class TableHelper {
    private final Adventurer mob;
    private final Consumer<String> dbg;
    private boolean needsWoodForPortable = false;

    public boolean needsWoodForPortable() {
        return needsWoodForPortable;
    }

    private BlockPos tablePos;
    private boolean portablePlaced = false;

    private int stuckTicks = 0;
    private double lastDistSq = Double.MAX_VALUE;
    private static final int STUCK_TICKS_MAX = 40;
    private static final double USE_RADIUS = 3.5;
    private static final double HOME_MAX_DIST = 30.0;

    public TableHelper(Adventurer mob, Consumer<String> dbg) {
        this.mob = mob;
        this.dbg = dbg;
    }

    public BlockPos tablePos() {
        return tablePos;
    }

    /**
     * Pick up a previously placed temporary table if we wandered away.
     */
    public void pickupIfFar() {
        if (!portablePlaced || tablePos == null) return;
        if (!mob.blockPosition().closerThan(tablePos, 5.0)) {
            if (mob.level().getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
                mob.level().removeBlock(tablePos, false);
                mob.addItemToInternal(new net.minecraft.world.item.ItemStack(Items.CRAFTING_TABLE));
                dbg.accept("Picked up portable crafting table from " + tablePos);
            }
            portablePlaced = false;
            tablePos = null;
        }
    }

    /**
     * Ensure we are near a usable crafting table; walk to it, or place a portable one if stuck.
     */
    public boolean ensureNearForCrafting() {
        BlockPos nearby = findNearbyCraftingTable(6);
        if (nearby != null) {
            setTarget(nearby, false);
            return walkOrDone(nearby);
        }

        if (mob.hasHomeTable()) {
            BlockPos ht = mob.getHomeTablePos().get();
            if (mob.blockPosition().closerThan(ht, HOME_MAX_DIST)) {
                setTarget(ht, false);
                if (walkOrDone(ht)) return true;
                if (isStuck(ht)) {
                    dbg.accept("Stuck trying to reach home table at " + ht + " — placing portable");
                    return placePortableAndWalk();
                }
                return false;
            } else {
                dbg.accept("Home table > " + (int) HOME_MAX_DIST + " blocks away; using portable");
            }
        }

        return placePortableAndWalk();
    }


    private void setTarget(BlockPos pos, boolean portable) {
        this.tablePos = pos;
        this.portablePlaced = portable;
        this.needsWoodForPortable = false;
    }


    private boolean walkOrDone(BlockPos target) {
        if (mob.blockPosition().closerThan(target, USE_RADIUS)) {
            stuckTicks = 0;
            lastDistSq = Double.MAX_VALUE;
            dbg.accept("At/near crafting table " + target + " → CRAFT_WOOD_PICK");
            return true;
        }

        double d2 = mob.blockPosition().distSqr(target);
        if (d2 < lastDistSq - 0.01) {
            stuckTicks = 0;
            lastDistSq = d2;
        } else {
            stuckTicks++;
        }

        if (mob.getNavigation().isDone()) {
            dbg.accept("Heading to crafting table at " + target);
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0D);
        }
        return false;
    }

    private boolean isStuck(BlockPos target) {
        return stuckTicks > STUCK_TICKS_MAX && !mob.blockPosition().closerThan(target, USE_RADIUS);
    }

    private boolean placePortableAndWalk() {
        BlockPos spot = findSolidAdjacentAir(mob.blockPosition(), 2);
        if (spot == null) {
            spot = mob.level().getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    mob.blockPosition()
            );
            if (!isStandable(spot)) spot = null;
        }
        if (spot == null) {
            dbg.accept("No safe spot to place portable table");
            return false;
        }

        if (!mob.level().getBlockState(spot).is(Blocks.CRAFTING_TABLE)) {
            if (InvHelper.count(mob, Items.OAK_PLANKS) < 4) {
                needsWoodForPortable = true;
                dbg.accept("Not enough planks to place portable table");
                return false;
            }
            if (!InvHelper.consume(mob, Items.OAK_PLANKS, 4)) {
                needsWoodForPortable = true;
                dbg.accept("Failed to consume planks for portable table");
                return false;
            }
            mob.level().setBlock(spot, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
            dbg.accept("Placed portable crafting table at " + spot);
        }

        setTarget(spot, true);
        return walkOrDone(spot);
    }


    private BlockPos findNearbyCraftingTable(int radius) {
        BlockPos base = mob.blockPosition();
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (mob.level().getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
                        double d = p.distSqr(base);
                        if (d < bestD) {
                            bestD = d; best = p.immutable();
                        }
                    }
                }
        return best;
    }

    private BlockPos findSolidAdjacentAir(BlockPos origin, int radius) {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos g = mob.level().getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        origin.offset(dx, 0, dz)
                );
                if (isStandable(g)) return g.immutable();
            }
        return null;
    }

    private boolean isStandable(BlockPos g) {
        return mob.level().isEmptyBlock(g) && mob.level().getBlockState(g.below()).isSolid();
    }
}
