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

import net.minecraft.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor interface for the private cooking arrays on {@link CampfireBlockEntity}.
 *
 * <p>Yarn 1.20.1 field names: {@code cookingTimes} (progress per slot),
 * {@code cookingTotalTimes} (recipe total per slot).  The item list is exposed via
 * the public {@code getItemsBeingCooked()} method and needs no accessor.
 *
 * Created by Mark Gottschling on 5/29/2026
 */
@Mixin(CampfireBlockEntity.class)
public interface IModCampfireBlockEntityMixin {

    @Accessor
    int[] getCookingTimes();

    @Accessor
    int[] getCookingTotalTimes();
}
