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

import mod.gottsch.fabric.everfurnace.core.EverFurnace;
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
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeInputProvider;
import net.minecraft.recipe.RecipeUnlocker;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
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
     * a simple mixin at executes that executes at the beginning of the Furnace's tick event.
     * the mixin processes any jewelry/charms the player may be using.
     * @param ci
     */
    @Inject(method = "tick", at = @At("HEAD")) // target more specifically somewhere closer to the actual calculations?
    private static void onTick(World world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        // cast block entity as a mixin block entity
        ModFurnaceBlockEntityMixin blockEntityMixin = (ModFurnaceBlockEntityMixin)(Object) blockEntity;

        // capture saved lastGameTime
        long localLastGameTime = blockEntityMixin.getLastGameTime();
        // update lastGameTime
        blockEntityMixin.setLastGameTime(blockEntity.getWorld().getTime());

        // check if the furnace is burning
        if (!blockEntity.isBurning()){
            return;
        }

        // calculate the difference between game time and the lastGameTime
        long deltaTime = blockEntity.getWorld().getTime() - localLastGameTime;

        // if less than 1 second (20 ticks) has elapsed then return
        if (deltaTime < 20) {
            return;
        }
        EverFurnace.LOGGER.debug("delta time -> {}", deltaTime);

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
        RecipeEntry<?> recipeEntry = (RecipeEntry<?>)blockEntity.matchGetter.getFirstMatch(new SingleStackRecipeInput(cookStack), world).orElse(null);
        if (!AbstractFurnaceBlockEntity.canAcceptRecipeOutput(blockEntity.getWorld().getRegistryManager(), recipeEntry, blockEntity.inventory, blockEntity.getMaxCountPerStack())) return;

        /////////////////////////

        /*
         * begin processing
         */
        // calculate totalBurnTimeRemaining
        ItemStack fuelStack = blockEntity.inventory.get(AbstractFurnaceBlockEntity.FUEL_SLOT_INDEX);
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

        // TODO 2 use cases a) applyTime < cookTimeTotal (may or may not cook item) and b) applyTime > cookTimeTotal
        if (actualAppliedTime < blockEntity.fuelTime) {
            // TODO this may be incorrect... because fuelTime could be less than cookTimeTotal
            // TODO it isn't a given that fuel time is always longer than cook time.
            // reduce burn time
            blockEntity.burnTime = -(int) actualAppliedTime;
            if (blockEntity.burnTime <= 0) {
                Item fuelItem = fuelStack.getItem();
                // reduce the size of the fuel stack
                fuelStack.decrement(1);
                if (fuelStack.isEmpty()) {
                    blockEntity.burnTime = 0;
                    Item fuelItemRecipeRemainder = fuelItem.getRecipeRemainder();
                    blockEntity.inventory.set(1, fuelItemRecipeRemainder == null ? ItemStack.EMPTY : new ItemStack(fuelItemRecipeRemainder));
                } else {
                    blockEntity.burnTime += blockEntity.fuelTime;
                }
            }
        } else {
            quotient
        }

        if (actualAppliedTime < blockEntity.cookTimeTotal) {
            // increment cook time
            blockEntity.cookTime += (int) actualAppliedTime;
            if (blockEntity.cookTime >= blockEntity.cookTimeTotal) {
                if (AbstractFurnaceBlockEntity.craftRecipe(world.getRegistryManager(), recipeEntry, blockEntity.inventory, blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
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
            //

        }

        if(!blockEntity.isBurning()) {
            state = state.with(AbstractFurnaceBlock.LIT, Boolean.valueOf(blockEntity.isBurning()));
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            AbstractFurnaceBlockEntity.markDirty(world, pos, state);
        }


        // NOTE getCookTime() gets the cook time for the current slotted item
//        EverFurnace.LOGGER.debug("updating lastGameTime to {}", ((AbstractFurnaceBlockEntity)(Object)blockEntity).getWorld().getTime());
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
