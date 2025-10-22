package net.liopyu.civilization.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class BuildPreviewRenderer {
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance(); var lvl = mc.level; if (lvl == null) return;
        int now = (int) lvl.getGameTime(); PoseStack stack = e.getPoseStack(); var cam = e.getCamera();
        stack.pushPose(); stack.translate(-cam.getPosition().x, -cam.getPosition().y, -cam.getPosition().z);
        RenderSystem.disableDepthTest();
        var buffers = e.getLevelRenderer().renderBuffers.bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        var it = BuildPreviewClient.entries().int2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var en = it.next().getValue();
            if (now > en.until) {
                it.remove(); continue;
            }
            for (var p : en.pos) LevelRenderer.renderLineBox(stack, vc, new AABB(p).inflate(0.0025), 0f, 1f, 0f, 0.9f);
        }
        buffers.endBatch();
        RenderSystem.enableDepthTest();
        stack.popPose();
    }
}
