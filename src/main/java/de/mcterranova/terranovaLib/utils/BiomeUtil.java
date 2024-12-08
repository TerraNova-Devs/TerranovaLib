package de.mcterranova.terranovaLib.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

public class BiomeUtil {

    //NMS Biome Class for Handling custom Biomes e.g. from Datapacks

    public BiomeUtil() {

    }

    public static boolean isBiomeInList(Location location, List<String> biomeNames) {
        if (location == null || location.getWorld() == null || biomeNames == null || biomeNames.isEmpty()) {
            throw new IllegalArgumentException("Location, world, or biome names cannot be null or empty.");
        }

        ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();
        BlockPos blockPos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        ResourceKey<Biome> biomeKey = nmsWorld.getBiome(blockPos).unwrapKey().orElse(null);

        if (biomeKey != null) {
            String fullBiomeName = biomeKey.location().toString();
            return biomeNames.stream().anyMatch(biomeName -> biomeName.equalsIgnoreCase(fullBiomeName));
        }
        return false;
    }

}

