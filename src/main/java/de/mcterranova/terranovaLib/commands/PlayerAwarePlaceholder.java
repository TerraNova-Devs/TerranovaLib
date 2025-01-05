package de.mcterranova.terranovaLib.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A placeholder can provide a list of possible tab completions,
 * given a CommandSender (which may or may not be a Player).
 */
@FunctionalInterface
public interface PlayerAwarePlaceholder {
    /**
     * Return a list of possible completions, possibly depending
     * on the sender (and thus their UUID).
     *
     * @param sender the CommandSender using tab complete
     * @return a list of possible completions
     */
    List<String> getSuggestions(CommandSender sender);

    /**
     * Utility method to wrap a plain Supplier<List<String>> as a PlayerAwarePlaceholder
     */
    static PlayerAwarePlaceholder ofStatic(java.util.function.Supplier<List<String>> supplier) {
        return sender -> supplier.get();
    }

    /**
     * Utility method to wrap a Function<UUID, List<String>> as a PlayerAwarePlaceholder.
     * If sender is not a player, returns an empty list.
     */
    static PlayerAwarePlaceholder ofPlayerFunction(java.util.function.Function<UUID, List<String>> function) {
        return sender -> {
            if (sender instanceof Player player) {
                return function.apply(player.getUniqueId());
            }
            return Collections.emptyList();
        };
    }
}
