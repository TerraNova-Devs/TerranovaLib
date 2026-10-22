package de.mcterranova.terranovaLib;

import de.mcterranova.terranovaLib.roseGUI.RoseGUIListener;
import de.mcterranova.terranovaLib.utils.Chat;
import de.mcterranova.terranovaLib.vote.VoteListener;
import de.mcterranova.terranovaLib.vote.VoteReminder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class TerranovaLib extends JavaPlugin {

    @Override
    public void onEnable() {

        saveDefaultConfig();
        FileConfiguration config = getConfig();


        Bukkit.getPluginManager().registerEvents(new RoseGUIListener(), this);

        // VoteListener names the classes of NuVotifier, so only with NuVotifier;
        // the reminder only where the votes arrive, elsewhere it would not know who voted
        if (Bukkit.getPluginManager().isPluginEnabled("Votifier")) {
            Bukkit.getPluginManager().registerEvents(new VoteListener(getLogger()), this);
            Bukkit.getPluginManager().registerEvents(new VoteReminder(this), this);
        }

        // /vote shows the lines of vote.message from the config, for everyone
        Objects.requireNonNull(getCommand("vote")).setExecutor((sender, command, label, args) -> {
            getConfig().getStringList("vote.message").forEach(line -> sender.sendMessage(Chat.stringToComponent(line)));
            return true;
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
