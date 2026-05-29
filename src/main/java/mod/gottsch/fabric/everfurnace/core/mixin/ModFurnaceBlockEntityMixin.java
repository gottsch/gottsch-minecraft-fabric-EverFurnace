/*
 * This file is part of  EverFurnace.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
 *
 * EverFurnace is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverFurnace is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverFurnace.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.fabric.everfurnace.core.mixin;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import mod.gottsch.fabric.everfurnace.core.CatchupEffectHelper;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeInputProvider;
import net.minecraft.recipe.RecipeUnlocker;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by Mark Gottschling on 12/05/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ModFurnaceBlockEntityMixin extends LockableContainerBlockEntity
        implements SidedInventory, RecipeUnlocker, RecipeInputProvider {

    @Unique private static final int INPUT_SLOT  = 0;
    @Unique private static final int FUEL_SLOT   = 1;
    @Unique private static final int OUTPUT_SLOT = 2;

    @Unique private long lastGameTime;

    protected ModFurnaceBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void onSave(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        nbt.putLong("lastGameTime", this.lastGameTime);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void onLoad(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        this.lastGameTime = nbt.getLong("lastGameTime");
    }

    /**
     * A simple mixin that executes at the beginning of the Furnace's (BlastFurnace, Smoker)
     * tick event and applies offline catch-up cooking.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private static void onTick(World world, BlockPos pos, BlockState state,
                                AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {

        ModFurnaceBlockEntityMixin mixin    = (ModFurnaceBlockEntityMixin)(Object) blockEntity;
        IModFurnaceBlockEntityMixin accessor = (IModFurnaceBlockEntityMixin)(Object) blockEntity;

        long localLastGameTime = mixin.getLastGameTime();
        mixin.setLastGameTime(world.getTime());

        if (!accessor.callIsBurning()) {
            return;
        }

        long deltaTime = world.getTime() - localLastGameTime;
        if (deltaTime < 20) {
            return;
        }

        DefaultedList<ItemStack> inv = accessor.getInventory();

        ItemStack cookStack = inv.get(INPUT_SLOT);
        if (cookStack.isEmpty()) return;

        ItemStack outputStack = inv.get(OUTPUT_SLOT);
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxCountPerStack()) return;

        @SuppressWarnings({"unchecked", "rawtypes"})
        RecipeEntry<?> recipeEntry = (RecipeEntry<?>) accessor.getMatchGetter()
                .getFirstMatch(new SingleStackRecipeInput(cookStack), world).orElse(null);
        if (!IModFurnaceBlockEntityMixin.callCanAcceptRecipeOutput(
                world.getRegistryManager(), recipeEntry, inv, blockEntity.getMaxCountPerStack())) return;

        ItemStack fuelStack = inv.get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        long totalBurnTimeRemaining = (long)(fuelStack.getCount() - 1) * accessor.getFuelTime() + accessor.getBurnTime();
        long totalCookTimeRemaining = (long)(cookStack.getCount() - 1) * accessor.getCookTimeTotal()
                + (accessor.getCookTimeTotal() - accessor.getCookTime());

        long maxInputTime      = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);
        long actualAppliedTime = Math.min(deltaTime, maxInputTime);

        // ---- fuel consumption -----------------------------------------------

        if (actualAppliedTime < accessor.getFuelTime()) {
            accessor.setBurnTime(accessor.getBurnTime() - (int) actualAppliedTime);
            if (accessor.getBurnTime() <= 0) {
                Item fuelItem = fuelStack.getItem();
                fuelStack.decrement(1);
                if (fuelStack.isEmpty()) {
                    accessor.setBurnTime(0);
                    Item remainder = fuelItem.getRecipeRemainder();
                    inv.set(FUEL_SLOT, remainder == null ? ItemStack.EMPTY : new ItemStack(remainder));
                } else {
                    accessor.setBurnTime(accessor.getFuelTime());
                }
            }
        } else {
            int  quotient  = (int) Math.floor((double) actualAppliedTime / accessor.getFuelTime());
            long remainder = actualAppliedTime % accessor.getFuelTime();
            Item fuelItem  = fuelStack.getItem();
            fuelStack.decrement(quotient);
            accessor.setBurnTime(accessor.getBurnTime() - (int) remainder);
            if (accessor.getBurnTime() <= 0) {
                fuelStack.decrement(1);
            }
            if (fuelStack.isEmpty()) {
                accessor.setBurnTime(0);
                Item fuelRemainder = fuelItem.getRecipeRemainder();
                inv.set(FUEL_SLOT, fuelRemainder == null ? ItemStack.EMPTY : new ItemStack(fuelRemainder));
            } else {
                accessor.setBurnTime(accessor.getFuelTime());
            }
        }

        // ---- cooking progress -----------------------------------------------

        int itemsCooked = 0;

        if (actualAppliedTime < accessor.getCookTimeTotal()) {
            accessor.setCookTime(accessor.getCookTime() + (int) actualAppliedTime);
            if (accessor.getCookTime() >= accessor.getCookTimeTotal()) {
                if (IModFurnaceBlockEntityMixin.callCraftRecipe(
                        world.getRegistryManager(), recipeEntry, inv, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
                    itemsCooked++;
                }
                if (cookStack.isEmpty()) {
                    accessor.setCookTime(0);
                    accessor.setCookTimeTotal(0);
                } else {
                    accessor.setCookTimeTotal(0);
                }
            }
        } else {
            int  quotient  = (int) Math.floor((double) actualAppliedTime / accessor.getCookTimeTotal());
            long remainder = actualAppliedTime % accessor.getCookTimeTotal();

            for (int i = 0; i < quotient; i++) {
                if (IModFurnaceBlockEntityMixin.callCraftRecipe(
                        world.getRegistryManager(), recipeEntry, inv, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
                    itemsCooked++;
                }
            }

            accessor.setCookTime(accessor.getCookTime() + (int) remainder);
            if (accessor.getCookTime() >= accessor.getCookTimeTotal()) {
                if (IModFurnaceBlockEntityMixin.callCraftRecipe(
                        world.getRegistryManager(), recipeEntry, inv, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
                    itemsCooked++;
                }
                if (cookStack.isEmpty()) {
                    accessor.setCookTime(0);
                    accessor.setCookTimeTotal(0);
                } else {
                    accessor.setCookTimeTotal(0);
                }
            }
        }

        // ---- effects --------------------------------------------------------

        if (itemsCooked > 0 && world instanceof ServerWorld serverWorld) {
            CatchupEffectHelper.spawnFurnaceEffects(serverWorld, pos);
            CatchupEffectHelper.notifyNearbyPlayers(serverWorld, pos, itemsCooked);
        }

        // ---- post-processing ------------------------------------------------

        if (!accessor.callIsBurning()) {
            state = state.with(AbstractFurnaceBlock.LIT, false);
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            AbstractFurnaceBlockEntity.markDirty(world, pos, state);
        }
    }

    @Unique
    public long getLastGameTime() {
        return this.lastGameTime;
    }

    @Unique
    public void setLastGameTime(long gameTime) {
        this.lastGameTime = gameTime;
    }
}
