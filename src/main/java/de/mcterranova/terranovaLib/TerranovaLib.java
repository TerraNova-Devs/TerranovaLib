package de.mcterranova.terranovaLib;

import de.mcterranova.terranovaLib.roseGUI.RoseGUIListener;
import de.mcterranova.terranovaLib.vote.VoteListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerranovaLib extends JavaPlugin {

    @Override
    public void onEnable() {

        saveDefaultConfig();
        FileConfiguration config = getConfig();


        Bukkit.getPluginManager().registerEvents(new RoseGUIListener(), this);

        // VoteListener names the classes of NuVotifier, so only with NuVotifier
        if (Bukkit.getPluginManager().isPluginEnabled("Votifier")) {
            Bukkit.getPluginManager().registerEvents(new VoteListener(getLogger()), this);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
