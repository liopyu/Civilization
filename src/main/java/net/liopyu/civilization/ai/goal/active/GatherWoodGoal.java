package net.liopyu.civilization.ai.goal.active;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.goal.ModeScopedGoal;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

public final class GatherWoodGoal extends ModeScopedGoal {
    private BlockPos target;
    private int mineTicks;
    private int repathCooldown;
    private int searchCooldown;
    private java.util.Set<BlockPos> cluster;
    private int clusterCooldown;

    public GatherWoodGoal(Adventurer a) {
        super(a, ActionMode.GATHER_WOOD);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public void onEnter(ActionMode mode) {
        target = null;
        mineTicks = 0;
        repathCooldown = 0;
        searchCooldown = 0;
        clusterCooldown = 0;
        cluster = new java.util.HashSet<>();
    }

    @Override
    public void onExit(ActionMode mode) {
        adv.getNavigation().stop();
        target = null;
        mineTicks = 0;
        cluster = null;
    }

    @Override
    public void tick() {
        if ((target == null || !isLog(adv.level(), target))) {
            if ((cluster == null || cluster.isEmpty()) && searchCooldown <= 0) {
                BlockPos start = findNearestLog(adv, 12);
                searchCooldown = 20;
                if (start != null) {
                    buildCluster(adv.level(), start, 96, 6);
                    target = pickNextFromCluster();
                }
            } else if (cluster != null && !cluster.isEmpty()) {
                if (clusterCooldown <= 0) {
                    pruneCluster();
                    clusterCooldown = 10;
                } else clusterCooldown--;
                target = pickNextFromCluster();
            }
            if (target == null) {
                if (adv.tickCount % 60 == 0) adv.controller().requestMode(ActionMode.EXPLORE, 200, 80, 20);
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
            } else repathCooldown--;
            mineTicks = 0;
            return;
        }

        adv.getNavigation().stop();
        adv.getLookControl().setLookAt(cx, cy, cz);

        if (!isLog(adv.level(), target)) {
            if (cluster != null) cluster.remove(target);
            target = null;
            mineTicks = 0;
            return;
        }

        if (adv.tickCount % 6 == 0) adv.swing(InteractionHand.MAIN_HAND);
        mineTicks++;

        if (mineTicks >= 30) {
            Level lvl = adv.level();
            BlockPos p = target;
            mineTicks = 0;
            if (lvl.getBlockState(p).is(BlockTags.LOGS)) {
                lvl.destroyBlock(p, true, adv);
                if (cluster != null) cluster.remove(p);
                adv.controller().requestPickup(net.liopyu.civilization.ai.core.PickupRequest.any(adv.tickCount, 60, 8).withNear(p));
            }
            target = cluster != null && !cluster.isEmpty() ? pickNextFromCluster() : null;
            if (target == null) searchCooldown = 0;
        }
    }

    private void buildCluster(Level lvl, BlockPos start, int max, int radius) {
        cluster.clear();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        q.add(start.immutable());
        visited.add(start.immutable());
        BlockPos s = start.immutable();
        while (!q.isEmpty() && cluster.size() < max) {
            BlockPos p = q.removeFirst();
            if (!lvl.isLoaded(p)) continue;
            if (p.distManhattan(s) > radius * 2) continue;
            if (!lvl.getBlockState(p).is(BlockTags.LOGS)) continue;
            cluster.add(p.immutable());
            for (BlockPos d : new BlockPos[]{p.above(), p.below(), p.north(), p.south(), p.east(), p.west()}) {
                if (!visited.contains(d)) {
                    visited.add(d);
                    q.addLast(d);
                }
            }
        }
    }

    private void pruneCluster() {
        if (cluster == null || cluster.isEmpty()) return;
        java.util.Iterator<BlockPos> it = cluster.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!isLog(adv.level(), p)) it.remove();
        }
    }

    private BlockPos pickNextFromCluster() {
        if (cluster == null || cluster.isEmpty()) return null;
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        for (BlockPos p : cluster) {
            double d2 = adv.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best == null ? null : best.immutable();
    }


    private static boolean isLog(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LOGS);
    }

    private static BlockPos findNearestLog(Adventurer a, int radius) {
        BlockPos origin = a.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        int r = Math.max(1, radius);
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (!a.isValidWorkPos(p)) continue;
                    if (!a.level().getBlockState(p).is(BlockTags.LOGS)) continue;
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
