package net.liopyu.civilization.ai.mining;

import net.liopyu.civilization.ai.goal.GatherResourcesGoal;
import net.liopyu.civilization.ai.util.BlockUtil;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class StairPlanner {
    private final Adventurer mob;
    private final double reach;

    private final ArrayDeque<BlockPos> plan = new ArrayDeque<>();
    private BlockPos entry = null;
    private BlockPos target = null;
    private BlockPos lastStep = null;

    private static final int MAX_DEPTH = 32;
    private static final int SEARCH_RADIUS = 6;

    private int mineTicks = 0;

    public StairPlanner(Adventurer mob, double reach) {
        this.mob = mob;
        this.reach = reach;
    }

    public static BlockPos findNearestSurface(Level level, BlockPos start, int radius, int maxRise) {
        // Try straight up until we see sky or light > 12, capped by maxRise.
        BlockPos p = start;
        for (int i = 0; i < maxRise; i++) {
            BlockPos head = p.above(1);
            if (level.canSeeSky(head) || level.getMaxLocalRawBrightness(head) >= 12) {
                return new BlockPos(p.getX(), p.getY(), p.getZ());
            }
            p = p.above();
        }
        // Fallback: use heightmap at start’s XZ; clamp to maxRise.
        int surfY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ());
        surfY = Math.min(surfY, start.getY() + maxRise);
        return new BlockPos(start.getX(), surfY, start.getZ());
    }

    public static List<GatherResourcesGoal.Step> planUpStair(Level level, BlockPos start, BlockPos target) {
        List<GatherResourcesGoal.Step> out = new ArrayList<>();
        BlockPos cur = start;
        int dy = target.getY() - start.getY();
        int steps = Math.max(0, dy);

        // Simple straight-line in X/Z toward target, rising 1 per step.
        int sx = Integer.compare(target.getX(), start.getX());
        int sz = Integer.compare(target.getZ(), start.getZ());
        if (sx == 0 && sz == 0) sx = 1; // pick a direction if vertical

        for (int i = 0; i < steps; i++) {
            BlockPos next = cur.offset(sx, +1, sz);
            BlockPos standOn = cur.offset(sx, 0, sz);

            BlockPos foot = next;                     // the block at feet after step-up
            BlockPos frontFoot = next.offset(sx, 0, sz);
            BlockPos frontHead = next.offset(sx, +1, sz);

            out.add(new GatherResourcesGoal.Step(foot, frontFoot, frontHead, standOn));
            cur = next;
            if (out.size() > 64) break; // hard cap
        }
        return out;
    }

    public void reset() {
        plan.clear();
        entry = null;
        target = null;
        lastStep = null;
        mineTicks = 0;
    }

    public boolean hasPlan() {
        return !plan.isEmpty();
    }

    /**
     * Optional, useful if the caller wants to stand on the end of the ramp after it completes.
     */
    public BlockPos lastStep() {
        return lastStep;
    }

    /**
     * Returns true while it is actively digging/advancing this tick. False means plan is finished or absent.
     */
    public boolean advance(Consumer<String> log) {
        if (plan.isEmpty()) return false;

        BlockPos step = plan.peekFirst();
        BlockPos head = (plan.size() >= 2) ? (BlockPos) plan.toArray()[1] : step.above();

        int dx = 0, dz = 0;
        if (plan.size() >= 4) {
            BlockPos nextStep = (BlockPos) plan.toArray()[2];
            dx = Integer.signum(nextStep.getX() - step.getX());
            dz = Integer.signum(nextStep.getZ() - step.getZ());
        } else if (target != null) {
            dx = Integer.signum(target.getX() - step.getX());
            dz = Integer.signum(target.getZ() - step.getZ());
        }
        if (dx == 0 && dz == 0) {
            int[] dirs = pickStableFacing(step);
            dx = dirs[0]; dz = dirs[1];
        }
        BlockPos frontFoot = step.offset(dx, 0, dz);
        BlockPos frontHead = frontFoot.above();

        if (!mob.blockPosition().closerThan(step, 1.8)) {
            moveTo(step);
            return true;
        }

        BlockState sStep = mob.level().getBlockState(step);
        if (!BlockUtil.isAirish(sStep)) {
            if (++mineTicks > 6) {
                mob.level().destroyBlock(step, false, mob);
                mineTicks = 0;
                if (log != null) log.accept("Stair cleared foot " + step + " (" + sStep.getBlock() + ")");
            } else {
                lookAndSwing(step);
            }
            return true;
        }

        BlockState sHead = mob.level().getBlockState(head);
        if (!BlockUtil.isAirish(sHead)) {
            if (++mineTicks > 6) {
                mob.level().destroyBlock(head, false, mob);
                mineTicks = 0;
                if (log != null) log.accept("Stair cleared headroom " + head + " (" + sHead.getBlock() + ")");
            } else {
                lookAndSwing(head);
            }
            return true;
        }

        BlockState sFF = mob.level().getBlockState(frontFoot);
        if (!BlockUtil.isAirish(sFF)) {
            if (++mineTicks > 6) {
                mob.level().destroyBlock(frontFoot, false, mob);
                mineTicks = 0;
                if (log != null) log.accept("Stair cleared front-foot " + frontFoot + " (" + sFF.getBlock() + ")");
            } else {
                lookAndSwing(frontFoot);
            }
            return true;
        }

        BlockState sFH = mob.level().getBlockState(frontHead);
        if (!BlockUtil.isAirish(sFH)) {
            if (++mineTicks > 6) {
                mob.level().destroyBlock(frontHead, false, mob);
                mineTicks = 0;
                if (log != null) log.accept("Stair cleared front-head " + frontHead + " (" + sFH.getBlock() + ")");
            } else {
                lookAndSwing(frontHead);
            }
            return true;
        }

        lastStep = step;
        plan.removeFirst();
        if (!plan.isEmpty()) plan.removeFirst();

        return !plan.isEmpty();
    }

    /**
     * Plan a proper ramp from surface down toward target so the mob ends adjacent with LoS.
     */
    public boolean planFromSurfaceTo(BlockPos target, Consumer<String> log) {
        reset();
        this.target = target;

        this.entry = surfaceEntryAbove(target);
        if (entry == null) {
            if (log != null) log.accept("Stair plan: no surface entry above " + target);
            return false;
        }

        int[] rampDir = chooseRampDir(entry, target);
        int rx = rampDir[0], rz = rampDir[1];

        int depth = Math.max(0, entry.getY() - target.getY());
        if (depth == 0) depth = 1;

        BlockPos cur = entry;
        int safety = Math.min(depth + 8, MAX_DEPTH);

        int steps = Math.min(depth, MAX_DEPTH);
        for (int i = 0; i < steps; i++) {
            cur = new BlockPos(cur.getX() + rx, cur.getY() - 1, cur.getZ() + rz);
            addStepPair(cur);
        }

        BlockPos last = cur;
        int lateralGuard = 8;
        while (lateralGuard-- > 0 && !isAdjacent(last, target)) {
            int lx = Integer.signum(target.getX() - last.getX());
            int lz = Integer.signum(target.getZ() - last.getZ());
            if (lx == 0 && lz == 0) break;
            last = new BlockPos(last.getX() + (lx == 0 ? rx : lx), last.getY(), last.getZ() + (lz == 0 ? rz : lz));
            addStepPair(last);
        }

        if (plan.isEmpty()) {
            if (log != null) log.accept("Stair plan from surface empty for target " + target);
            return false;
        }
        if (log != null)
            log.accept("Stair plan (surface->target): steps=" + plan.size() + " entry=" + entry + " target=" + target);
        return true;
    }

    /**
     * Generic “go down somewhere safe” ramp (used when no visible stone).
     */
    public boolean planGeneric(Consumer<String> log) {
        reset();
        this.entry = pickGenericAnchor();
        if (entry == null) {
            if (log != null) log.accept("Generic stair: no anchor");
            return false;
        }

        int[] dir = pickStableFacing(entry);
        int rx = dir[0], rz = dir[1];
        if (rx == 0 && rz == 0) {
            rx = 1; rz = 0;
        }

        BlockPos cur = entry;
        for (int i = 0; i < Math.min(MAX_DEPTH, 12); i++) {
            cur = new BlockPos(cur.getX() + rx, cur.getY() - 1, cur.getZ() + rz);
            addStepPair(cur);
            BlockPos face = new BlockPos(cur.getX() + rx, cur.getY(), cur.getZ() + rz);
            if (mob.level().isLoaded(face) && BlockUtil.isMineableStone(mob.level().getBlockState(face))) break;
        }

        if (plan.isEmpty()) return false;
        if (log != null) log.accept("Generic stair plan: steps=" + plan.size() + " entry=" + entry);
        return true;
    }


    private void addStepPair(BlockPos step) {
        plan.addLast(step);
        plan.addLast(step.above());
    }

    private void moveTo(BlockPos pos) {
        mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0D);
    }

    private void lookAndSwing(BlockPos p) {
        mob.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private BlockPos surfaceEntryAbove(BlockPos t) {
        BlockPos top = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, t);
        for (int y = top.getY(); y >= t.getY(); y--) {
            BlockPos p = new BlockPos(t.getX(), y, t.getZ());
            if (mob.level().isEmptyBlock(p) && mob.level().getBlockState(p.below()).isSolid()) {
                return p.immutable();
            }
        }
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                if (dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS) continue;
                BlockPos col = top.offset(dx, 0, dz);
                BlockPos g = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
                if (mob.level().isEmptyBlock(g) && mob.level().getBlockState(g.below()).isSolid()) return g.immutable();
            }
        }
        return null;
    }

    private BlockPos pickGenericAnchor() {
        BlockPos start = mob.blockPosition();
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                if (dx * dx + dz * dz > SEARCH_RADIUS * SEARCH_RADIUS) continue;
                BlockPos g = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.offset(dx, 0, dz));
                if (!mob.level().isEmptyBlock(g)) continue;
                if (!mob.level().getBlockState(g.below()).isSolid()) continue;
                double d = g.distSqr(start);
                if (d < bestD) {
                    bestD = d; best = g.immutable();
                }
            }
        }
        return best;
    }

    /**
     * Choose ramp dir. If target shares XZ with entry (dx=dz=0), pick a stable lateral facing.
     */
    private int[] chooseRampDir(BlockPos entry, BlockPos target) {
        int dx = Integer.signum(target.getX() - entry.getX());
        int dz = Integer.signum(target.getZ() - entry.getZ());
        if (dx == 0 && dz == 0) return pickStableFacing(entry);
        return new int[]{dx, dz};
    }

    /**
     * Returns a cardinal facing (±X or ±Z) that has empty foot/head and solid below from a given foot pos.
     */
    private int[] pickStableFacing(BlockPos foot) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            BlockPos ff = foot.offset(d[0], 0, d[1]);
            BlockPos fh = ff.above();
            if (!mob.level().isLoaded(ff) || !mob.level().isLoaded(fh)) continue;
            if (!BlockUtil.isAirish(mob.level().getBlockState(ff))) continue;
            if (!BlockUtil.isAirish(mob.level().getBlockState(fh))) continue;
            if (!mob.level().getBlockState(ff.below()).isSolid()) continue;
            return d;
        }
        return new int[]{1, 0};
    }

    private boolean isAdjacent(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return dy == 0 && dx + dz == 1;
    }
}
