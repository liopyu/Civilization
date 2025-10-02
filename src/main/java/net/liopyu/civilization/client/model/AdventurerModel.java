// net/liopyu/civilization/client/model/AdventurerModel.java
package net.liopyu.civilization.client.model;

import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

public class AdventurerModel extends PlayerModel<Adventurer> {

    public AdventurerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    /**
     * PlayerModel -> HumanoidModel#setupAnim already applies attack swing when
     * model.attackTime > 0. We leave super.setupAnim to do the heavy lifting,
     * but this class exists to keep things isolated in case you want Adventurer-
     * specific pose adjustments later (e.g., mining stances).
     * <p>
     * If you ever need to force additional rotations during mining (e.g., a more
     * pronounced overhand strike), you can call setupAttackAnimation(entity, ageInTicks)
     * or tweak arms after super.setupAnim.
     */
    @Override
    public void setupAnim(Adventurer entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // Optional: ensure sleeves/jacket follow arms/body (PlayerModel already does this in its setupAnim)
        // leftSleeve.copyFrom(leftArm);
        // rightSleeve.copyFrom(rightArm);
        // jacket.copyFrom(body);

        // If you want to *force* the vanilla attack swing pass (in case some pose blocked it),
        // you can uncomment this. Usually not needed if attackTime > 0.
        // this.setupAttackAnimation(entity, ageInTicks);
    }
}
