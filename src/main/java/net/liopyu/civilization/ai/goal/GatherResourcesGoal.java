package net.liopyu.civilization.ai.goal;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.craft.TableHelper;
import net.liopyu.civilization.ai.mining.StairPlanner;
import net.liopyu.civilization.ai.trees.TreeMarker;
import net.liopyu.civilization.ai.util.BlockUtil;
import net.liopyu.civilization.ai.util.InvHelper;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.List;

import static net.liopyu.civilization.ai.util.BlockUtil.isLeaves;
import static net.liopyu.civilization.ai.util.BlockUtil.isLog;

public class GatherResourcesGoal extends Goal {
    private final Adventurer mob;
    private final double speed;
    private StairPlan activeStair;
    private long stairReplanCooldownUntil = 0L;
    private final java.util.ArrayDeque<BlockPos> backtrack = new java.util.ArrayDeque<>();
    private BlockPos prevPos = null; // last tick’s block pos
    private int lastY = Integer.MIN_VALUE;
    private Phase afterStairPhase = Phase.PLACE_TABLE;
    private static final double SURF_FLAT_RATIO = 0.6;
    private static final int SURF_RADIUS = 2;
    private long lastEscapeLogAt = 0L;

    private enum Phase {
        ENSURE_BASE, ENSURE_WOOD, PLACE_TABLE, CRAFT_WOOD_PICK,
        MINE_STONE_FOR_STONE_PICK, CRAFT_STONE_PICK, DROP_WOOD_PICK,
        MINE_BULK_STONE, CRAFT_TOOLSET, IDLE, ESCAPE_WITH_STAIRS
    }

    private boolean isSurfaceHere() {
        BlockPos p = mob.blockPosition();
        int y = p.getY();
        int h = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        if (Math.abs(y - h) <= 1) return true;
        int ok = 0, tot = 0, r = 2;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int hy = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX() + dx, p.getZ() + dz);
                if (Math.abs(hy - y) <= 1) ok++;
                tot++;
            }
        return (ok / (double) tot) >= 0.6;
    }

    private boolean isUndergroundHere() {
        if (mob.level().canSeeSky(mob.blockPosition().above())) return false;
        return !isSurfaceHere();
    }

    private boolean ensureReachableOrEscape(Phase returnPhase) {
        var nav = mob.getNavigation();
        if (isUndergroundHere() && (nav.getPath() == null || !nav.isInProgress())) {
            afterStairPhase = returnPhase;
            phase = Phase.ESCAPE_WITH_STAIRS;
            activeStair = null;
            stairReplanCooldownUntil = mob.level().getGameTime();
            return true;
        }
        return false;
    }


    private List<Step> planCanonUp(BlockPos start, BlockPos goal, int max) {
        java.util.ArrayList<Step> out = new java.util.ArrayList<>();
        int dx = Integer.compare(goal.getX(), start.getX());
        int dz = Integer.compare(goal.getZ(), start.getZ());
        if (dx == 0 && dz == 0) dx = 1;
        BlockPos cur = start;
        int steps = Math.max(0, goal.getY() - start.getY());
        for (int i = 0; i < steps && i < max; i++) {
            BlockPos next = cur.offset(dx, +1, dz);
            BlockPos standOn = next;
            BlockPos foot = standOn;
            BlockPos frontFoot = standOn.offset(dx, 0, dz);
            BlockPos frontHead = frontFoot.above();
            out.add(new Step(foot, frontFoot, frontHead, standOn));
            cur = next;
        }
        return out;
    }

    private List<Step> planCanonDown(BlockPos start, BlockPos goal, int max) {
        java.util.ArrayList<Step> out = new java.util.ArrayList<>();
        int dx = Integer.compare(goal.getX(), start.getX());
        int dz = Integer.compare(goal.getZ(), start.getZ());
        if (dx == 0 && dz == 0) dx = 1;
        BlockPos cur = start;
        int steps = Math.max(0, start.getY() - goal.getY());
        for (int i = 0; i < steps && i < max; i++) {
            BlockPos next = cur.offset(dx, -1, dz);
            BlockPos standOn = next;
            BlockPos foot = standOn;
            BlockPos frontFoot = standOn.offset(dx, 0, dz);
            BlockPos frontHead = frontFoot.above();
            out.add(new Step(foot, frontFoot, frontHead, standOn));
            cur = next;
        }
        return out;
    }

    static final class StairPlan {
        final BlockPos entry;
        final BlockPos target;
        final List<Step> steps;
        int cursor; // next step index to clear/move to

        StairPlan(BlockPos entry, BlockPos target, List<Step> steps) {
            this.entry = entry; this.target = target; this.steps = steps; this.cursor = 0;
        }

        boolean done() {
            return cursor >= steps.size();
        }
    }

    public static final class Step {
        final BlockPos foot, frontFoot, frontHead, standOn;

        public Step(BlockPos foot, BlockPos frontFoot, BlockPos frontHead, BlockPos standOn) {
            this.foot = foot; this.frontFoot = frontFoot; this.frontHead = frontHead; this.standOn = standOn;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    public void debug(String msg) {
        if (!mob.level().isClientSide) LOGGER.info("[Adventurer:{}] {}", mob.getId(), msg);
    }

    private final java.util.ArrayDeque<BlockPos> markedTree = new java.util.ArrayDeque<>();

    private static final double REACH = 4.5;
    private static final double REACH_SQ = REACH * REACH;

    public final Level level;
    private final TreeMarker trees = new TreeMarker();
    private final StairPlanner stairs;
    private final TableHelper tables;
    private Phase phase = Phase.ENSURE_BASE;
    private BlockPos workTarget, workStand;
    private BlockPos tablePos;
    private int mineTicks;

    private boolean isAirOrReplaceable(BlockPos p) {
        var st = level.getBlockState(p);
        return st.isAir() || st.canBeReplaced();
    }

    private boolean isSolid(BlockPos p) {
        return level.getBlockState(p).isSolid();
    }

    private void clearBlock(BlockPos p) {
        // Approach if out of reach
        if (mob.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) > REACH_SQ) {
            tryPathTo(p);
            return;
        }
        // Face and swing a few ticks, then destroy (like your tree/stone mining)
        mob.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (++mineTicks >= 8) {
            BlockState bs = level.getBlockState(p);
            level.destroyBlock(p, /*drop*/ false, mob); // clear space; don’t collect
            debug("Stair cleared " + p + " (" + bs.getBlock() + ")");
            mineTicks = 0;
        }
    }


    private boolean placeSupport(BlockPos p) {
        // Already OK?
        if (!level.isEmptyBlock(p) || !level.getBlockState(p.below()).isSolid()) return true;

        NonNullList<ItemStack> inv = mob.getInternalInventory();

        // prefer cobble, then dirt, then planks
        int idx = findPlaceable(inv, Blocks.COBBLESTONE);
        if (idx == -1) idx = findPlaceable(inv, Blocks.DIRT);
        if (idx == -1) idx = findPlaceable(inv, Blocks.OAK_PLANKS);
        if (idx == -1) return false;

        ItemStack s = inv.get(idx);
        Block block = ((BlockItem) s.getItem()).getBlock();

        // Must still be placeable
        if (!level.isEmptyBlock(p) || !level.getBlockState(p.below()).isSolid()) return false;

        boolean ok = level.setBlockAndUpdate(p, block.defaultBlockState());
        if (ok) {
            s.shrink(1);
            if (s.isEmpty()) inv.set(idx, ItemStack.EMPTY);
            mob.setInternalInventory(inv);
            debug("Placed support " + block + " at " + p);
            return true;
        }
        return false;
    }

    private int findPlaceable(NonNullList<ItemStack> inv, Block target) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof BlockItem bi && bi.getBlock() == target) return i;
        }
        return -1;
    }


    public GatherResourcesGoal(Adventurer mob, double speed) {
        this.mob = mob; this.speed = speed;
        this.stairs = new StairPlanner(mob, REACH);
        this.tables = new TableHelper(mob, this::debug);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        mob.setCanPickUpLoot(false);
        this.level = mob.level();
    }

    @Override
    public boolean canUse() {
        return !mob.level().isClientSide;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void start() {
        phase = mob.hasHome() ? Phase.ENSURE_WOOD : Phase.ENSURE_BASE;
    }

    public StuckMonitor stuckMon = new StuckMonitor();
    private long lastMoveOrderTime = 0L;
    private BlockPos lastMoveTarget = null;

    private long escapeCooldownUntil = 0L;

    @Override
    public void tick() {
        BlockPos now = mob.blockPosition();
        if (prevPos == null) {
            prevPos = now;
            lastY = now.getY();
        } else {
            // If we dropped by at least 1 block, remember where we came from (breadcrumb)
            if (now.getY() < lastY) {
                // store the block we just left as a “step” to climb back to
                backtrack.addLast(prevPos.immutable());
            }
            // Optionally: if we rose, we can pop the breadcrumb we just used
            if (now.getY() > lastY && !backtrack.isEmpty()) {
                // If we climbed to the last breadcrumb, consume it
                BlockPos tip = backtrack.peekLast();
                if (tip != null && now.closerThan(tip, 1.5)) {
                    backtrack.removeLast();
                }
            }
            prevPos = now;
            lastY = now.getY();
        }

        stuckMon.tick(mob, mob.getNavigation().isInProgress());
        if (shouldTriggerEscape(mob.level().getGameTime())) {
            if (mob.tickCount % 20 == 0) debug("Stuck detected → ESCAPE_WITH_STAIRS");
            phase = Phase.ESCAPE_WITH_STAIRS;
            escapeCooldownUntil = mob.level().getGameTime() + 40;
        }

        switch (phase) {
            case ENSURE_BASE -> doEnsureBase();
            case ENSURE_WOOD -> doEnsureWood();
            case PLACE_TABLE -> doPlaceTable();
            case CRAFT_WOOD_PICK -> doCraftWoodPick();
            case MINE_STONE_FOR_STONE_PICK -> doMineStone(3, Phase.CRAFT_STONE_PICK);
            case CRAFT_STONE_PICK -> doCraftStonePick();
            case DROP_WOOD_PICK -> doDropWoodPick();
            case MINE_BULK_STONE -> doMineStone(25, Phase.CRAFT_TOOLSET);
            case CRAFT_TOOLSET -> doCraftToolset();
            case ESCAPE_WITH_STAIRS -> doEscapeWithStairs();
            case IDLE -> {
            }
        }
    }


    private void randomNudge() {
        int radiusMin = 4, radiusMax = 6;
        BlockPos base = mob.blockPosition();

        for (int tries = 0; tries < 8; tries++) {
            int dx = mob.getRandom().nextInt(radiusMax - radiusMin + 1) + radiusMin;
            int dz = mob.getRandom().nextInt(radiusMax - radiusMin + 1) + radiusMin;
            if (mob.getRandom().nextBoolean()) dx = -dx;
            if (mob.getRandom().nextBoolean()) dz = -dz;

            BlockPos trialCol = base.offset(dx, 0, dz);
            BlockPos g = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, trialCol);

            // must be standable surface: empty at head/feet, solid below, no fluids
            if (!mob.level().isEmptyBlock(g)) continue;
            if (!mob.level().getBlockState(g.below()).isSolid()) continue;
            if (!mob.level().getFluidState(g).isEmpty() || !mob.level().getFluidState(g.below()).isEmpty()) continue;

            // Try to path there. moveTo() already flips into stairs if no path.
            debug("Random nudge to " + g);
            moveTo(g);
            return;
        }

        // If we couldn't find a good nudge spot, do a tiny upward bias if there's air above.
        BlockPos up = base.above();
        if (mob.level().isEmptyBlock(up)) {
            debug("Random nudge fallback: tiny upward move target " + up);
            moveTo(up);
        }
    }

    private void doEscapeWithStairs() {
        final long time = mob.level().getGameTime();
        if (!isUndergroundHere()) {
            activeStair = null; stuckMon.reset(); return;
        }

        if (!backtrack.isEmpty()) {
            BlockPos targetStep = backtrack.peekLast();
            if (targetStep != null) {
                BlockPos standOn = targetStep;
                BlockPos frontFoot = standOn.above();
                BlockPos frontHead = standOn.above(2);
                if (!isAirOrReplaceable(frontHead)) {
                    clearBlock(frontHead); return;
                }
                if (!isAirOrReplaceable(frontFoot)) {
                    clearBlock(frontFoot); return;
                }
                if (!isSolid(standOn)) {
                    if (!placeSupport(standOn)) return;
                }
                if (!mob.blockPosition().closerThan(standOn, 1.2)) {
                    tryPathTo(standOn); return;
                }
                backtrack.removeLast();
                stuckMon.reset();
                return;
            }
        }

        if ((activeStair == null || activeStair.done()) && time >= stairReplanCooldownUntil) {
            BlockPos start = mob.blockPosition();
            if (afterStairPhase == Phase.MINE_STONE_FOR_STONE_PICK || afterStairPhase == Phase.MINE_BULK_STONE) {
                BlockPos tgt = (workTarget != null) ? new BlockPos(start.getX() + Integer.compare(workTarget.getX(), start.getX()),
                        Math.min(workTarget.getY(), start.getY() - 16),
                        start.getZ() + Integer.compare(workTarget.getZ(), start.getZ()))
                        : start.below(8);
                List<Step> steps = planCanonDown(start, tgt, 64);
                if (steps.isEmpty()) {
                    stairReplanCooldownUntil = time + 10; return;
                }
                activeStair = new StairPlan(start, tgt, steps);
                stairReplanCooldownUntil = time + 10;
                debug("Stair plan (down): steps=" + steps.size() + " entry=" + start + " target=" + tgt);
            } else {
                BlockPos goal;
                if (tablePos != null) {
                    int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tablePos.getX(), tablePos.getZ());
                    goal = new BlockPos(tablePos.getX(), Math.max(start.getY() + 1, y), tablePos.getZ());
                } else {
                    int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ());
                    goal = new BlockPos(start.getX(), Math.max(start.getY() + 1, y), start.getZ());
                }
                List<Step> steps = planCanonUp(start, goal, 64);
                if (steps.isEmpty()) {
                    if (tryPillarUpOnce()) {
                        stairReplanCooldownUntil = time + 5; return;
                    }
                    randomNudge(); stairReplanCooldownUntil = time + 20; return;
                }
                activeStair = new StairPlan(start, goal, steps);
                stairReplanCooldownUntil = time + 10;
                debug("Stair plan (up): steps=" + steps.size() + " entry=" + start + " target=" + goal);
            }
        }

        if (activeStair == null) return;

        Step s = activeStair.steps.get(activeStair.cursor);
        if (!isAirOrReplaceable(s.frontHead)) {
            clearBlock(s.frontHead); return;
        }
        if (!isAirOrReplaceable(s.frontFoot)) {
            clearBlock(s.frontFoot); return;
        }
        if (!isAirOrReplaceable(s.foot)) {
            clearBlock(s.foot); return;
        }
        if (!isSolid(s.standOn)) {
            if (!placeSupport(s.standOn)) {
                stairReplanCooldownUntil = mob.level().getGameTime() + 10; return;
            }
        }
        if (!mob.blockPosition().closerThan(s.standOn, 1.2)) {
            tryPathTo(s.standOn); return;
        }

        activeStair.cursor++;
        if (activeStair.done()) {
            activeStair = null;
            stairReplanCooldownUntil = mob.level().getGameTime() + 20;
            stuckMon.reset();
            escapeCooldownUntil = mob.level().getGameTime() + 40;
            phase = afterStairPhase;
        }
    }


    private boolean tryPillarUpOnce() {
        BlockPos here = mob.blockPosition();
        BlockPos above = here.above();
        // Need headroom to climb
        if (!isAirOrReplaceable(above) || !isAirOrReplaceable(above.above())) return false;

        // Choose a side to place a step (try 4 cardinals)
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            BlockPos stepPos = here.offset(d[0], 0, d[1]);      // where the step will be
            BlockPos headPos = stepPos.above();                 // space we'll occupy after stepping up

            // We must be able to stand on stepPos: place block there; head must be free
            if (!isAirOrReplaceable(headPos)) continue;

            // Try to place the step block
            if (!isSolid(stepPos)) {
                // Prefer dirt, then cobble, then planks (craft if needed)
                if (!placeSupportPrefer(stepPos)) continue;
            }

            // Move onto the step
            tryPathTo(stepPos);
            // If we’re close enough, we can proceed next tick to continue rising
            return true;
        }
        return false;
    }

    private boolean placeSupportPrefer(BlockPos p) {
        // Already good?
        if (!mob.level().isEmptyBlock(p) || !mob.level().getBlockState(p.below()).isSolid()) return true;

        NonNullList<ItemStack> inv = mob.getInternalInventory();
        int idx = findPlaceable(inv, Blocks.DIRT);
        if (idx == -1) idx = findPlaceable(inv, Blocks.COBBLESTONE);
        if (idx == -1) {
            // try planks; craft 1x log if needed
            if (InvHelper.count(mob, Items.OAK_PLANKS) < 1) ensurePlanks(1);
            idx = findPlaceable(inv, Blocks.OAK_PLANKS);
        }
        if (idx == -1) return false;

        ItemStack s = inv.get(idx);
        Block block = ((BlockItem) s.getItem()).getBlock();

        boolean ok = mob.level().setBlockAndUpdate(p, block.defaultBlockState());
        if (ok) {
            s.shrink(1);
            if (s.isEmpty()) inv.set(idx, ItemStack.EMPTY);
            mob.setInternalInventory(inv);
            debug("Placed support " + block + " at " + p + " (prefer)");
            return true;
        }
        return false;
    }

    private void moveTo(BlockPos pos) {
        var nav = mob.getNavigation();
        lastMoveOrderTime = mob.level().getGameTime();
        lastMoveTarget = pos;
        boolean pathOk = nav.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
        if (!pathOk || nav.getPath() == null) {
            long now = mob.level().getGameTime();
            if (now - lastEscapeLogAt >= 20) {
                debug("No path to " + pos + " — switching to ESCAPE_WITH_STAIRS"); lastEscapeLogAt = now;
            }
            if (isUndergroundHere()) {
                afterStairPhase = Phase.PLACE_TABLE;
                phase = Phase.ESCAPE_WITH_STAIRS;
                activeStair = null;
                stairReplanCooldownUntil = now;
                escapeCooldownUntil = now + 20;
            }
        }
    }

    private void tryPathTo(BlockPos p) {
        var nav = mob.getNavigation();
        if (!nav.isInProgress()) {
            lastMoveOrderTime = mob.level().getGameTime();
            lastMoveTarget = p;
            boolean pathOk = nav.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.0);
            if (!pathOk || nav.getPath() == null) {
                long now = mob.level().getGameTime();
                if (now - lastEscapeLogAt >= 20) {
                    debug("No path to " + p + " — switching to ESCAPE_WITH_STAIRS"); lastEscapeLogAt = now;
                }
                if (isUndergroundHere()) {
                    afterStairPhase = Phase.PLACE_TABLE;
                    phase = Phase.ESCAPE_WITH_STAIRS;
                    activeStair = null;
                    stairReplanCooldownUntil = now;
                    escapeCooldownUntil = now + 20;
                }
            }
        }
    }

    private void doEnsureBase() {
        if (mob.hasHome()) {
            phase = Phase.ENSURE_WOOD; return;
        }
        BlockPos p = findFlatSpot(8);
        if (p != null) moveTo(p);
    }

    private void markTreeAt(BlockPos start) {
        markedTree.clear();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(start);
        seen.add(start);

        int logs = 0, leaves = 0;
        final int MAX_TREE_NODES = 192;

        while (!q.isEmpty() && markedTree.size() < MAX_TREE_NODES) {
            BlockPos p = q.removeFirst();
            BlockState bs = mob.level().getBlockState(p);
            if (!(isLog(bs) || isLeaves(bs))) continue;

            markedTree.add(p.immutable());
            if (isLog(bs)) logs++;
            else leaves++;

            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        BlockState bs2 = mob.level().getBlockState(n);
                        if (!seen.contains(n) && (isLog(bs2) || isLeaves(bs2))) {
                            seen.add(n);
                            q.add(n);
                        }
                    }
        }
        debug("Marked tree nodes logs=" + logs + " leaves=" + leaves + " total=" + markedTree.size() + " from " + start);
    }

    private void doEnsureWood() {
        if (hasAnyTool()) {
            debug("Has tools; proceed to PLACE_TABLE");
            phase = Phase.PLACE_TABLE;
            return;
        }

        // If the table flow needs planks, make sure we *have* planks before leaving wood phase.
        if (tables.needsWoodForPortable() || InvHelper.count(mob, Items.OAK_PLANKS) < 4) {
            if (ensurePlanks(4)) {
                debug("Crafted planks for portable table; proceed to PLACE_TABLE");
                phase = Phase.PLACE_TABLE;
            }
            // else continue harvesting wood below (don’t early-return to PLACE_TABLE)
        } else if (countLogsInternalInv() >= 2) {
            debug("Has >=2 logs; proceed to PLACE_TABLE");
            phase = Phase.PLACE_TABLE;
            return;
        }

        if (countLogsInternalInv() >= 2) {
            debug("Has >=2 logs; proceed to PLACE_TABLE");
            phase = Phase.PLACE_TABLE;
            return;
        }

        if (trees.isEmpty()) {
            BlockPos found = findNearestLog(12);
            if (found == null) {
                BlockPos roam = findFlatSpot(8);
                if (roam != null) {
                    debug("No logs; roaming to " + roam);
                    moveTo(roam);
                }
                return;
            }
            debug("Found log at " + found + " — marking tree");
            trees.mark(mob.level(), found);
        }

        var snapshot = trees.snapshot();
        BlockPos breakNow = null;
        BlockPos lowestLog = null;
        BlockPos lowestAny = null;

        for (BlockPos p : snapshot) {
            if (lowestAny == null || p.getY() < lowestAny.getY()) lowestAny = p;

            double d2 = mob.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (d2 <= REACH_SQ) {
                if (isLog(mob.level().getBlockState(p))) {
                    if (lowestLog == null || p.getY() < lowestLog.getY()) lowestLog = p;
                } else if (breakNow == null) {
                    breakNow = p;
                }
            }
        }
        if (lowestLog != null) breakNow = lowestLog;

        if (breakNow == null && lowestAny != null) {
            BlockPos stand = groundStandNear(lowestAny);
            if (stand == null) {
                debug("No valid ground near tree (lowest=" + lowestAny + "); abandoning this tree and searching another");
                trees.clear();
                return;
            }
            if (!mob.blockPosition().closerThan(stand, 1.8)) {
                debug("Pathing to stand near tree: " + stand);
                moveTo(stand);
                return;
            } else {
                debug("Standing near tree at " + stand + " but nothing reachable yet (rechecking)");
            }
        }

        if (breakNow != null) {
            if (++mineTicks > 8) {
                BlockState st = mob.level().getBlockState(breakNow);
                if (st.is(BlockTags.LOGS)) {
                    mob.level().destroyBlock(breakNow, false, mob);
                    mob.addItemToInternal(new ItemStack(st.getBlock().asItem(), 1));
                    debug("Chopped log at " + breakNow);
                } else if (st.is(BlockTags.LEAVES)) {
                    mob.level().destroyBlock(breakNow, false, mob);
                    debug("Cleared leaves at " + breakNow);
                }
                trees.remove(breakNow);
                mineTicks = 0;
            } else {
                mob.getLookControl().setLookAt(breakNow.getX() + 0.5, breakNow.getY() + 0.5, breakNow.getZ() + 0.5);
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }

        if (trees.isEmpty()) {
            int have = countLogsInternalInv();
            debug("Tree cleared. Logs now=" + have);
            if (have >= 2) {
                phase = Phase.PLACE_TABLE;
            } else {
                BlockPos next = findNearestLog(12);
                if (next != null) {
                    debug("Not enough logs; marking next tree at " + next);
                    trees.mark(mob.level(), next);
                } else {
                    BlockPos roam = findFlatSpot(8);
                    if (roam != null) {
                        debug("No more trees; roaming to " + roam);
                        moveTo(roam);
                    }
                }
            }
        }
    }


    private boolean isTreeCleared() {
        for (BlockPos p : trees.all()) return false;
        return true;
    }

    private void doMineStone(int needed, Phase next) {
        if (!hasAnyPickaxe()) {
            phase = Phase.CRAFT_WOOD_PICK; return;
        }
        if (InvHelper.count(mob, Items.COBBLESTONE) >= needed) {
            stairs.reset(); phase = next; return;
        }

        if (stairs.hasPlan() && stairs.advance(this::debug)) return;

        if (!stairs.hasPlan() && workTarget != null) {
            BlockPos end = stairs.lastStep();
            if (end != null && !mob.blockPosition().closerThan(end, 1.8)) {
                workStand = end;
                debug("Moving to end of ramp " + end + " before mining target " + workTarget);
                moveTo(end);
                return;
            }
        }

        if (workTarget == null || !BlockUtil.isMineableStone(mob.level().getBlockState(workTarget))) {
            workTarget = findNearestStone(15);
            if (workTarget == null) {
                if (stairs.planGeneric(this::debug)) stairs.advance(this::debug);
                return;
            }
            boolean buried = !BlockUtil.isAirish(mob.level().getBlockState(workTarget.above()));
            workStand = standNearBlock(workTarget);
            if (buried || workStand == null) {
                afterStairPhase = Phase.MINE_STONE_FOR_STONE_PICK;
                phase = Phase.ESCAPE_WITH_STAIRS;
                activeStair = null;
                stairReplanCooldownUntil = mob.level().getGameTime();
                return;
            }
            moveTo(workStand);
            mineTicks = 0;
            return;
        }

        double d2 = mob.distanceToSqr(workTarget.getX() + 0.5, workTarget.getY() + 0.5, workTarget.getZ() + 0.5);
        if (d2 <= REACH_SQ && BlockUtil.canSeeBlock(mob, workTarget)) {
            if (++mineTicks > 10) {
                mob.level().destroyBlock(workTarget, false, mob);
                mob.addItemToInternal(new ItemStack(Items.COBBLESTONE, 1));
                workTarget = null; workStand = null; mineTicks = 0;
            } else {
                mob.getLookControl().setLookAt(workTarget.getX() + 0.5, workTarget.getY() + 0.5, workTarget.getZ() + 0.5);
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        } else {
            if (workStand == null) workStand = standNearBlock(workTarget);
            if (workStand != null) moveTo(workStand);
        }
    }


    private void doPlaceTable() {
        if (InvHelper.count(mob, Items.OAK_PLANKS) < 4) ensurePlanks(4);

        boolean ready = tables.ensureNearForCrafting();
        if (ready) {
            this.tablePos = tables.tablePos();
            debug("At/near crafting table " + this.tablePos + " → CRAFT_WOOD_PICK");
            phase = Phase.CRAFT_WOOD_PICK;
            return;
        }

        if (tables.needsWoodForPortable()) {
            if (InvHelper.count(mob, Items.OAK_PLANKS) >= 4 && tables.ensureNearForCrafting()) {
                this.tablePos = tables.tablePos();
                debug("At/near crafting table " + this.tablePos + " → CRAFT_WOOD_PICK");
                phase = Phase.CRAFT_WOOD_PICK;
                return;
            }
            debug("TableHelper needs planks for portable table → ENSURE_WOOD");
            phase = Phase.ENSURE_WOOD;
            return;
        }

        if (ensureReachableOrEscape(Phase.PLACE_TABLE)) return;

        debug("Waiting for crafting table (helper in progress)...");
        if (InvHelper.count(mob, Items.OAK_PLANKS) < 4 && countLogsInternalInv() < 1) {
            debug("No planks/logs to place a table; returning to ENSURE_WOOD.");
            phase = Phase.ENSURE_WOOD;
        }
    }

    private void doCraftWoodPick() {
        if (InvHelper.count(mob, Items.WOODEN_PICKAXE) > 0 || InvHelper.count(mob, Items.STONE_PICKAXE) > 0) {
            phase = Phase.MINE_STONE_FOR_STONE_PICK;
            return;
        }
        if (!tables.ensureNearForCrafting()) {
            if (tables.needsWoodForPortable()) {
                phase = Phase.ENSURE_WOOD; return;
            }
            if (ensureReachableOrEscape(Phase.PLACE_TABLE)) return;
            return;
        }
        if (InvHelper.count(mob, Items.STICK) < 2) {
            if (InvHelper.count(mob, Items.OAK_PLANKS) < 5 && !ensurePlanks(5)) {
                phase = Phase.ENSURE_WOOD; return;
            }
            if (!InvHelper.craftSticks(mob)) {
                phase = Phase.ENSURE_WOOD; return;
            }
        }
        if (InvHelper.count(mob, Items.OAK_PLANKS) < 3 && !ensurePlanks(3)) {
            phase = Phase.ENSURE_WOOD; return;
        }

        if (InvHelper.consume(mob, Items.OAK_PLANKS, 3) && InvHelper.consume(mob, Items.STICK, 2)) {
            mob.addItemToInternal(new ItemStack(Items.WOODEN_PICKAXE));
            phase = Phase.MINE_STONE_FOR_STONE_PICK;
        } else {
            phase = Phase.ENSURE_WOOD;
        }
    }


    private void doCraftStonePick() {
        if (!tables.ensureNearForCrafting()) {
            if (ensureReachableOrEscape(Phase.PLACE_TABLE)) return;
            debug("Waiting for crafting table (helper in progress)...");
            return;
        }

        if (InvHelper.count(mob, Items.COBBLESTONE) < 3) {
            phase = Phase.MINE_STONE_FOR_STONE_PICK; return;
        }
        if (InvHelper.count(mob, Items.STICK) < 2 && !InvHelper.craftSticks(mob)) return;

        if (InvHelper.consume(mob, Items.COBBLESTONE, 3) && InvHelper.consume(mob, Items.STICK, 2)) {
            mob.addItemToInternal(new ItemStack(Items.STONE_PICKAXE));
            phase = Phase.DROP_WOOD_PICK;
        }
    }


    private void doDropWoodPick() {
        NonNullList<ItemStack> inv = mob.getInternalInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s.is(Items.WOODEN_PICKAXE)) {
                ItemStack drop = s.copy(); inv.set(i, ItemStack.EMPTY); mob.setInternalInventory(inv);
                mob.spawnAtLocation(drop); break;
            }
        }
        phase = Phase.MINE_BULK_STONE;
    }

    private void doCraftToolset() {
        if (InvHelper.count(mob, Items.STICK) < 10) {
            ensurePlanks(2); InvHelper.craftSticks(mob);
        }
        craftIfMissing(Items.STONE_AXE, 3, 2);
        craftIfMissing(Items.STONE_SWORD, 2, 1);
        craftIfMissing(Items.STONE_SHOVEL, 1, 2);
        craftIfMissing(Items.STONE_HOE, 2, 2);
        tables.pickupIfFar();
        phase = Phase.IDLE;
    }

    private boolean hasPickaxe(net.minecraft.world.item.Item i) {
        return InvHelper.count(mob, i) > 0;
    }

    private boolean has(net.minecraft.world.item.Item i) {
        return InvHelper.count(mob, i) > 0;
    }

    private boolean hasAnyPickaxe() {
        return InvHelper.count(mob, Items.WOODEN_PICKAXE) > 0 ||
                InvHelper.count(mob, Items.STONE_PICKAXE) > 0;
    }

    private boolean hasAnyTool() {
        return InvHelper.count(mob, Items.WOODEN_PICKAXE) > 0 ||
                InvHelper.count(mob, Items.STONE_PICKAXE) > 0 ||
                InvHelper.count(mob, Items.STONE_AXE) > 0 ||
                InvHelper.count(mob, Items.STONE_SWORD) > 0 ||
                InvHelper.count(mob, Items.STONE_SHOVEL) > 0 ||
                InvHelper.count(mob, Items.STONE_HOE) > 0;
    }


    private int countLogsInternalInv() {
        int c = 0;
        for (ItemStack s : mob.getInternalInventory()) if (isLogItem(s)) c += s.getCount();
        return c;
    }

    private static boolean isLogItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof BlockItem bi) {
            Block block = bi.getBlock(); Holder<Block> h = block.builtInRegistryHolder();
            return h.is(BlockTags.LOGS);
        }
        return false;
    }

    private boolean ensurePlanks(int min) {
        int planks = InvHelper.count(mob, Items.OAK_PLANKS);
        if (planks >= min) return true;
        NonNullList<ItemStack> inv = mob.getInternalInventory();
        for (int i = 0; i < inv.size() && planks < min; i++) {
            ItemStack s = inv.get(i);
            if (isLogItem(s)) {
                s.shrink(1);
                if (s.isEmpty()) inv.set(i, ItemStack.EMPTY);
                InvHelper.insertInto(inv, new ItemStack(Items.OAK_PLANKS, 4));
                planks += 4;
            }
        }
        mob.setInternalInventory(inv);
        return InvHelper.count(mob, Items.OAK_PLANKS) >= min;
    }

    private BlockPos standNearBlock(BlockPos target) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int dy = -1; dy <= 1; dy++)
            for (int[] d : dirs) {
                BlockPos feet = target.offset(d[0], dy, d[1]);
                if (!mob.level().isLoaded(feet)) continue;
                if (!mob.level().isEmptyBlock(feet)) continue;
                if (!mob.level().getBlockState(feet.below()).isSolid()) continue;
                return feet.immutable();
            }
        BlockPos g = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
        if (mob.level().isEmptyBlock(g) && mob.level().getBlockState(g.below()).isSolid()) return g.immutable();
        return null;
    }

    private BlockPos groundStandNear(BlockPos target) {
        int radius = 5;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                BlockPos col = target.offset(dx, 0, dz);
                BlockPos g = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
                if (!mob.level().isEmptyBlock(g)) continue;
                if (!mob.level().getBlockState(g.below()).isSolid()) continue;
                double d = g.distSqr(target);
                if (d < bestD) {
                    bestD = d; best = g.immutable();
                }
            }
        }
        return best;
    }

    private BlockPos findNearestLog(int r) {
        BlockPos base = mob.blockPosition();
        BlockPos best = null; double bestD = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++)
            for (int dy = -3; dy <= 3; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState st = mob.level().getBlockState(p);
                    if (st.is(BlockTags.LOGS)) {
                        double d = p.distSqr(base);
                        if (d < bestD) {
                            bestD = d; best = p.immutable();
                        }
                    }
                }
        return best;
    }

    private BlockPos findNearestStone(int r) {
        BlockPos base = mob.blockPosition(); BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (BlockUtil.isMineableStone(mob.level().getBlockState(p))) {
                        double d = p.distSqr(base); if (d < bestD) {
                            bestD = d; best = p.immutable();
                        }
                    }
                }
        return best;
    }

    private BlockPos findFlatSpot(int r) {
        BlockPos base = mob.blockPosition();
        for (int i = 0; i < 40; i++) {
            int dx = mob.getRandom().nextInt(r * 2 + 1) - r;
            int dz = mob.getRandom().nextInt(r * 2 + 1) - r;
            BlockPos g = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.offset(dx, 0, dz));
            if (mob.level().isEmptyBlock(g) && mob.level().getBlockState(g.below()).isSolid()) return g.immutable();
        }
        return null;
    }

    private void craftIfMissing(net.minecraft.world.item.Item tool, int head, int sticks) {
        if (mob.countItem(tool) > 0) return;
        if (InvHelper.count(mob, Items.COBBLESTONE) < head) return;
        if (InvHelper.count(mob, Items.STICK) < sticks) {
            ensurePlanks(2); InvHelper.craftSticks(mob);
        }
        if (InvHelper.consume(mob, Items.COBBLESTONE, head) && InvHelper.consume(mob, Items.STICK, sticks)) {
            mob.addItemToInternal(new ItemStack(tool));
        }
    }

    private boolean shouldTriggerEscape(long now) {
        if (phase == Phase.ESCAPE_WITH_STAIRS) return false;
        if (now < escapeCooldownUntil) return false;
        boolean recentlyIssuedMove = (now - lastMoveOrderTime) <= 40;
        if (!recentlyIssuedMove) return false;
        var nav = mob.getNavigation();
        boolean pathDead = (!nav.isInProgress() || nav.getPath() == null);
        boolean working =
                (phase == Phase.CRAFT_WOOD_PICK) ||
                        (phase == Phase.CRAFT_STONE_PICK) ||
                        (phase == Phase.CRAFT_TOOLSET) ||
                        (phase == Phase.ENSURE_WOOD && mineTicks > 0) ||
                        (phase == Phase.MINE_STONE_FOR_STONE_PICK && workTarget != null &&
                                mob.distanceToSqr(workTarget.getX() + 0.5, workTarget.getY() + 0.5, workTarget.getZ() + 0.5) <= REACH_SQ);
        if (working) return false;
        if (!isUndergroundHere()) return false;
        return stuckMon.veryStuck() && pathDead;
    }


    public static final class StuckMonitor {
        private BlockPos last;
        private int stillTicks;
        private int noPathTicks;

        void tick(Mob mob, boolean navInProgress) {
            BlockPos now = mob.blockPosition();
            // moved more than ~0.5 blocks? reset
            if (last == null || !now.closerThan(last, 0.5)) {
                last = now;
                stillTicks = 0;
            } else {
                stillTicks++;
            }
            if (!navInProgress) noPathTicks++;
            else noPathTicks = 0;
        }

        // Be stricter: require BOTH standing still for ~5s AND nav failing for ~2s
        boolean veryStuck() {
            return stillTicks > 100 && noPathTicks > 40;
        }

        void reset() {
            stillTicks = 0; noPathTicks = 0;
        }
    }

}
