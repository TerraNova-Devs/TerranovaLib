package de.mcterranova.terranovaLib;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import de.mcterranova.terranovaLib.silver.Silver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public final class TerranovaLib extends JavaPlugin {
    private static InfluxDBClient influxDBClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        String url = config.getString("influxdb.url");
        String token = config.getString("influxdb.token");
        String org = config.getString("influxdb.org");
        String bucket = config.getString("influxdb.bucket");

        influxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
        Silver.setInfluxDB(influxDBClient);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
