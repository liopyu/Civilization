package net.liopyu.civilization.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.liopyu.civilization.net.BuildPreviewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

public final class BuildPreviewClient {
    public static final class Entry {
        public final List<BlockPos> pos;
        public int until;

        public Entry(List<BlockPos> pos, int until) {
            this.pos = pos; this.until = until;
        }
    }

    private static final Int2ObjectOpenHashMap<Entry> MAP = new Int2ObjectOpenHashMap<>();

    public static void receive(BuildPreviewPacket p) {
        var lvl = Minecraft.getInstance().level; if (lvl == null) return;
        int now = (int) lvl.getGameTime();
        MAP.put(p.entityId(), new Entry(p.positions(), now + p.ttl()));
    }

    public static Int2ObjectOpenHashMap<Entry> entries() {
        return MAP;
    }
}
