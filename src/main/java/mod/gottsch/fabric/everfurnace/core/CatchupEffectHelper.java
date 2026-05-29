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
package mod.gottsch.fabric.everfurnace.core;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Server-side catch-up visual and chat effects.
 *
 * <p>All methods use vanilla packets only (particle, sound, chat), so no EverFurnace
 * client mod is required.  Particles and sounds are dispatched by {@link ServerWorld}
 * to every Minecraft client within range; chat messages are sent with
 * {@link Text#literal} so the client needs no lang file.
 *
 * Created by Mark Gottschling on 5/29/2026
 */
public class CatchupEffectHelper {

    /** Radius (blocks) within which nearby players receive furnace notifications. */
    public static final double NOTIFY_RADIUS = 32.0;

    // -------------------------------------------------------------------------
    // Furnace effects
    // -------------------------------------------------------------------------

    /**
     * Spawns a small burst of flame + smoke particles and a fire-crackle sound
     * at a furnace position.  Safe to call on the server; clients receive vanilla
     * packets and need no mod.
     */
    public static void spawnFurnaceEffects(ServerWorld world, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 6, 0.25, 0.20, 0.25, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, cx, cy + 0.5, cz, 8, 0.30, 0.30, 0.30, 0.01);
        world.playSound(null, cx, cy, cz,
                SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * Sends a plain-text chat notification to every player within
     * {@value #NOTIFY_RADIUS} blocks of {@code pos}.
     *
     * <p>Uses {@link Text#literal} so the message is readable on any vanilla client.
     */
    public static void notifyNearbyPlayers(ServerWorld world, BlockPos pos, int itemsCooked) {
        String msg = itemsCooked == 1
                ? "Your furnace cooked 1 item while you were away!"
                : "Your furnace cooked " + itemsCooked + " items while you were away!";
        Text text = Text.literal(msg);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        world.getPlayers().stream()
                .filter(p -> p.squaredDistanceTo(cx, cy, cz) <= NOTIFY_RADIUS * NOTIFY_RADIUS)
                .forEach(p -> p.sendMessage(text));
    }

    // -------------------------------------------------------------------------
    // Campfire effects
    // -------------------------------------------------------------------------

    /**
     * Spawns campfire smoke + flame particles and a crackle sound at a campfire
     * position.  No client mod required.
     */
    public static void spawnCampfireEffects(ServerWorld world, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy + 0.5, cz, 6, 0.30, 0.30, 0.30, 0.01);
        world.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 3, 0.20, 0.10, 0.20, 0.01);
        world.playSound(null, cx, cy, cz,
                SoundEvents.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }
}
