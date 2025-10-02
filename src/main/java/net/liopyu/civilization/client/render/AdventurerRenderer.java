package net.liopyu.civilization.client.render;

import net.liopyu.civilization.Civilization;
import net.liopyu.civilization.client.skin.AdventurerSkins;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AdventurerRenderer extends HumanoidMobRenderer<Adventurer, HumanoidModel<Adventurer>> {

    public AdventurerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Adventurer entity) {
        return AdventurerSkins.getTexture(entity.getUsername());
    }
}
