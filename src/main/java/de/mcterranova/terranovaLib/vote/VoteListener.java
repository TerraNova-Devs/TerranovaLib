package de.mcterranova.terranovaLib.vote;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

/** Turns the votes of NuVotifier into a {@link VoteEvent} for the other TerraNova plugins. */
public class VoteListener implements Listener {
    private final Logger logger;

    public VoteListener(Logger logger) {
        this.logger = logger;
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        // vote sites take any name, only players who have been on the server count
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(vote.getUsername());
        if (player == null) {
            logger.info("Vote von " + vote.getServiceName() + " für unbekannten Spieler " + vote.getUsername() + " ignoriert.");
            return;
        }
        Bukkit.getPluginManager().callEvent(new VoteEvent(player, vote.getServiceName()));
    }
}
