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

import mod.gottsch.fabric.everfurnace.core.CatchupEffectHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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
 * Adds offline catch-up to vanilla campfires (and soul campfires).
 *
 * <p>The vanilla {@code litServerTick} ticker only runs for chunks that are actually
 * ticking — i.e. within a player's simulation distance — and only while the campfire
 * is lit (otherwise {@code cooldownTick} is registered instead).  Proximity gating and
 * the "is lit" check are therefore both implicit: while no player is near the ticker
 * never fires and the gap accumulates in {@code everfurnace$lastGameTime}; on the first
 * tick after a player returns the catch-up is applied.
 *
 * <p>Rather than re-implement recipe assembly / drop / slot-clear, this inject simply
 * advances each slot's progress up to its total.  A slot pushed to its total is
 * completed by the vanilla body's own {@code ++}/threshold check that runs immediately
 * after.  Each slot holds one item and is never restocked, so output is bounded to one
 * item per slot.
 *
 * <p>No config, no particles — matching the minimal Fabric feature set.
 *
 * Created by Mark Gottschling on 5/29/2026
 */
@Mixin(CampfireBlockEntity.class)
public abstract class ModCampfireBlockEntityMixin {

    @Unique private static final String LAST_GAME_TIME_TAG  = "everfurnace_lastGameTime";
    @Unique private static final String NBT_VERSION_TAG     = "everfurnace_version";
    @Unique private static final int    CURRENT_NBT_VERSION = 1;

    @Unique private static final int MIN_DELTA_THRESHOLD = 20;
    @Unique private static final int MAX_CATCHUP_TICKS   = 24_000;

    @Unique private long everfurnace$lastGameTime;

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void everfurnace$onSave(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        nbt.putInt (NBT_VERSION_TAG,    CURRENT_NBT_VERSION);
        nbt.putLong(LAST_GAME_TIME_TAG, this.everfurnace$lastGameTime);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void everfurnace$onLoad(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        this.everfurnace$lastGameTime = nbt.getLong(LAST_GAME_TIME_TAG);
    }

    /**
     * HEAD inject into the lit-server tick.  Yarn 1.20.1 name: {@code litServerTick}.
     * If the name is wrong the Mixin framework will report a clear error at load time.
     */
    @Inject(method = "litServerTick", at = @At("HEAD"))
    private static void everfurnace$onLitServerTick(World world, BlockPos pos, BlockState state,
                                                     CampfireBlockEntity blockEntity, CallbackInfo ci) {

        ModCampfireBlockEntityMixin  mixin    = (ModCampfireBlockEntityMixin)(Object) blockEntity;
        IModCampfireBlockEntityMixin accessor = (IModCampfireBlockEntityMixin)(Object) blockEntity;

        long currentGameTime   = world.getTime();
        long localLastGameTime = mixin.everfurnace$lastGameTime;

        mixin.everfurnace$lastGameTime = currentGameTime;

        if (localLastGameTime == 0L) return;

        long deltaTime = currentGameTime - localLastGameTime;
        if (deltaTime < MIN_DELTA_THRESHOLD) return;

        deltaTime = Math.min(deltaTime, MAX_CATCHUP_TICKS);

        DefaultedList<ItemStack> items  = blockEntity.getItemsBeingCooked();
        int[] cookingTimes      = accessor.getCookingTimes();
        int[] cookingTotalTimes = accessor.getCookingTotalTimes();

        boolean anyCompleted = false;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) continue;

            int total = cookingTotalTimes[i];
            if (total <= 0) continue;

            int remaining = total - cookingTimes[i];
            if (deltaTime >= remaining) {
                cookingTimes[i] = total;
                anyCompleted = true;
            } else {
                cookingTimes[i] += (int) deltaTime;
            }
        }

        if (anyCompleted && world instanceof ServerWorld serverWorld) {
            CatchupEffectHelper.spawnCampfireEffects(serverWorld, pos);
        }
    }
}
