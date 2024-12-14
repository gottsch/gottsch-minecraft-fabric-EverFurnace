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
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeInputProvider;
import net.minecraft.recipe.RecipeUnlocker;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by Mark Gottschling on 12/05/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ModFurnaceBlockEntityMixin extends LockableContainerBlockEntity implements SidedInventory, RecipeUnlocker, RecipeInputProvider { //}, IModFurnaceBlockEntityMixin {

    @Unique
    private long lastGameTime;

    protected ModFurnaceBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void onSave(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
        nbt.putLong("lastGameTime", this.lastGameTime);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void onLoad(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
        this.lastGameTime = nbt.getLong("lastGameTime");
    }

    /**
     * a simple mixin that executes at the beginning of the Furnace's (BlastFurnace, Smoker) tick event.
     * @param world
     * @param pos
     * @param state
     * @param blockEntity
     * @param ci
     */
    @Inject(method = "tick", at = @At("HEAD")) // target more specifically somewhere closer to the actual calculations?
    private static void onTick(ServerWorld world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        // cast block entity as a mixin block entity
        ModFurnaceBlockEntityMixin blockEntityMixin = (ModFurnaceBlockEntityMixin)(Object) blockEntity;

        // record last world time
        long localLastGameTime = blockEntityMixin.getLastGameTime();
        blockEntityMixin.setLastGameTime(blockEntity.getWorld().getTime());

        if (!blockEntity.isBurning()){
            return;
        }

        // calculate the difference between game time and the lastGameTime
        long deltaTime = blockEntity.getWorld().getTime() - localLastGameTime;

        // exit if not enough time has passed
        if (deltaTime < 20) {
            return;
        }

        /*
         * //////////////////////
         * validations
         * //////////////////////
         */
        ItemStack cookStack = blockEntity.inventory.get(AbstractFurnaceBlockEntity.INPUT_SLOT_INDEX);
        if (cookStack.isEmpty()) return;

        // get the output stack
        ItemStack outputStack = blockEntity.inventory.get(AbstractFurnaceBlockEntity.OUTPUT_SLOT_INDEX);
        // return if it is already maxed out
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxCountPerStack()) return;

        // test if can accept recipe output
        SingleStackRecipeInput singleStackRecipeInput = new SingleStackRecipeInput(cookStack);
        RecipeEntry<? extends AbstractCookingRecipe> recipeEntry;
        recipeEntry = (RecipeEntry)blockEntity.matchGetter.getFirstMatch(singleStackRecipeInput, world).orElse(null);

        if (!AbstractFurnaceBlockEntity.canAcceptRecipeOutput(blockEntity.getWorld().getRegistryManager(), recipeEntry, singleStackRecipeInput, blockEntity.inventory, blockEntity.getMaxCountPerStack())) return;
        /////////////////////////

        /*
         * begin processing
         */
        // calculate totalBurnTimeRemaining
        ItemStack fuelStack = blockEntity.inventory.get(AbstractFurnaceBlockEntity.FUEL_SLOT_INDEX);
        if (fuelStack.isEmpty()) return;

        // have to calculate fuel time as it is no longer calculated during readNbt() as in 1.21.1
        if (blockEntity.litTotalTime == 0) {
            blockEntity.litTotalTime = blockEntity.getFuelTime(blockEntity.getWorld().getFuelRegistry(), fuelStack);
        }

        long totalBurnTimeRemaining = (long) (fuelStack.getCount() - 1) * blockEntity.litTotalTime + blockEntity.litTimeRemaining;

        // calculate totalCookTimeRemaining
        long totalCookTimeRemaining = (long) (cookStack.getCount() -1) * blockEntity.cookingTotalTime + (blockEntity.cookingTotalTime - blockEntity.cookingTimeSpent);

        // determine the max amount of time that can be used before one or both input run out.
        long maxInputTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);

        /*
         * determine  the actual max time that can be applied to processing. ie if elapsed time is < maxInputTime,
         * then only the elapse time can be used.
         */
        long actualAppliedTime = Math.min(deltaTime, maxInputTime);

        if (actualAppliedTime < blockEntity.litTotalTime) {
            // reduce burn time
            blockEntity.litTimeRemaining =- (int) actualAppliedTime;
            if (blockEntity.litTimeRemaining <= 0) {
                Item fuelItem = fuelStack.getItem();
                // reduce the size of the fuel stack
                fuelStack.decrement(1);
                if (fuelStack.isEmpty()) {
                    blockEntity.litTimeRemaining = 0;
                    blockEntity.inventory.set(1, fuelItem.getRecipeRemainder());
                } else {
                    blockEntity.litTimeRemaining =+ blockEntity.litTotalTime;
                }
            }
        } else {
            int quotient = (int) (Math.floorDivExact(actualAppliedTime, blockEntity.litTotalTime));
            long remainder = actualAppliedTime % blockEntity.litTotalTime;
            // reduced stack by quotient
            Item fuelItem = fuelStack.getItem();
            fuelStack.decrement(quotient);
            // reduce litTimeRemaining by remainder
            blockEntity.litTimeRemaining =- (int)remainder;
            if (blockEntity.litTimeRemaining <= 0) {
                // reduce the size of the fuel stack
                fuelStack.decrement(1);
            }
            if (fuelStack.isEmpty()) {
                blockEntity.litTimeRemaining = 0;
                blockEntity.inventory.set(1, fuelItem.getRecipeRemainder());
            } else {
                blockEntity.litTimeRemaining =+ blockEntity.litTotalTime;
            }
        }

        if (actualAppliedTime < blockEntity.cookingTotalTime) {
            // increment cook time
            blockEntity.cookingTimeSpent =+ (int) actualAppliedTime;
            if (blockEntity.cookingTimeSpent >= blockEntity.cookingTotalTime) {
                if (AbstractFurnaceBlockEntity.craftRecipe(world.getRegistryManager(), recipeEntry, singleStackRecipeInput, blockEntity.inventory, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
                }
                if (cookStack.isEmpty()) {
                    blockEntity.cookingTimeSpent = 0;
                    blockEntity.cookingTotalTime = 0;
                } else {
                    blockEntity.cookingTotalTime -= blockEntity.cookingTotalTime;
                }
            }
        }
        // actual applied time is greated that cook time total,
        // there, need to apply a factor of
        else {
            int quotient = (int) (Math.floorDivExact(actualAppliedTime, blockEntity.cookingTotalTime));
            long remainder = actualAppliedTime % blockEntity.cookingTotalTime;
            // reduced stack by quotient
            boolean isSuccessful = false;
            for (int iterations = 0; iterations < quotient; iterations++) {
                isSuccessful |= AbstractFurnaceBlockEntity.craftRecipe(world.getRegistryManager(), recipeEntry, singleStackRecipeInput, blockEntity.inventory, blockEntity.getMaxCountPerStack());
            }
            // update last recipe
            if (isSuccessful) blockEntity.setLastRecipe(recipeEntry);

            // increment cook time
            blockEntity.cookingTimeSpent =+ (int) remainder;
            if (blockEntity.cookingTimeSpent >= blockEntity.cookingTotalTime) {
                if (AbstractFurnaceBlockEntity.craftRecipe(world.getRegistryManager(), recipeEntry, singleStackRecipeInput, blockEntity.inventory, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
                }
                if (cookStack.isEmpty()) {
                    blockEntity.cookingTimeSpent = 0;
                    blockEntity.cookingTotalTime = 0;
                } else {
                    blockEntity.cookingTotalTime -= blockEntity.cookingTotalTime;
                }
            }
        }

        if(!blockEntity.isBurning()) {
            state = state.with(AbstractFurnaceBlock.LIT, Boolean.valueOf(blockEntity.isBurning()));
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
