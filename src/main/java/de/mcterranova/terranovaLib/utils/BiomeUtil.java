package de.mcterranova.terranovaLib.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

import org.bukkit.Bukkit;
import org.bukkit.Location; // Adjust based on your server version
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

public class BiomeUtil {

    //NMS Biome Class for Handling custom Biomes e.g. from Datapacks

    private final Logger logger;

    public BiomeUtil(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    public Biome getBiome(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location or world cannot be null.");
        }

        // Convert Bukkit World to NMS World
        ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();
        // Get the biome at the specific BlockPos
        BlockPos blockPos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());

        // Access the registry of biomes to resolve custom biomes from datapacks
        Biome biome = nmsWorld.getBiome(blockPos).value();
        return biome;
    }

    public void listAllBiomes(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location or world cannot be null.");
        }

        ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();
        var registryAccess = nmsWorld.registryAccess();
        var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        biomeRegistry.entrySet().forEach(entry -> {
            ResourceKey<Biome> biomeKey = entry.getKey();
            Biome biome = entry.getValue();
            logger.info("Biome Key: " + biomeKey.location() + " Biome: " + biome);
        });
    }

    public void checkResourceKey(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location or world cannot be null.");
        }

        ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();
        BlockPos blockPos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        ResourceKey<Biome> biomeKey = nmsWorld.getBiome(blockPos).unwrapKey().orElse(null);
        if (biomeKey != null) {
            String namespace = biomeKey.location().getNamespace();
            String biomeName = biomeKey.location().getPath();
            logger.info("Biome namespace: " + namespace + ", Biome name: " + biomeName);
        } else {
            logger.warning("Could not find biome key at the specified location.");
        }
    }

    public void runAsyncBiomeCheck(JavaPlugin plugin, Location location) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Biome biome = getBiome(location);
                Bukkit.getScheduler().runTask(plugin, () -> logger.info("Biome at location: " + biome));
            } catch (IllegalArgumentException e) {
                logger.severe("Error while getting biome: " + e.getMessage());
            }
        });
    }
}

