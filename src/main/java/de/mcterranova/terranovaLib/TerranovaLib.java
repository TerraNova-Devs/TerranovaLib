package de.mcterranova.terranovaLib;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;

public final class TerranovaLib extends JavaPlugin {
    private static InfluxDB influxDB;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        String url = config.getString("influxdb.url");
        String database = config.getString("influxdb.database");
        String username = config.getString("influxdb.username");
        String password = config.getString("influxdb.password");

        influxDB = InfluxDBFactory.connect(url, username, password);
        influxDB.setDatabase(database);
    }

    public static InfluxDB getInfluxDB() {
        return influxDB;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
