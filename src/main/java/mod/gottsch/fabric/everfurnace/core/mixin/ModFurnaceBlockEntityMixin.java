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
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeInputProvider;
import net.minecraft.recipe.RecipeUnlocker;
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
public abstract class ModFurnaceBlockEntityMixin extends LockableContainerBlockEntity implements SidedInventory, RecipeUnlocker, RecipeInputProvider { //}, IModFurnaceBlockEntityMixin {
    private static final int INPUT_SLOT_INDEX = 0;
    private static final int FUEL_SLOT_INDEX = 1;
    private static final int OUTPUT_SLOT_INDEX = 2;

    @Unique
    private long lastGameTime;

    protected ModFurnaceBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void onSave(NbtCompound nbt, CallbackInfo ci) {
        nbt.putLong("lastGameTime", this.lastGameTime);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void onLoad(NbtCompound nbt, CallbackInfo ci) {
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
    private static void onTick(World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
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
        ItemStack cookStack = blockEntity.inventory.get(INPUT_SLOT_INDEX);
        if (cookStack.isEmpty()) return;

        // get the output stack
        ItemStack outputStack = blockEntity.inventory.get(OUTPUT_SLOT_INDEX);
        // return if it is already maxed out
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxCountPerStack()) return;

        // test if can accept recipe output
        Recipe<?> recipe = (Recipe)world.getRecipeManager().getFirstMatch(blockEntity.recipeType, blockEntity, world).orElse(null);
        if (!AbstractFurnaceBlockEntity.canAcceptRecipeOutput(recipe, blockEntity.inventory, blockEntity.getMaxCountPerStack())) return;
        /////////////////////////

        /*
         * begin processing
         */
        // calculate totalBurnTimeRemaining
        ItemStack fuelStack = blockEntity.inventory.get(FUEL_SLOT_INDEX);
        if (fuelStack.isEmpty()) return;
        long totalBurnTimeRemaining = (long) (fuelStack.getCount() - 1) * blockEntity.fuelTime + blockEntity.burnTime;

        // calculate totalCookTimeRemaining
        long totalCookTimeRemaining = (long) (cookStack.getCount() -1) * blockEntity.cookTimeTotal + (blockEntity.cookTimeTotal - blockEntity.cookTime);

        // determine the max amount of time that can be used before one or both input run out.
        long maxInputTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);

        /*
         * determine  the actual max time that can be applied to processing. ie if elapsed time is < maxInputTime,
         * then only the elapse time can be used.
         */
        long actualAppliedTime = Math.min(deltaTime, maxInputTime);

        if (actualAppliedTime < blockEntity.fuelTime) {
            // reduce burn time
            blockEntity.burnTime =- (int) actualAppliedTime;
            if (blockEntity.burnTime <= 0) {
                Item fuelItem = fuelStack.getItem();
                // reduce the size of the fuel stack
                fuelStack.decrement(1);
                if (fuelStack.isEmpty()) {
                    blockEntity.burnTime = 0;
                    Item fuelItemRecipeRemainder = fuelItem.getRecipeRemainder();
                    blockEntity.inventory.set(1, fuelItemRecipeRemainder == null ? ItemStack.EMPTY : new ItemStack(fuelItemRecipeRemainder));
                } else {
                    blockEntity.burnTime =+ blockEntity.fuelTime;
                }
            }
        } else {
            int quotient = (int) (Math.floor((double) actualAppliedTime / blockEntity.fuelTime));
            long remainder = actualAppliedTime % blockEntity.fuelTime;
            // reduced stack by quotient
            Item fuelItem = fuelStack.getItem();
            fuelStack.decrement(quotient);
            // reduce burnTime by remainder
            blockEntity.burnTime =- (int)remainder;
            if (blockEntity.burnTime <= 0) {
                // reduce the size of the fuel stack
                fuelStack.decrement(1);
            }
            if (fuelStack.isEmpty()) {
                blockEntity.burnTime = 0;
                Item fuelItemRecipeRemainder = fuelItem.getRecipeRemainder();
                blockEntity.inventory.set(1, fuelItemRecipeRemainder == null ? ItemStack.EMPTY : new ItemStack(fuelItemRecipeRemainder));
            } else {
                blockEntity.burnTime =+ blockEntity.fuelTime;
            }
        }

        if (actualAppliedTime < blockEntity.cookTimeTotal) {
            // increment cook time
            blockEntity.cookTime =+ (int) actualAppliedTime;
            if (blockEntity.cookTime >= blockEntity.cookTimeTotal) {
                if (AbstractFurnaceBlockEntity.craftRecipe(recipe, blockEntity.inventory, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipe);
                }
                if (cookStack.isEmpty()) {
                    blockEntity.cookTime = 0;
                    blockEntity.cookTimeTotal = 0;
                } else {
                    blockEntity.cookTimeTotal -= blockEntity.cookTimeTotal;
                }
            }
        }
        // actual applied time is greated that cook time total,
        // there, need to apply a factor of
        else {
            int quotient = (int) (Math.floor((double) actualAppliedTime / blockEntity.cookTimeTotal));
            long remainder = actualAppliedTime % blockEntity.cookTimeTotal;
            // reduced stack by quotient
            boolean isSuccessful = false;
            for (int iterations = 0; iterations < quotient; iterations++) {
                isSuccessful |= AbstractFurnaceBlockEntity.craftRecipe(recipe, blockEntity.inventory, blockEntity.getMaxCountPerStack());
            }
            // update last recipe
            if (isSuccessful) blockEntity.setLastRecipe(recipe);

            // increment cook time
            blockEntity.cookTime =+ (int) remainder;
            if (blockEntity.cookTime >= blockEntity.cookTimeTotal) {
                if (AbstractFurnaceBlockEntity.craftRecipe(recipe, blockEntity.inventory, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipe);
                }
                if (cookStack.isEmpty()) {
                    blockEntity.cookTime = 0;
                    blockEntity.cookTimeTotal = 0;
                } else {
                    blockEntity.cookTimeTotal -= blockEntity.cookTimeTotal;
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
