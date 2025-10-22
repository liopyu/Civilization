package net.liopyu.civilization.ai.core;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class ResourcePlanner {
    public static final class Need {
        public final ResourceLocation id;
        public final ResourceType type;
        public final Set<Item> items;
        public final Set<TagKey<Item>> itemTags;
        public final Set<Block> blocks;
        public final Set<TagKey<Block>> blockTags;
        public int priority;
        public int remaining;
        int seenTotal;

        Need(ResourceLocation id, ResourceType type, Set<Item> items, Set<TagKey<Item>> itemTags, Set<Block> blocks, Set<TagKey<Block>> blockTags, int count, int priority) {
            this.id = id;
            this.type = type;
            this.items = new HashSet<>(items);
            this.itemTags = new HashSet<>(itemTags);
            this.blocks = new HashSet<>(blocks);
            this.blockTags = new HashSet<>(blockTags);
            this.remaining = Math.max(0, count);
            this.priority = priority;
            this.seenTotal = 0;
        }

        public boolean accepts(BlockState st, RegistryAccess ra) {
            if (blocks.contains(st.getBlock())) return true;
            if (!blockTags.isEmpty()) for (TagKey<Block> t : blockTags) if (st.is(t)) return true;
            return false;
        }

        int inventoryCount(Map<Item, Integer> inv) {
            int c = 0;
            for (Item it : items) c += inv.getOrDefault(it, 0);
            if (!itemTags.isEmpty()) {
                for (Map.Entry<Item, Integer> e : inv.entrySet()) {
                    if (e.getKey().builtInRegistryHolder().tags().anyMatch(itemTags::contains)) c += e.getValue();
                }
            }
            return c;
        }

        boolean matchesItem(Item it) {
            if (items.contains(it)) return true;
            if (!itemTags.isEmpty()) return it.builtInRegistryHolder().tags().anyMatch(itemTags::contains);
            return false;
        }
    }

    private final List<Need> needs = new ArrayList<>();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private static String builtInId(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).toString();
    }

    private static String builtInId(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).toString();
    }

    public ListTag saveNeeds() {
        ListTag list = new ListTag();
        for (Need n : needs) {
            CompoundTag t = new CompoundTag();
            t.putString("id", n.id.toString());
            t.putString("type", n.type.name());
            t.putInt("remaining", n.remaining);
            t.putInt("priority", n.priority);
            ListTag its = new ListTag();
            for (Item it : n.items) its.add(StringTag.valueOf(builtInId(it)));
            t.put("items", its);
            ListTag itTags = new ListTag();
            for (TagKey<Item> tag : n.itemTags) itTags.add(StringTag.valueOf(tag.location().toString()));
            t.put("itemTags", itTags);
            ListTag bl = new ListTag();
            for (Block b : n.blocks) bl.add(StringTag.valueOf(builtInId(b)));
            t.put("blocks", bl);
            ListTag blTags = new ListTag();
            for (TagKey<Block> tag : n.blockTags) blTags.add(StringTag.valueOf(tag.location().toString()));
            t.put("blockTags", blTags);
            list.add(t);
        }
        return list;
    }

    public void loadNeeds(ListTag list) {
        needs.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation rid = ResourceLocation.parse(t.getString("id"));
            ResourceType type = ResourceType.valueOf(t.getString("type"));
            int remaining = t.getInt("remaining");
            int prio = t.getInt("priority");

            Set<Item> items = new HashSet<>();
            ListTag its = t.getList("items", Tag.TAG_STRING);
            for (int j = 0; j < its.size(); j++) {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(its.getString(j)));
                if (it != null) items.add(it);
            }

            Set<TagKey<Item>> itemTags = new HashSet<>();
            ListTag itTags = t.getList("itemTags", Tag.TAG_STRING);
            for (int j = 0; j < itTags.size(); j++)
                itemTags.add(TagKey.create(Registries.ITEM, ResourceLocation.parse(itTags.getString(j))));

            Set<Block> blocks = new HashSet<>();
            ListTag bl = t.getList("blocks", Tag.TAG_STRING);
            for (int j = 0; j < bl.size(); j++) {
                Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(bl.getString(j)));
                if (b != null) blocks.add(b);
            }

            Set<TagKey<Block>> blockTags = new HashSet<>();
            ListTag blTags = t.getList("blockTags", Tag.TAG_STRING);
            for (int j = 0; j < blTags.size(); j++)
                blockTags.add(TagKey.create(Registries.BLOCK, ResourceLocation.parse(blTags.getString(j))));

            Need n = new Need(rid, type, items, itemTags, blocks, blockTags, remaining, prio);
            needs.add(n);
        }
        needs.sort(Comparator.comparingInt(n -> -n.priority));
    }

    public void requestMineable(ResourceLocation id,
                                Set<Item> items, Set<TagKey<Item>> itemTags,
                                Set<Block> blocks, Set<TagKey<Block>> blockTags,
                                int count, int priority) {
        Need n = null;
        for (Need x : needs)
            if (x.id.equals(id) && x.type == ResourceType.MINEABLE) {
                n = x; break;
            }
        if (n == null) {
            n = new Need(id, ResourceType.MINEABLE, items, itemTags, blocks, blockTags, count, priority);
            needs.add(n);
        } else {
            n.items.addAll(items);
            n.itemTags.addAll(itemTags);
            n.blocks.addAll(blocks);
            n.blockTags.addAll(blockTags);
            n.remaining = Math.max(n.remaining, count);
            n.priority = Math.max(n.priority, priority);
        }
        needs.sort(Comparator.comparingInt(x -> -x.priority));
        LOG.info("[Plan] request MINEABLE id={} need={} prio={}", id, n.remaining, n.priority);
    }

    public Optional<Need> topMineableNeed(Map<Item, Integer> inv) {
        reconcile(inv);
        for (Need n : needs)
            if (n.type == ResourceType.MINEABLE && n.remaining > 0) {
                LOG.info("[Plan] top need id={} remaining={}", n.id, n.remaining);
                return Optional.of(n);
            }
        return Optional.empty();
    }

    public void reconcile(Map<Item, Integer> inv) {
        boolean changed = false;
        Iterator<Need> it = needs.iterator();
        while (it.hasNext()) {
            Need n = it.next();
            if (n.remaining <= 0) continue;
            int cur = n.inventoryCount(inv);
            int delta = Math.max(0, cur - n.seenTotal);
            if (delta > 0) {
                int before = n.remaining;
                n.remaining = Math.max(0, n.remaining - delta);
                LOG.info("[Plan] reconcile {} {} -> {}", n.id, before, n.remaining);
                changed = true;
            }
            n.seenTotal = cur;
        }
        if (changed) needs.removeIf(n -> n.remaining <= 0);
    }

    public boolean hasOutstanding(ResourceType type) {
        for (Need n : needs) if (n.type == type && n.remaining > 0) return true;
        return false;
    }
}
