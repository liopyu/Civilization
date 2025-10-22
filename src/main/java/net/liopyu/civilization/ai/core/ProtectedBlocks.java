package net.liopyu.civilization.ai.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class ProtectedBlocks {
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet set = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public void protect(BlockPos p) {
        if (p != null) set.add(p.asLong());
    }

    public void protect(Iterable<BlockPos> positions) {
        if (positions == null) return;
        for (BlockPos p : positions) if (p != null) set.add(p.asLong());
    }

    public boolean isProtected(BlockPos p) {
        return p != null && set.contains(p.asLong());
    }

    /**
     * Remove a single protected block.
     */
    public void unprotect(BlockPos p) {
        if (p != null) set.remove(p.asLong());
    }

    /**
     * Remove many protected blocks at once.
     */
    public void unprotect(Iterable<BlockPos> positions) {
        if (positions == null) return;
        for (BlockPos p : positions) if (p != null) set.remove(p.asLong());
    }

    public void clear() {
        set.clear();
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putLongArray("pos", set.toLongArray());
        return t;
    }

    public void fromTag(CompoundTag t) {
        set.clear();
        if (t != null && t.contains("pos")) {
            for (long l : t.getLongArray("pos")) set.add(l);
        }
    }

    // (Optional helpers)
    public int size() {
        return set.size();
    }

    public boolean isEmpty() {
        return set.isEmpty();
    }
}
