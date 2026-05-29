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

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor / invoker interface for private fields and methods on
 * {@link AbstractFurnaceBlockEntity}.  Replaces the access widener so no widener
 * entries are needed for the furnace.
 *
 * <p>Field names are the yarn 1.21.1 names confirmed by the mappings jar.
 * Method names follow Mixin's "callXxx → xxx" convention so the explicit
 * {@code @Invoker("name")} override is only needed where the convention would
 * produce a different result.
 *
 * Created by Mark Gottschling on 5/29/2026
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface IModFurnaceBlockEntityMixin {

    // ---- inventory ----------------------------------------------------------

    @Accessor
    DefaultedList<ItemStack> getInventory();

    // ---- fuel / burn --------------------------------------------------------

    @Accessor
    int getBurnTime();
    @Accessor
    void setBurnTime(int burnTime);

    @Accessor
    int getFuelTime();

    // ---- cook ---------------------------------------------------------------

    @Accessor
    int getCookTime();
    @Accessor
    void setCookTime(int cookTime);

    @Accessor
    int getCookTimeTotal();
    @Accessor
    void setCookTimeTotal(int cookTimeTotal);

    // ---- recipe matching ----------------------------------------------------

    @SuppressWarnings("rawtypes")
    @Accessor("matchGetter")
    RecipeManager.MatchGetter getMatchGetter();

    // ---- invokers -----------------------------------------------------------

    @Invoker
    boolean callIsBurning();

    @Invoker("canAcceptRecipeOutput")
    static boolean callCanAcceptRecipeOutput(DynamicRegistryManager registryManager,
                                              RecipeEntry<?> recipe,
                                              DefaultedList<ItemStack> inventory,
                                              int maxCount) {
        throw new AssertionError();
    }

    @Invoker("craftRecipe")
    static boolean callCraftRecipe(DynamicRegistryManager registryManager,
                                    RecipeEntry<?> recipe,
                                    DefaultedList<ItemStack> inventory,
                                    int maxCount) {
        throw new AssertionError();
    }
}
