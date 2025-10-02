package net.liopyu.civilization.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(BlockBehaviour.class)
public interface BlockBehaviourAccessor {
    @Invoker("getDrops")
    List<ItemStack> invokeGetDrops(BlockState state, LootParams.Builder builder);
}
