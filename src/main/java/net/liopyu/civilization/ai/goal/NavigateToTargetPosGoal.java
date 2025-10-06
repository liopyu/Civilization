package net.liopyu.civilization.ai.goal;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

import static net.liopyu.civilization.ai.util.AiUtil.aiLogger;
import static net.liopyu.civilization.ai.util.AiUtil.findStandNear;

public class NavigateToTargetPosGoal extends Goal {
    private final Adventurer mob;
    private final double speed;
    private final double stopRadius;
    private BlockPos currentStand;
    private int repathCooldown;
    private double lastDist2;
    private int stuckTicks;
    private MineBlockGoal miner;
    private java.util.ArrayDeque<BlockPos> tunnelQueue = new java.util.ArrayDeque<>();
    private boolean tunneling;
    private int reoptCooldown;
    private int maxTunnelBlocksPerPlan = 96;
    private int tunnelLookahead = 8;
    private int lingerTicks;
    private int lingerMax = 8;
    private int proximityCheckCooldown;
    private BlockPos lastTreeAnchor;


    public NavigateToTargetPosGoal(Adventurer mob, double speed) {
        this(mob, speed, 1.6);
    }

    public NavigateToTargetPosGoal(Adventurer mob, double speed, double stopRadius) {
        this.mob = mob;
        this.speed = speed;
        this.stopRadius = Math.max(0.5, stopRadius);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        boolean hasWork = (mob.getActionMode() == net.liopyu.civilization.ai.ActionMode.CUTTING_TREE && ensureMiner() && !miner.isIdle()) || tunneling;
        BlockPos target = mob.getMiningTargetPos();
        if (target == null) return hasWork;
        double r = mob.entityInteractionRange();
        double cx = target.getX() + 0.5, cy = target.getY() + 0.5, cz = target.getZ() + 0.5;
        double d2 = mob.distanceToSqr(cx, cy, cz);
        boolean outOfReach = d2 > (r * r);
        boolean blocked = !hasLineOfSightTo(target);
        return outOfReach || blocked || hasWork;
    }

    @Override
    public boolean canContinueToUse() {
        boolean hasWork = (mob.getActionMode() == net.liopyu.civilization.ai.ActionMode.CUTTING_TREE && ensureMiner() && !miner.isIdle()) || tunneling;
        BlockPos target = mob.getMiningTargetPos();
        if (target == null) return hasWork || lingerTicks > 0;
        double r = mob.entityInteractionRange();
        double cx = target.getX() + 0.5, cy = target.getY() + 0.5, cz = target.getZ() + 0.5;
        double d2 = mob.distanceToSqr(cx, cy, cz);
        boolean outOfReach = d2 > (r * r);
        boolean blocked = !hasLineOfSightTo(target);
        return outOfReach || blocked || hasWork || lingerTicks > 0;
    }

    @Override
    public void tick() {
        BlockPos target = mob.getMiningTargetPos();
        if (target == null) {
            if (lingerTicks > 0) lingerTicks--; return;
        }

        double cx = target.getX() + 0.5, cy = target.getY() + 0.5, cz = target.getZ() + 0.5;
        double d2 = mob.distanceToSqr(cx, cy, cz);
        double r = mob.entityInteractionRange();
        boolean outOfReach = d2 > (r * r);
        boolean blocked = !hasLineOfSightTo(target);

        if (!outOfReach) {
            if (blocked) {
                java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> pred =
                        net.liopyu.civilization.ai.util.AiUtil.obstaclePredicateForMode(mob);
                BlockPos obstacle = net.liopyu.civilization.ai.util.AiUtil.firstBlockingAlong(mob, target, pred);
                if (obstacle != null && ensureMiner()) {
                    miner.enqueueFront(obstacle);
                    mob.setMiningTargetPos(obstacle);
                    lingerTicks = lingerMax;
                    return;
                }
                BlockPos vantage = findVantageStand(target, 4);
                if (vantage != null) {
                    if (--repathCooldown <= 0 || currentStand == null || !currentStand.equals(vantage) || mob.getNavigation().isDone()) {
                        currentStand = vantage.immutable();
                        mob.getNavigation().moveTo(vantage.getX() + 0.5, vantage.getY(), vantage.getZ() + 0.5, speed);
                        repathCooldown = 10;
                    }
                    return;
                }
            }
            mob.getNavigation().stop();
            lingerTicks = lingerMax;
            if (--reoptCooldown <= 0) {
                if (planTunnelIfBetter(target)) {
                    tunneling = true; repathCooldown = 0;
                }
                reoptCooldown = 20;
            }
            if (lingerTicks > 0) lingerTicks--;
            return;
        }

        if (tunneling) {
            while (!tunnelQueue.isEmpty()) {
                BlockPos peek = tunnelQueue.peekFirst();
                if (!mob.level().isLoaded(peek) || mob.level().getBlockState(peek).isAir()) {
                    tunnelQueue.removeFirst(); continue;
                }
                if (ensureMiner()) {
                    miner.enqueueFront(peek); mob.setMiningTargetPos(peek);
                }
                double nx = peek.getX() + 0.5, ny = peek.getY() + 0.5, nz = peek.getZ() + 0.5;
                double nd2 = mob.distanceToSqr(nx, ny, nz);
                boolean nOut = nd2 > (r * r);
                if (nOut) {
                    BlockPos stand = findStandNearObstacle(peek);
                    double sx = stand.getX() + 0.5, sy = stand.getY(), sz = stand.getZ() + 0.5;
                    if (--repathCooldown <= 0 || currentStand == null || !currentStand.equals(stand) || mob.getNavigation().isDone()) {
                        currentStand = stand.immutable();
                        mob.getNavigation().moveTo(sx, sy, sz, speed);
                        repathCooldown = 10;
                    }
                    if (nd2 > lastDist2 - 0.01) {
                        if (++stuckTicks > 60) {
                            tryRepath(); stuckTicks = 0;
                        }
                    } else {
                        stuckTicks = 0;
                    }
                    lastDist2 = nd2;
                    return;
                } else {
                    tunnelQueue.removeFirst();
                    repathCooldown = 3;
                    lingerTicks = lingerMax;
                    return;
                }
            }
            if (tunnelQueue.isEmpty()) {
                tunneling = false; repathCooldown = 0;
            }
        }

        if (--reoptCooldown <= 0) {
            if (planTunnelIfBetter(target)) {
                tunneling = true; repathCooldown = 0; return;
            }
            reoptCooldown = 20;
        }

        if (d2 > lastDist2 - 0.01) {
            if (++stuckTicks > 60) {
                tryRepath(); stuckTicks = 0;
            }
        } else {
            stuckTicks = 0;
        }
        lastDist2 = d2;

        if (--repathCooldown <= 0 || currentStand == null || !currentStand.closerThan(target, 4.5)) tryRepath();
        if (lingerTicks > 0) lingerTicks--;
    }

    private boolean hasLineOfSightTo(BlockPos pos) {
        net.minecraft.world.phys.Vec3 start = mob.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob);
        net.minecraft.world.phys.HitResult hit = mob.level().clip(ctx);
        if (hit == null) return true;
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
            return ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos().equals(pos);
        return true;
    }

    private BlockPos findVantageStand(BlockPos target, int maxR) {
        BlockPos best = null; double bestScore = Double.POSITIVE_INFINITY;
        for (int r = 1; r <= maxR; r++) {
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos feet = target.offset(dx, 0, dz);
                    if (!mob.level().isLoaded(feet)) continue;
                    if (!mob.level().getBlockState(feet).isAir()) continue;
                    if (!mob.level().getBlockState(feet.below()).isSolid()) continue;
                    if (!hasLineOfSightFrom(feet, target)) continue;
                    double score = mob.distanceToSqr(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
                    if (score < bestScore) {
                        bestScore = score; best = feet.immutable();
                    }
                }
            if (best != null) break;
        }
        return best;
    }

    private boolean hasLineOfSightFrom(BlockPos from, BlockPos to) {
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(from.getX() + 0.5, from.getY() + mob.getEyeHeight(), from.getZ() + 0.5);
        net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob);
        net.minecraft.world.phys.HitResult hit = mob.level().clip(ctx);
        if (hit == null) return true;
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
            return ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos().equals(to);
        return true;
    }


    private boolean treeStillExistsAround(BlockPos center, int r) {
        int x0 = center.getX(), y0 = center.getY(), z0 = center.getZ();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 8; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
                    if (!mob.level().isLoaded(p)) continue;
                    if (mob.level().getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)) return true;
                }
            }
        }
        return false;
    }

    private BlockPos pickCloserLogTarget(BlockPos currentTarget, int r) {
        BlockPos base = currentTarget;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (!mob.level().isLoaded(p)) continue;
                    if (!mob.level().getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)) continue;
                    double d = mob.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d < bestD) {
                        bestD = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean planTunnelIfBetter(BlockPos target) {
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> pred =
                net.liopyu.civilization.ai.util.AiUtil.obstaclePredicateForMode(mob);

        int[][] offsets = new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        java.util.List<BlockPos> best = null;
        int bestCost = Integer.MAX_VALUE;

        for (int[] o : offsets) {
            BlockPos t2 = target.offset(o[0], 0, o[1]);
            java.util.List<BlockPos> obs = collectLineObstaclesBresenham(mob, t2, pred, maxTunnelBlocksPerPlan);
            if (obs.isEmpty()) continue;
            int cost = 0;
            int limit = Math.min(obs.size(), tunnelLookahead);
            for (int i = 0; i < obs.size(); i++) {
                BlockPos p = obs.get(i);
                net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(p);
                cost += breakCost(st, p);
                if (i >= limit && cost > bestCost) break;
                if (cost > 400) break;
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = obs;
            }
        }

        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().getPath();
        int pathLen = path != null ? path.getNodeCount() : 0;

        if (best == null) return false;

        if (path == null || pathLen == 0 || bestCost + 6 < pathLen * 3) {
            tunnelQueue.clear();
            for (BlockPos p : best) tunnelQueue.addLast(p.immutable());
            return true;
        }
        return false;
    }

    private java.util.List<BlockPos> collectLineObstaclesBresenham(Adventurer e, BlockPos target, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> pred, int max) {
        net.minecraft.world.phys.Vec3 startV = e.getEyePosition(1.0F);
        BlockPos start = new BlockPos(net.minecraft.util.Mth.floor(startV.x), net.minecraft.util.Mth.floor(startV.y), net.minecraft.util.Mth.floor(startV.z));
        int x1 = start.getX(), y1 = start.getY(), z1 = start.getZ();
        int x2 = target.getX(), y2 = target.getY(), z2 = target.getZ();

        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;

        int ax = dx << 1, ay = dy << 1, az = dz << 1;

        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();

        if (dx >= dy && dx >= dz) {
            int yd = ay - dx, zd = az - dx;
            while (x1 != x2 && out.size() < max) {
                if (!addIfObstacle(e, x1, y1, z1, pred, out, seen)) {
                }
                if (yd >= 0) {
                    y1 += sy; yd -= ax;
                }
                if (zd >= 0) {
                    z1 += sz; zd -= ax;
                }
                x1 += sx; yd += ay; zd += az;
            }
        } else if (dy >= dx && dy >= dz) {
            int xd = ax - dy, zd = az - dy;
            while (y1 != y2 && out.size() < max) {
                if (!addIfObstacle(e, x1, y1, z1, pred, out, seen)) {
                }
                if (xd >= 0) {
                    x1 += sx; xd -= ay;
                }
                if (zd >= 0) {
                    z1 += sz; zd -= ay;
                }
                y1 += sy; xd += ax; zd += az;
            }
        } else {
            int xd = ax - dz, yd = ay - dz;
            while (z1 != z2 && out.size() < max) {
                if (!addIfObstacle(e, x1, y1, z1, pred, out, seen)) {
                }
                if (xd >= 0) {
                    x1 += sx; xd -= az;
                }
                if (yd >= 0) {
                    y1 += sy; yd -= az;
                }
                z1 += sz; xd += ax; yd += ay;
            }
        }
        addIfObstacle(e, x2, y2, z2, pred, out, seen);
        return out;
    }

    private boolean addIfObstacle(Adventurer e, int x, int y, int z, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> pred, java.util.ArrayList<BlockPos> out, java.util.HashSet<BlockPos> seen) {
        BlockPos bp = new BlockPos(x, y, z);
        if (!seen.add(bp)) return false;
        if (!e.level().isLoaded(bp)) return false;
        net.minecraft.world.level.block.state.BlockState st = e.level().getBlockState(bp);
        if (!st.isAir() && pred.test(st)) {
            out.add(bp.immutable());
            return true;
        }
        return false;
    }

    private int breakCost(net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(mob.level(), pos);
        if (hardness < 0) return 9999;
        float speed = 1.0F;
        net.minecraft.world.item.ItemStack tool = mob.getMainHandItem();
        if (!tool.isEmpty()) {
            float s = tool.getDestroySpeed(state);
            if (s > 1.0F) speed = s;
        }
        if (speed <= 0.0F) speed = 0.5F;
        float t = Math.max(1.0F, 20.0F * hardness / speed);
        if (t > 200F) t = 200F;
        return (int) Math.ceil(t);
    }

    private java.util.List<BlockPos> collectLineObstacles(Adventurer e, BlockPos target, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> pred, int max) {
        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();
        net.minecraft.world.phys.Vec3 start = e.getEyePosition(1.0F);
        net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        net.minecraft.world.phys.Vec3 dir = end.subtract(start);
        double dist = dir.length();
        if (dist <= 0.0001) return out;
        dir = dir.scale(1.0 / dist);
        double step = 0.5;
        int steps = Math.min((int) Math.ceil(dist / step), 256);
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        for (int i = 0; i <= steps && out.size() < max; i++) {
            net.minecraft.world.phys.Vec3 p = start.add(dir.scale(i * step));
            BlockPos bp = new BlockPos(net.minecraft.util.Mth.floor(p.x), net.minecraft.util.Mth.floor(p.y), net.minecraft.util.Mth.floor(p.z));
            if (!seen.add(bp)) continue;
            if (!e.level().isLoaded(bp)) continue;
            net.minecraft.world.level.block.state.BlockState st = e.level().getBlockState(bp);
            if (!st.isAir() && pred.test(st)) out.add(bp.immutable());
        }
        return out;
    }


    private void tryRepath() {
        BlockPos target = mob.getMiningTargetPos();
        if (target == null) return;

        double r = mob.entityInteractionRange();
        double cx = target.getX() + 0.5;
        double cy = target.getY() + 0.5;
        double cz = target.getZ() + 0.5;
        double d2 = mob.distanceToSqr(cx, cy, cz);

        boolean outOfReach = d2 > (r * r);
        boolean aboveFeet = target.getY() >= mob.blockPosition().getY();

        BlockPos stand;
        if (outOfReach && aboveFeet) {
            BlockPos column = new BlockPos(target.getX(), mob.blockPosition().getY(), target.getZ());
            double hx = Math.abs(mob.getX() - (column.getX() + 0.5));
            double hz = Math.abs(mob.getZ() - (column.getZ() + 0.5));
            if (hx <= 0.6 && hz <= 0.6) {
                mob.getNavigation().stop();
                repathCooldown = 8;
                return;
            }
            stand = column;
        } else {
            BlockPos s = findStandNear(mob.level(), target, 3);
            stand = s == null ? target : s;
        }

        if (currentStand != null && currentStand.equals(stand) && !mob.getNavigation().isDone()) {
            repathCooldown = (outOfReach && aboveFeet) ? 8 : 20;
            return;
        }

        currentStand = stand.immutable();
        mob.getNavigation().moveTo(
                currentStand.getX() + 0.5,
                currentStand.getY(),
                currentStand.getZ() + 0.5,
                speed
        );
        repathCooldown = (outOfReach && aboveFeet) ? 8 : 20;
    }

    @Override
    public void start() {
        aiLogger("Start: NavigateToTargetPosGoal");
        miner = null;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof MineBlockGoal m) {
                miner = m;
                break;
            }
        }
        repathCooldown = 0;
        stuckTicks = 0;
        lastDist2 = Double.MAX_VALUE;
        currentStand = null;
        tunnelQueue.clear();
        tunneling = false;
        reoptCooldown = 0;
        lingerTicks = 0;
        proximityCheckCooldown = 0;
        lastTreeAnchor = mob.getMiningTargetPos();
    }


    @Override
    public void stop() {
        aiLogger("Stop: NavigateToTargetPosGoal");
        super.stop();
        tunnelQueue.clear();
        tunneling = false;
    }

    private BlockPos findStandNearObstacle(BlockPos obstacle) {
        BlockPos s = findStandNear(mob.level(), obstacle, 2);
        if (s != null) return s.immutable();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos feet = obstacle.offset(dx, 0, dz);
                if (!mob.level().isLoaded(feet)) continue;
                if (!mob.level().getBlockState(feet).isAir()) continue;
                if (!mob.level().getBlockState(feet.below()).isSolid()) continue;
                return feet.immutable();
            }
        }
        return obstacle.immutable();
    }

    private boolean ensureMiner() {
        if (miner != null) return true;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof MineBlockGoal m) {
                miner = m;
                break;
            }
        }
        return miner != null;
    }

}
