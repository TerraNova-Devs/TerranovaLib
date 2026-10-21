package de.mcterranova.terranovaLib.vote;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A player voted for the server on a vote site. Fired by TerranovaLib for every NuVotifier vote
 * of a player who has been on the server; the player may be offline.
 */
public class VoteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final String service;

    public VoteEvent(OfflinePlayer player, String service) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.service = service;
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    /** The vote site, as it names itself. */
    public String getService() {
        return service;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
