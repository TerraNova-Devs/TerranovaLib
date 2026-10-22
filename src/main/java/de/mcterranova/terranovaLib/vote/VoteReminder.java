package de.mcterranova.terranovaLib.vote;

import de.mcterranova.terranovaLib.utils.Chat;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 20 minutes after joining, reminds a player who has not voted today to vote, at most once a day.
 * Days are German days, like the reset of the vote sites at midnight.
 */
public class VoteReminder implements Listener {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final long DELAY_TICKS = 20 * 60 * 20;

    private final JavaPlugin plugin;
    private final File file;
    // <uuid>.voted and <uuid>.reminded: the last day the player voted or was reminded
    private final YamlConfiguration days;

    public VoteReminder(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "votes.yml");
        this.days = YamlConfiguration.loadConfiguration(file);
    }

    @EventHandler
    public void onVote(VoteEvent event) {
        mark(event.getPlayer().getUniqueId(), "voted");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // false after leaving, also after leaving and joining again; that join has its own 20 minutes
            if (!player.isConnected()) return;
            String today = LocalDate.now(ZONE).toString();
            String id = player.getUniqueId().toString();
            if (today.equals(days.getString(id + ".voted")) || today.equals(days.getString(id + ".reminded"))) return;
            mark(player.getUniqueId(), "reminded");
            // without a default here, so a config.yml without vote.reminder takes the one from the jar
            player.sendMessage(Chat.stringToComponent(plugin.getConfig().getString("vote.reminder")));
        }, DELAY_TICKS);
    }

    private void mark(UUID player, String key) {
        days.set(player + "." + key, LocalDate.now(ZONE).toString());
        try {
            days.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "votes.yml nicht gespeichert", e);
        }
    }
}
