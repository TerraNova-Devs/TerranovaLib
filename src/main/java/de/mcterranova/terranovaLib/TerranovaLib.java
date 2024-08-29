package de.mcterranova.terranovaLib;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import de.mcterranova.terranovaLib.silver.Silver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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
