package de.mcterranova.terranovaLib.ghostblocks;

import de.mcterranova.terranovaLib.utils.PluginHooks;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Client-side blocks per player, switched by BetonQuest actions. Groups in the ghostblocks folder.
 */
public final class GhostBlocks {

    private final JavaPlugin plugin;

    private final GhostBlockService service;

    private final File groupFolder;

    private GhostBlocks(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.service = new GhostBlockService(plugin);
        this.groupFolder = new File(plugin.getDataFolder(), "ghostblocks");
    }

    /**
     * Loads the groups and registers the listeners, the BetonQuest action and condition and
     * /ghostblocks.
     */
    public static void enable(final JavaPlugin plugin) {
        final GhostBlocks feature = new GhostBlocks(plugin);
        if (!feature.groupFolder.exists()) {
            plugin.saveResource("ghostblocks/example.yml", false);
        }
        feature.loadGroups(null);
        plugin.getServer().getPluginManager().registerEvents(feature.service, plugin);
        // BetonQuest may enable after the lib; it takes integrations until all plugins are enabled
        PluginHooks.whenEnabled(plugin, "BetonQuest", () -> {
            if (BetonQuestHook.register(plugin, feature.service)) {
                plugin.getLogger().info("BetonQuest action and condition '" + BetonQuestHook.NAME + "' registered.");
            }
        });
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("ghostblocks", "Manages ghost block groups.", List.of(),
                        feature.new GhostCommand()));
    }

    private void loadGroups(@Nullable final CommandSender reportTo) {
        final Map<String, GhostGroup> groups = new HashMap<>();
        final File[] files = groupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        for (final File file : files == null ? new File[0] : files) {
            final String id = file.getName().substring(0, file.getName().length() - ".yml".length());
            try {
                final YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file);
                groups.put(id, GhostGroup.parse(id, yaml));
            } catch (final IOException | InvalidConfigurationException | IllegalArgumentException e) {
                final String message = file.getName() + ": " + e.getMessage();
                plugin.getLogger().warning(message);
                if (reportTo != null) {
                    reportTo.sendRichMessage("<red>" + message);
                }
            }
        }
        service.setGroups(groups);
        if (reportTo != null) {
            reportTo.sendRichMessage("<green>" + groups.size() + " group(s) loaded.");
        }
    }

    /**
     * Saves the non-air blocks in a box as a new group, read on the calling player's thread.
     */
    private void capture(final Player player, final String id, final String[] coordinates) {
        if (!GhostGroup.ID.matcher(id).matches()) {
            player.sendRichMessage("<red>Group ids may only use a-z, 0-9, _ and -.");
            return;
        }
        final File file = new File(groupFolder, id + ".yml");
        if (file.exists()) {
            player.sendRichMessage("<red>Group '" + id + "' exists; delete " + file.getName() + " first.");
            return;
        }
        final int[] c = new int[6];
        try {
            for (int i = 0; i < 6; i++) {
                c[i] = Integer.parseInt(coordinates[i]);
            }
        } catch (final NumberFormatException e) {
            player.sendRichMessage("<red>Coordinates must be whole numbers.");
            return;
        }
        final long volume = (long) (Math.abs(c[3] - c[0]) + 1) * (Math.abs(c[4] - c[1]) + 1) * (Math.abs(c[5] - c[2]) + 1);
        if (volume > GhostGroup.MAX_BLOCKS) {
            player.sendRichMessage("<red>That box has " + volume + " blocks; at most " + GhostGroup.MAX_BLOCKS + ".");
            return;
        }
        final World world = player.getWorld();
        final List<String> blocks = new ArrayList<>();
        try {
            for (int x = Math.min(c[0], c[3]); x <= Math.max(c[0], c[3]); x++) {
                for (int y = Math.min(c[1], c[4]); y <= Math.max(c[1], c[4]); y++) {
                    for (int z = Math.min(c[2], c[5]); z <= Math.max(c[2], c[5]); z++) {
                        final BlockData data = world.getBlockData(x, y, z);
                        if (!data.getMaterial().isAir()) {
                            blocks.add(GhostGroup.line(x, y, z, data));
                        }
                    }
                }
            }
            GhostGroup.toYaml(world.getName(), false, blocks).save(file);
        } catch (final IOException | IllegalStateException e) {
            // IllegalStateException: on Folia, part of the box belongs to another region thread.
            player.sendRichMessage("<red>Capture failed: " + e.getMessage());
            return;
        }
        player.sendRichMessage("<green>Saved " + blocks.size() + " block(s) as '" + id
                + "'. Remove the real blocks, then show the group with a quest or /ghostblocks show.");
        loadGroups(player);
    }

    private final class GhostCommand implements BasicCommand {

        @Override
        public void execute(final CommandSourceStack source, final String[] args) {
            final CommandSender sender = source.getSender();
            switch (args.length == 0 ? "" : args[0]) {
                case "reload" -> loadGroups(sender);
                case "list" -> service.groups().values().stream().sorted((a, b) -> a.id().compareTo(b.id()))
                        .forEach(group -> sender.sendRichMessage("<gray>" + group.id() + " <dark_gray>" + group.world() + ", "
                                + group.byChunk().values().stream().mapToInt(Map::size).sum() + " block(s)"
                                + (group.defaultVisible() ? ", visible by default" : "")));
                case "show", "hide" -> toggle(sender, args);
                case "capture" -> {
                    if (!(source.getExecutor() instanceof final Player player) || args.length != 8) {
                        sender.sendRichMessage("<gray>As a player: /ghostblocks capture <group> <x1> <y1> <z1> <x2> <y2> <z2>");
                        return;
                    }
                    capture(player, args[1], Arrays.copyOfRange(args, 2, 8));
                }
                default -> sender.sendRichMessage("<gray>/ghostblocks reload | list | show <player> <group>"
                        + " | hide <player> <group> | capture <group> <x1> <y1> <z1> <x2> <y2> <z2>");
            }
        }

        private void toggle(final CommandSender sender, final String[] args) {
            final Player player = args.length == 3 ? Bukkit.getPlayerExact(args[1]) : null;
            final GhostGroup group = args.length == 3 ? service.group(args[2]) : null;
            if (player == null || group == null) {
                sender.sendRichMessage("<red>/ghostblocks " + args[0] + " <online player> <group>");
                return;
            }
            final boolean visible = "show".equals(args[0]);
            player.getScheduler().run(plugin, task -> service.setVisible(player, group, visible), null);
            sender.sendRichMessage("<green>'" + group.id() + "' is now " + (visible ? "shown to " : "hidden from ") + player.getName() + ".");
        }

        @Override
        public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
            if (args.length <= 1) {
                return List.of("reload", "list", "show", "hide", "capture");
            }
            if (args.length == 3 && ("show".equals(args[0]) || "hide".equals(args[0]))) {
                return new TreeSet<>(service.groups().keySet());
            }
            return List.of();
        }

        @Override
        public String permission() {
            return "ghostblocks.admin";
        }
    }
}
