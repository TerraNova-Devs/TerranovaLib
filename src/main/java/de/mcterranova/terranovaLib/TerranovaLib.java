package de.mcterranova.terranovaLib;

import de.mcterranova.terranovaLib.roseGUI.RoseGUIListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerranovaLib extends JavaPlugin {

    @Override
    public void onEnable() {

        saveDefaultConfig();
        FileConfiguration config = getConfig();


        Bukkit.getPluginManager().registerEvents(new RoseGUIListener(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
