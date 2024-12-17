/*
 * This file is part of EverFurnace.
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

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.FuelRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Created by Mark Gottschling on 12/13/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface IEverFurnaceBlockEntity {

    @Accessor
    int getLitTimeRemaining();
     @Accessor("litTimeRemaining")
    public void setLitTimeRemaining(int litTimeRemaining);

    @Accessor
    int getLitTotalTime();
    @Accessor("litTotalTime")
    public void setLitTotalTime(int litTotalTime);

    @Accessor
    int getCookingTimeSpent();
    @Accessor("cookingTimeSpent")
    public void setCookingTimeSpent(int cookingTimeSpent);

    @Accessor
    int getCookingTotalTime();
    @Accessor
    public void setCookingTotalTime(int cookingTotalTime);

    @Accessor
    DefaultedList<ItemStack> getInventory();

    @Accessor
    ServerRecipeManager.MatchGetter<SingleStackRecipeInput, ? extends AbstractCookingRecipe> getMatchGetter();


    @Invoker
    public boolean callIsBurning();

//    @Invoker
//    public boolean callCanBurn(RegistryAccess pRecipe, @Nullable RecipeHolder<?> pInventory, NonNullList<ItemStack> pMaxStackSize, int p_155008_);

//    @Invoker
//    public boolean callBurn(RegistryAccess pRecipe, @Nullable RecipeHolder<?> pInventory, NonNullList<ItemStack> pMaxStackSize, int p_267157_);

    @Invoker
    public int callGetFuelTime(FuelRegistry fuelRegistry, ItemStack stack);

    @Invoker
    public static boolean callCraftRecipe(DynamicRegistryManager dynamicRegistryManager, @Nullable RecipeEntry<? extends AbstractCookingRecipe> recipe, SingleStackRecipeInput input, DefaultedList<ItemStack> inventory, int maxCount) {
        throw new AssertionError();
    }

    @Invoker
    public static boolean callCanAcceptRecipeOutput(DynamicRegistryManager dynamicRegistryManager, @Nullable RecipeEntry<? extends AbstractCookingRecipe> recipe, SingleStackRecipeInput input, DefaultedList<ItemStack> inventory, int maxCount) {
        throw new AssertionError();
    }
}
