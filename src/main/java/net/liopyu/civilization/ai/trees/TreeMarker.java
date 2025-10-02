package net.liopyu.civilization.ai.trees;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static net.liopyu.civilization.ai.util.BlockUtil.isLeaves;
import static net.liopyu.civilization.ai.util.BlockUtil.isLog;

/**
 * Holds a set of marked tree block positions and can flood-fill from a start log.
 * NO recursion anywhere — just iterative BFS with caps.
 */
public final class TreeMarker {

    private final ArrayDeque<BlockPos> nodes = new ArrayDeque<>();

    /**
     * Clear all marked nodes.
     */
    public void clear() {
        nodes.clear();
    }

    /**
     * True if nothing is marked.
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Remove a single node (after it was chopped).
     */
    public void remove(BlockPos pos) {
        nodes.remove(pos);
    }

    /**
     * Snapshot for safe iteration (avoid concurrent modification).
     */
    public List<BlockPos> snapshot() {
        return new ArrayList<>(nodes);
    }

    /**
     * Iterate all nodes (be careful not to mutate while iterating).
     */
    public Iterable<BlockPos> all() {
        return nodes;
    }

    /**
     * Mark the entire tree (logs + leaves) starting at {@code start}.
     * Uses an iterative BFS with a hard cap to avoid huge traversals.
     */
    public void mark(Level level, BlockPos start) {
        nodes.clear();

        final int MAX_TREE_NODES = 192;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> seen = new HashSet<>();

        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty() && nodes.size() < MAX_TREE_NODES) {
            BlockPos p = queue.removeFirst();
            BlockState bs = level.getBlockState(p);
            if (!(isLog(bs) || isLeaves(bs))) continue;

            nodes.add(p.immutable());

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (!seen.add(n)) continue;
                        BlockState bs2 = level.getBlockState(n);
                        if (isLog(bs2) || isLeaves(bs2)) {
                            queue.addLast(n);
                        }
                    }
                }
            }
        }
    }
}
