package net.liopyu.civilization.entity;


import net.liopyu.civilization.ai.goal.*;
import net.liopyu.civilization.screen.AdventurerMenu;
import net.liopyu.civilization.util.UsernamePool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class Adventurer extends PathfinderMob {
    private static final EntityDataAccessor<String> USERNAME =
            SynchedEntityData.defineId(Adventurer.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> INV_TAG =
            SynchedEntityData.defineId(Adventurer.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> HOME_POS =
            SynchedEntityData.defineId(Adventurer.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<java.util.Optional<BlockPos>> HOME_TABLE_POS =
            SynchedEntityData.defineId(Adventurer.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(USERNAME, "");
        builder.define(INV_TAG, new CompoundTag());
        builder.define(HOME_POS, java.util.Optional.empty());
        builder.define(HOME_TABLE_POS, java.util.Optional.empty());
    }

    public boolean hasHomeTable() {
        return entityData.get(HOME_TABLE_POS).isPresent();
    }

    public java.util.Optional<BlockPos> getHomeTablePos() {
        return entityData.get(HOME_TABLE_POS);
    }

    public void setHomeTablePos(BlockPos p) {
        entityData.set(HOME_TABLE_POS, java.util.Optional.of(p.immutable()));
    }

    public void clearHomeTable() {
        entityData.set(HOME_TABLE_POS, java.util.Optional.empty());
    }

    private static final int INV_SIZE = 27;

    public NonNullList<ItemStack> getInternalInventory() {
        NonNullList<ItemStack> list = NonNullList.withSize(INV_SIZE, ItemStack.EMPTY);
        CompoundTag tag = entityData.get(INV_TAG);
        if (!tag.isEmpty()) {
            ContainerHelper.loadAllItems(tag, list, registries()); // 1.21.x signature
        }
        return list;
    }

    public void setInternalInventory(NonNullList<ItemStack> list) {
        CompoundTag out = new CompoundTag();
        // ORDER: provider, tag, list, alwaysWrite
        ContainerHelper.saveAllItems(out, list, true, registries());
        entityData.set(INV_TAG, out);
    }

    private HolderLookup.Provider registries() {
        return this.level().registryAccess();
    }

    // Utilities
    public boolean hasHome() {
        return entityData.get(HOME_POS).isPresent();
    }

    public java.util.Optional<BlockPos> getHomePos() {
        return entityData.get(HOME_POS);
    }

    public void setHomePos(BlockPos pos) {
        entityData.set(HOME_POS, java.util.Optional.of(pos.immutable()));
    }

    public void clearHome() {
        entityData.set(HOME_POS, java.util.Optional.empty());
    }

    public String getUsername() {
        return this.entityData.get(USERNAME);
    }

    public void setUsername(String name) {
        this.entityData.set(USERNAME, name);
    }

    /**
     * Choose a username deterministically by UUID if you like
     */
    private static String pickUsername(RandomSource rnd) {
        String[] POOL = UsernamePool.NAMES;
        return POOL[rnd.nextInt(POOL.length)];
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("AdventurerName", getUsername());
        tag.put("Inv", entityData.get(INV_TAG).copy());
        getHomePos().ifPresent(p -> tag.putLong("Home", p.asLong()));
        getHomeTablePos().ifPresent(p -> tag.putLong("HomeTable", p.asLong()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AdventurerName")) setUsername(tag.getString("AdventurerName"));
        if (tag.contains("Inv", Tag.TAG_COMPOUND)) {
            entityData.set(INV_TAG, tag.getCompound("Inv").copy());
        }
        if (tag.contains("Home")) setHomePos(BlockPos.of(tag.getLong("Home")));
        if (tag.contains("HomeTable")) setHomeTablePos(BlockPos.of(tag.getLong("HomeTable")));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new AdventurerMenu(id, inv, this),
                    Component.literal("Adventurer")
            );
            sp.openMenu(provider);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private int countLogs() {
        int c = 0;
        for (ItemStack s : getInternalInventory()) {
            if (!s.isEmpty() && s.getItem() instanceof BlockItem bi) {
                if (bi.getBlock().defaultBlockState().is(BlockTags.LOGS)) {
                    c += s.getCount();
                }
            }
        }
        return c;
    }

    public String chestOwnerKey() {
        return "civilization.owner";
    }

    public UUID chestOwnerId() {
        return this.getUUID();
    }

    // Tiny helpers to mutate internal inv
    public int countItem(ItemStack like) {
        int c = 0;
        for (ItemStack s : getInternalInventory()) if (ItemStack.isSameItemSameComponents(s, like)) c += s.getCount();
        return c;
    }

    public int countItem(net.minecraft.world.item.Item item) {
        int c = 0; for (ItemStack s : getInternalInventory()) if (s.is(item)) c += s.getCount(); return c;
    }

    public boolean addItemToInternal(ItemStack stack) {
        NonNullList<ItemStack> inv = getInternalInventory();
        // simple “merge or first empty” logic
        for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
            ItemStack slot = inv.get(i);
            if (slot.isEmpty()) {
                inv.set(i, stack.copy()); stack.setCount(0); break;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(move); stack.shrink(move);
            }
        }
        setInternalInventory(inv);
        return stack.isEmpty();
    }

    public boolean consumeItems(net.minecraft.world.item.Item item, int want) {
        NonNullList<ItemStack> inv = getInternalInventory();
        int need = want;
        for (int i = 0; i < inv.size() && need > 0; i++) {
            ItemStack s = inv.get(i);
            if (s.is(item)) {
                int used = Math.min(need, s.getCount());
                s.shrink(used); need -= used;
                if (s.getCount() <= 0) inv.set(i, ItemStack.EMPTY);
            }
        }
        if (need == 0) {
            setInternalInventory(inv); return true;
        }
        return false;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance diff,
                                                  MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, diff, reason, data);
        if (getUsername().isEmpty()) setUsername(pickUsername(this.getRandom()));
        return out;
    }

    public Adventurer(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.getNavigation().setCanFloat(true);
        if (this.getNavigation() instanceof GroundPathNavigation gpn) {
            gpn.setCanOpenDoors(true);
            gpn.setCanPassDoors(true);
        }
    }

    // Base attributes (tweak as needed)
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new SetUpBaseGoal(this, 1.0D, 8));
        this.goalSelector.addGoal(3, new GatherResourcesGoal(this, 1.0D));
    }


    // Simple helper: gentle wandering nudge if stuck (safe to remove)
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && this.tickCount % 200 == 0 && this.getNavigation().isDone()) {
            Vec3 v = DefaultRandomPos.getPos(this, 6, 3);
            if (v != null) this.getNavigation().moveTo(v.x, v.y, v.z, 1.0D);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !this.level().isClientSide) this.playSound(SoundEvents.PLAYER_HURT, 0.8F, 1.0F);
        return r;
    }

    public boolean isValidWorkPos(BlockPos pos) {
        return this.level().isLoaded(pos) && this.level().isInWorldBounds(pos);
    }

    // When you’re ready to add inventories, tools, or “job” enums,
    // add SynchedEntityData fields here to network them to clients.
}
