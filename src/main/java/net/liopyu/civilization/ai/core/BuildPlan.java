package net.liopyu.civilization.ai.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.*;

public final class BuildPlan {
    public static final class Step {
        public final BlockPos rel;
        public final BlockState state;

        Step(BlockPos rel, BlockState state) {
            this.rel = rel.immutable(); this.state = state;
        }
    }

    private final List<Step> steps;
    private final Map<Item, Integer> required;

    private BuildPlan(List<Step> steps, Map<Item, Integer> required) {
        this.steps = List.copyOf(steps);
        this.required = Map.copyOf(required);
    }

    public static BuildPlan fromStructure(ServerLevel level, ResourceLocation id, boolean ignoreAir) {
        var opt = level.getServer().getStructureManager().get(id);
        if (opt.isEmpty()) return new BuildPlan(List.of(), Map.of());
        var t = opt.get();
        var palettes = t.palettes;
        if (palettes.isEmpty()) return new BuildPlan(List.of(), Map.of());
        var infos = palettes.get(0).blocks();

        List<Step> out = new ArrayList<>();
        Map<Item, Integer> req = new HashMap<>();

        for (var info : infos) {
            var st = info.state();
            if (ignoreAir && st.isAir()) continue;
            var rel = info.pos();
            out.add(new Step(rel, st));
            var it = st.getBlock().asItem();
            if (it != net.minecraft.world.item.Items.AIR) req.merge(it, 1, Integer::sum);
        }

        out.sort(Comparator
                .comparingInt((Step s) -> s.rel.getY())
                .thenComparingInt(s -> s.rel.getZ())
                .thenComparingInt(s -> s.rel.getX()));

        return new BuildPlan(out, req);
    }


    public List<Step> steps() {
        return steps;
    }

    public Map<Item, Integer> requiredItems() {
        return required;
    }

    public List<BlockPos> absolutePositions(BlockPos anchor) {
        List<BlockPos> abs = new ArrayList<>(steps.size());
        for (Step s : steps) abs.add(anchor.offset(s.rel));
        return abs;
    }
    

}
