package net.liopyu.civilization.debug;

import net.liopyu.civilization.ai.core.BuildPlan;
import net.liopyu.civilization.ai.core.BuildPlanSelector;
import net.liopyu.civilization.net.BuildPreviewPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;

public final class BuildPlanDebug {
    public static void preview(ServerPlayer sp, net.liopyu.civilization.entity.Adventurer adv, int ttl) {
        var sl = sp.serverLevel();
        var planId = BuildPlanSelector.selectFor(adv.getType()).orElse(null); if (planId == null) return;
        var plan = BuildPlan.fromStructure(sl, planId, true);
        var seq = plan.steps();
        var site = chooseSite(adv, 8, seq); if (site == null) return;
        var list = new ArrayList<BlockPos>(seq.size());
        for (var s : seq) {
            var p = site.offset(s.rel);
            if (adv.level().getBlockState(p).isAir() || adv.level().getBlockState(p).canBeReplaced())
                list.add(p.immutable());
        }
        if (!list.isEmpty()) PacketDistributor.sendToPlayer(sp, new BuildPreviewPacket(adv.getId(), list, ttl));
    }

    private static BlockPos chooseSite(net.liopyu.civilization.entity.Adventurer a, int radius, java.util.List<BuildPlan.Step> seq) {
        var o = a.blockPosition(); BlockPos best = null; double bestD2 = Double.MAX_VALUE; int r = Math.max(2, radius);
        for (int y = -2; y <= 2; y++)
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) {
                    var c = o.offset(x, y, z); if (!a.isValidWorkPos(c)) continue; if (!areaOk(a, c, seq)) continue;
                    double d2 = a.distanceToSqr(c.getX() + 0.5, c.getY() + 0.02, c.getZ() + 0.5); if (d2 < bestD2) {
                        bestD2 = d2; best = c.immutable();
                    }
                }
        return best;
    }

    private static boolean areaOk(net.liopyu.civilization.entity.Adventurer a, BlockPos center, java.util.List<BuildPlan.Step> seq) {
        var lvl = a.level();
        for (var s : seq) {
            var p = center.offset(s.rel);
            if (!a.isValidWorkPos(p)) return false;
            var below = p.below(); if (!lvl.getBlockState(below).isSolidRender(lvl, below)) return false;
            var here = lvl.getBlockState(p); if (!here.isAir() && !here.canBeReplaced()) return false;
        }
        return true;
    }
}
