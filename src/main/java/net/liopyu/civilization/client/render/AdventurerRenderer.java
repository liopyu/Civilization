package net.liopyu.civilization.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.liopyu.civilization.client.model.AdventurerModel;
import net.liopyu.civilization.client.skin.AdventurerSkins;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public class AdventurerRenderer extends HumanoidMobRenderer<Adventurer, AdventurerModel> {

    private final AdventurerModel wideModel; // Steve
    private final AdventurerModel slimModel; // Alex

    public AdventurerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AdventurerModel(ctx.bakeLayer(ModelLayers.PLAYER), /*slim=*/false), 0.5f);
        this.wideModel = this.getModel();
        this.slimModel = new AdventurerModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), /*slim=*/true);
    }

    @Override
    public ResourceLocation getTextureLocation(Adventurer entity) {
        return AdventurerSkins.getTexture(entity.getUsername());
    }

    @Override
    public void render(Adventurer entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        // Choose model to match skin arms
        boolean slim = AdventurerSkins.isSlim(entity.getUsername());
        AdventurerModel m = slim ? slimModel : wideModel;
        this.model = m;

        // === Mirror PlayerRenderer#setModelProperties ===
        m.setAllVisible(true);                 // spectator handling not needed for Adventurer
        m.crouching = entity.isCrouching();
        m.riding = entity.isPassenger();
        m.young = entity.isBaby();

        // CRUCIAL: feed the swing progress (0..1). Your Adventurer#getAttackAnim(partialTicks)
        // must compute from your swinging/swingTime/currentSwingDuration.
        m.attackTime = entity.getAttackAnim(partialTicks);

        // Arm poses (copied from PlayerRenderer#getArmPose semantics)
        HumanoidModel.ArmPose mainPose = getArmPose(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = getArmPose(entity, InteractionHand.OFF_HAND);

        if (mainPose.isTwoHanded()) {
            offPose = entity.getOffhandItem().isEmpty()
                    ? HumanoidModel.ArmPose.EMPTY
                    : HumanoidModel.ArmPose.ITEM;
        }

        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            m.rightArmPose = mainPose;
            m.leftArmPose = offPose;
        } else {
            m.rightArmPose = offPose;
            m.leftArmPose = mainPose;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    // Same logic as PlayerRenderer#getArmPose but using Adventurer
    private static HumanoidModel.ArmPose getArmPose(Adventurer e, InteractionHand hand) {
        ItemStack item = e.getItemInHand(hand);
        if (item.isEmpty()) return HumanoidModel.ArmPose.EMPTY;

        if (e.getUsedItemHand() == hand && e.getUseItemRemainingTicks() > 0) {
            UseAnim anim = item.getUseAnimation();
            if (anim == UseAnim.BLOCK) return HumanoidModel.ArmPose.BLOCK;
            if (anim == UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
            if (anim == UseAnim.SPEAR) return HumanoidModel.ArmPose.THROW_SPEAR;
            if (anim == UseAnim.CROSSBOW) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            // Add Spyglass/Brush/Horn if your mob uses them
        } else if (!e.swinging && item.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(item)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        // Default: ITEM (lets the attack swing animate the arm)
        return HumanoidModel.ArmPose.ITEM;
    }
}
