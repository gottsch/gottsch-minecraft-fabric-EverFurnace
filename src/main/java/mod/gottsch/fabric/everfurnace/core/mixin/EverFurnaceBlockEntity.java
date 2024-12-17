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
import net.minecraft.block.entity.HopperBlockEntity;
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
public abstract class EverFurnaceBlockEntity extends LockableContainerBlockEntity implements SidedInventory, RecipeUnlocker, RecipeInputProvider { //}, IModFurnaceBlockEntityMixin {
    @Unique
    private static final int INPUT_SLOT = 0;
    @Unique
    private static final int FUEL_SLOT = 1;
    @Unique
    private static final int OUTPUT_SLOT = 2;
    @Unique
    private static final String LAST_GAME_TIME_TAG = "everfurnace_lastGameTime";
    @Unique
    private static final String REMAINING_TIME_TAG = "everfurnace_remainingTime";
    @Unique
    private static final String COOLDOWN_TIME_TAG = "everfurnace_cooldownTime";

    @Unique
    private long everfurnace$lastGameTime;
    @Unique
    private int everfurnace$remainingTime;
    @Unique
    private int everfurnace$Cooldown;

    protected EverFurnaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void onSave(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        nbt.putLong(LAST_GAME_TIME_TAG, getEverfurnace$lastGameTime());
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void onLoad(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        setEverfurnace$lastGameTime(nbt.getLong(LAST_GAME_TIME_TAG));
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
        EverFurnaceBlockEntity blockEntityMixin = (EverFurnaceBlockEntity)(Object) blockEntity;
        IEverFurnaceBlockEntity everFurnaceBlockEntity = (IEverFurnaceBlockEntity) ((Object) blockEntity);

        // record last world time
        long localLastGameTime = blockEntityMixin.getEverfurnace$lastGameTime();
        blockEntityMixin.setEverfurnace$lastGameTime(blockEntity.getWorld().getTime());

        // if not burning - no fuel left - then exit
        if (!everFurnaceBlockEntity.callIsBurning()) {
            blockEntityMixin.everfurnace$ClearTimes();
            return;
        }

        // calculate the difference between game time and the lastGameTime
        long deltaTime = blockEntity.getWorld().getTime() - localLastGameTime;

        int remainingTime = blockEntityMixin.getEverfurnace$remainingTime();
        int cooldownTime = blockEntityMixin.getEverfurnace$Cooldown();

        // exit if not enough time has passed
        if (deltaTime < 20 && remainingTime == 0) {
            return;
        }
        deltaTime += remainingTime;

        /*
         * //////////////////////
         * validations
         * //////////////////////
         */
        ItemStack cookStack = everFurnaceBlockEntity.getInventory().get(INPUT_SLOT);
        if (cookStack.isEmpty()) {
            blockEntityMixin.setEverfurnace$Cooldown(--cooldownTime);
            if (cooldownTime <= 0) {
                blockEntityMixin.setEverfurnace$remainingTime(0);
            }
            return;
        }

        // get the output stack
        ItemStack outputStack = everFurnaceBlockEntity.getInventory().get(OUTPUT_SLOT);
        // return if it is already maxed out
        if (!outputStack.isEmpty() && outputStack.getCount() == blockEntity.getMaxCountPerStack()) {
            blockEntityMixin.setEverfurnace$Cooldown(--cooldownTime);
            if (cooldownTime <= 0) {
                blockEntityMixin.setEverfurnace$remainingTime(0);
            }
            return;
        }

        // test if can accept recipe output
        SingleStackRecipeInput singleStackRecipeInput = new SingleStackRecipeInput(cookStack);
        RecipeEntry recipeEntry;
        recipeEntry = (RecipeEntry)everFurnaceBlockEntity.getMatchGetter().getFirstMatch(singleStackRecipeInput, world).orElse(null);

        if (!IEverFurnaceBlockEntity.callCanAcceptRecipeOutput(blockEntity.getWorld().getRegistryManager(), recipeEntry, singleStackRecipeInput, everFurnaceBlockEntity.getInventory(), blockEntity.getMaxCountPerStack())) return;
        /////////////////////////

        /*
         * begin processing
         */
        // calculate totalBurnTimeRemaining
        ItemStack fuelStack = everFurnaceBlockEntity.getInventory().get(FUEL_SLOT);
        if (fuelStack.isEmpty()) return;

        // have to calculate fuel time as it is no longer calculated during readNbt() as in 1.21.1
        if (everFurnaceBlockEntity.getLitTotalTime() == 0) {
            everFurnaceBlockEntity.setLitTotalTime(everFurnaceBlockEntity.callGetFuelTime(blockEntity.getWorld().getFuelRegistry(), fuelStack));
        }

        long totalBurnTimeRemaining = (long) (fuelStack.getCount() - 1) * everFurnaceBlockEntity.getLitTotalTime()
                + everFurnaceBlockEntity.getLitTimeRemaining();

        // calculate totalCookTimeRemaining
        long totalCookTimeRemaining = (long) (cookStack.getCount() - 1) * everFurnaceBlockEntity.getCookingTotalTime()
                + (everFurnaceBlockEntity.getCookingTotalTime() - everFurnaceBlockEntity.getCookingTimeSpent());

        // determine the max amount of time that can be used before one or both input run out.
        long maxInputTime = Math.min(totalBurnTimeRemaining, totalCookTimeRemaining);

        /*
         * determine  the actual max time that can be applied to processing. ie if elapsed time is < maxInputTime,
         * then only the elapse time can be used.
         */
        long actualAppliedTime = Math.min(deltaTime, maxInputTime);

        // calculate and save the remaining time
        if (deltaTime > actualAppliedTime) {
            blockEntityMixin.setEverfurnace$remainingTime((int) (deltaTime - actualAppliedTime));
        } else {
            blockEntityMixin.setEverfurnace$remainingTime(0);
        }

        if (actualAppliedTime < everFurnaceBlockEntity.getLitTotalTime()) {
            // reduce burn time
            everFurnaceBlockEntity.setLitTimeRemaining(everFurnaceBlockEntity.getLitTimeRemaining()
                - (int) actualAppliedTime);

            if (everFurnaceBlockEntity.getLitTimeRemaining() <= 0) {
                Item fuelItem = fuelStack.getItem();
                // reduce the size of the fuel stack
                fuelStack.decrement(1);
                if (fuelStack.isEmpty()) {
                    everFurnaceBlockEntity.setLitTimeRemaining(0);
                    everFurnaceBlockEntity.getInventory().set(1, fuelItem.getRecipeRemainder());
                } else {
                    everFurnaceBlockEntity.setLitTimeRemaining(
                            everFurnaceBlockEntity.getLitTimeRemaining() - everFurnaceBlockEntity.getLitTotalTime());
                }
            }
        } else {
            int quotient = (int) (Math.floorDivExact(actualAppliedTime, everFurnaceBlockEntity.getLitTotalTime()));
            long remainder = actualAppliedTime % everFurnaceBlockEntity.getLitTotalTime();
            // reduced stack by quotient
            Item fuelItem = fuelStack.getItem();
            fuelStack.decrement(quotient);
            // reduce litTimeRemaining by remainder
            everFurnaceBlockEntity.setLitTimeRemaining(everFurnaceBlockEntity.getLitTimeRemaining() - (int) remainder);
            if (everFurnaceBlockEntity.getLitTimeRemaining() <= 0) {
                // reduce the size of the fuel stack
                fuelStack.decrement(1);
            }
            if (fuelStack.isEmpty()) {
                everFurnaceBlockEntity.setLitTimeRemaining(0);
                everFurnaceBlockEntity.getInventory().set(1, fuelItem.getRecipeRemainder());
            } else {
                everFurnaceBlockEntity.setLitTimeRemaining(everFurnaceBlockEntity.getLitTimeRemaining() + everFurnaceBlockEntity.getLitTotalTime());
            }
        }

        if (actualAppliedTime < everFurnaceBlockEntity.getCookingTotalTime()) {
            // increment cook time
            everFurnaceBlockEntity.setCookingTimeSpent(everFurnaceBlockEntity.getCookingTimeSpent()
                    + (int) actualAppliedTime);
            if (everFurnaceBlockEntity.getCookingTimeSpent() >= everFurnaceBlockEntity.getCookingTotalTime()) {
                if (IEverFurnaceBlockEntity.callCraftRecipe(world.getRegistryManager(), recipeEntry, singleStackRecipeInput, everFurnaceBlockEntity.getInventory(), blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
                }
                if (cookStack.isEmpty()) {
                    everFurnaceBlockEntity.setCookingTimeSpent(0);
                    everFurnaceBlockEntity.setCookingTotalTime(0);
                } else {
                    everFurnaceBlockEntity.setCookingTimeSpent(everFurnaceBlockEntity.getCookingTimeSpent()
                            - everFurnaceBlockEntity.getCookingTotalTime());
                }
            }
        }
        // actual applied time is greater than cook time total,
        // then, need to apply a factor of
        else {
            int quotient = (int) (Math.floorDivExact(actualAppliedTime, everFurnaceBlockEntity.getCookingTotalTime()));
            long remainder = actualAppliedTime % everFurnaceBlockEntity.getCookingTotalTime();
            // reduced stack by quotient
            boolean isSuccessful = false;
            for (int iterations = 0; iterations < quotient; iterations++) {
                isSuccessful |= IEverFurnaceBlockEntity.callCraftRecipe(world.getRegistryManager(), recipeEntry, singleStackRecipeInput, everFurnaceBlockEntity.getInventory(), blockEntity.getMaxCountPerStack());
            }
            // update last recipe
            if (isSuccessful) blockEntity.setLastRecipe(recipeEntry);

            // increment cook time
            everFurnaceBlockEntity.setCookingTimeSpent(everFurnaceBlockEntity.getCookingTimeSpent()
                    + (int) remainder);
            if (everFurnaceBlockEntity.getCookingTimeSpent() >= everFurnaceBlockEntity.getCookingTotalTime()) {
                if (IEverFurnaceBlockEntity.callCraftRecipe(world.getRegistryManager(), recipeEntry, singleStackRecipeInput, everFurnaceBlockEntity.getInventory(), blockEntity.getMaxCountPerStack())) {
                    blockEntity.setLastRecipe(recipeEntry);
                }
                if (cookStack.isEmpty()) {
                    everFurnaceBlockEntity.setCookingTimeSpent(0);
                    everFurnaceBlockEntity.setCookingTotalTime(0);
                } else {
                    everFurnaceBlockEntity.setCookingTimeSpent(everFurnaceBlockEntity.getCookingTimeSpent()
                            - everFurnaceBlockEntity.getCookingTotalTime());
                }
            }
        }

        // reset cooldown time
        blockEntityMixin.setEverfurnace$Cooldown(HopperBlockEntity.TRANSFER_COOLDOWN);

        // update block state
        if(!everFurnaceBlockEntity.callIsBurning()) {
            state = state.with(AbstractFurnaceBlock.LIT, Boolean.valueOf(everFurnaceBlockEntity.callIsBurning()));
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            AbstractFurnaceBlockEntity.markDirty(world, pos, state);
        }
    }

    @Unique
    public void everfurnace$ClearTimes() {
        setEverfurnace$Cooldown(HopperBlockEntity.TRANSFER_COOLDOWN);
        setEverfurnace$remainingTime(0);
    }

    @Unique
    public long getEverfurnace$lastGameTime() {
        return this.everfurnace$lastGameTime;
    }

    @Unique
    public void setEverfurnace$lastGameTime(long gameTime) {
        this.everfurnace$lastGameTime = gameTime;
    }
    @Unique
    public int getEverfurnace$remainingTime() {
        return everfurnace$remainingTime;
    }
    @Unique
    public void setEverfurnace$remainingTime(int everfurnace$remainingTime) {
        this.everfurnace$remainingTime = everfurnace$remainingTime;
    }
    @Unique
    public int getEverfurnace$Cooldown() {
        return everfurnace$Cooldown;
    }
    @Unique
    public void setEverfurnace$Cooldown(int everfurnace$Cooldown) {
        this.everfurnace$Cooldown = everfurnace$Cooldown;
    }
}
