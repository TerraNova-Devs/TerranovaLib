package de.mcterranova.terranovaLib.npcs;

import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.CitizensReloadEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Citizens NPCs defined in YAML files, with permanent ids that BetonQuest can refer to. Settings under
 * npcs in config.yml, definitions in the npcs folder.
 */
public final class Npcs implements Listener {

    private final JavaPlugin plugin;

    private final NpcReconciler npcs;

    private final File definitionFolder;

    private Npcs(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.npcs = new NpcReconciler(plugin);
        this.definitionFolder = new File(plugin.getDataFolder(), "npcs");
    }

    /**
     * Registers the BetonQuest NPC type, the Citizens listeners and /tnpc. Needs Citizens.
     */
    public static void enable(final JavaPlugin plugin) {
        final Npcs feature = new Npcs(plugin);
        if (!feature.definitionFolder.exists()) {
            plugin.saveResource("npcs/example.yml", false);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("BetonQuest") && BetonQuestHook.register(plugin, feature.npcs)) {
            plugin.getLogger().info("BetonQuest NPC type '" + BetonQuestHook.TYPE + "' registered.");
        }
        plugin.getServer().getPluginManager().registerEvents(feature, plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("tnpc", "Manages NPCs defined in TerranovaLib/npcs.", List.of(),
                        feature.new NpcCommand()));

        // Citizens loads its saved NPCs a tick after the server starts and announces it with
        // CitizensEnableEvent. If they are already there, this plugin was enabled late.
        if (CitizensAPI.getNPCRegistry().iterator().hasNext()) {
            feature.reconcile(null);
        }
    }

    @EventHandler
    public void onCitizensEnable(final CitizensEnableEvent event) {
        reconcile(null);
    }

    @EventHandler
    public void onCitizensReload(final CitizensReloadEvent event) {
        reconcile(null);
    }

    /**
     * Loads every definition file and brings Citizens in line with it.
     */
    private void reconcile(@Nullable final CommandSender reportTo) {
        final List<String> errors = new ArrayList<>();
        final List<NpcDefinition> definitions = loadDefinitions(errors);
        errors.forEach(error -> plugin.getLogger().warning(error));
        if (reportTo != null) {
            errors.forEach(error -> reportTo.sendRichMessage("<red>" + error));
        }
        // A run with errors, or without a single definition (folder missing or not moved yet),
        // looks the same as "delete them all"; it never removes NPCs.
        final boolean removeOrphans = plugin.getConfig().getBoolean("npcs.remove-orphans")
                && errors.isEmpty() && !definitions.isEmpty();
        if (!errors.isEmpty()) {
            plugin.getLogger().warning("Definition errors: NPCs without a definition are kept this time.");
        } else if (definitions.isEmpty()) {
            plugin.getLogger().warning("No NPC definitions in " + definitionFolder + ": NPCs without a definition are kept.");
        }
        npcs.reconcile(definitions, removeOrphans);
        if (reportTo != null) {
            reportTo.sendRichMessage("<green>" + definitions.size() + " NPC(s) in sync.");
        }
    }

    private List<NpcDefinition> loadDefinitions(final List<String> errors) {
        final List<NpcDefinition> definitions = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final Set<UUID> replaced = new HashSet<>();
        final List<Path> files;
        try (Stream<Path> walk = Files.walk(definitionFolder.toPath())) {
            files = walk.filter(path -> path.toString().endsWith(".yml")).sorted().toList();
        } catch (final IOException e) {
            errors.add("Cannot read " + definitionFolder + ": " + e.getMessage());
            return definitions;
        }
        for (final Path file : files) {
            final YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file.toFile());
            } catch (final IOException | InvalidConfigurationException e) {
                errors.add(file.getFileName() + ": " + e.getMessage());
                continue;
            }
            for (final String id : yaml.getKeys(false)) {
                final ConfigurationSection section = yaml.getConfigurationSection(id);
                if (section == null) {
                    errors.add(file.getFileName() + ": '" + id + "' is not a section");
                    continue;
                }
                if (!seen.add(id)) {
                    errors.add(file.getFileName() + ": id '" + id + "' is defined twice");
                    continue;
                }
                try {
                    final NpcDefinition definition = NpcDefinition.parse(id, section);
                    if (definition.replaces() != null && !replaced.add(definition.replaces())) {
                        errors.add(file.getFileName() + ": '" + id + "' replaces " + definition.replaces()
                                + ", which another definition replaces already");
                        continue;
                    }
                    definitions.add(definition);
                } catch (final IllegalArgumentException e) {
                    errors.add(file.getFileName() + ": " + e.getMessage());
                }
            }
        }
        return definitions;
    }

    /**
     * Takes an NPC made in game under management. If a file already defines the id, the NPC is only
     * tagged and then brought in line with that definition. Otherwise its current state is written to
     * npcs/adopted.yml, with {@code replaces} set to its UUID so other servers holding the same NPC
     * (e.g. Prod) take it over on their own.
     */
    private void adopt(final CommandSender sender, final String citizensId, final String id) {
        if (!NpcDefinition.ID.matcher(id).matches()) {
            sender.sendRichMessage("<red>Ids may only use a-z, 0-9, _ and -.");
            return;
        }
        final NPC npc;
        try {
            npc = CitizensAPI.getNPCRegistry().getById(Integer.parseInt(citizensId));
        } catch (final NumberFormatException e) {
            sender.sendRichMessage("<red>Citizens ids are numbers.");
            return;
        }
        if (npc == null || npc.getStoredLocation() == null) {
            sender.sendRichMessage("<red>No placed Citizens NPC #" + citizensId + ".");
            return;
        }
        if (npc.data().has(NpcReconciler.KEY) || npcs.get(id) != null) {
            sender.sendRichMessage("<red>That NPC or that id is already managed.");
            return;
        }
        if (loadDefinitions(new ArrayList<>()).stream().anyMatch(definition -> definition.id().equals(id))) {
            npcs.adopt(npc, id);
            reconcile(sender);
            sender.sendRichMessage("<green>NPC #" + citizensId + " is now '" + id + "' as defined in its file."
                    + " Add \"replaces: " + npc.getUniqueId() + "\" there so other servers do this on their own.");
            return;
        }
        final File file = new File(definitionFolder, "adopted.yml");
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection section = yaml.createSection(id);
        section.set("name", npc.getRawName());
        section.set("type", npc.getOrAddTrait(MobType.class).getType().name());
        section.set("location", NpcDefinition.format(npc.getStoredLocation()));
        final SkinTrait skin = npc.getTraitNullable(SkinTrait.class);
        if (skin != null && skin.getTexture() != null && skin.getSignature() != null) {
            section.set("skin.texture", skin.getTexture());
            section.set("skin.signature", skin.getSignature());
        } else if (skin != null && skin.getSkinName() != null) {
            section.set("skin.name", skin.getSkinName());
        }
        final LookClose lookClose = npc.getTraitNullable(LookClose.class);
        section.set("look-close", lookClose != null && lookClose.isEnabled());
        section.set("protected", npc.isProtected());
        section.set("replaces", npc.getUniqueId().toString());
        try {
            yaml.save(file);
        } catch (final IOException e) {
            sender.sendRichMessage("<red>Cannot write " + file + ": " + e.getMessage());
            return;
        }
        npcs.adopt(npc, id);
        sender.sendRichMessage("<green>NPC #" + citizensId + " is now '" + id + "', written to npcs/adopted.yml."
                + " Refer to it as \"" + BetonQuestHook.TYPE + " " + id + "\".");
    }

    private final class NpcCommand implements BasicCommand {

        @Override
        public void execute(final CommandSourceStack source, final String[] args) {
            final CommandSender sender = source.getSender();
            switch (args.length == 0 ? "" : args[0]) {
                case "reload" -> reconcile(sender);
                case "list" -> {
                    final Map<String, NPC> sorted = new TreeMap<>(npcs.all());
                    sorted.forEach((id, npc) -> sender.sendRichMessage("<gray>" + id + " <dark_gray>#" + npc.getId()
                            + (npc.isSpawned() ? " <green>spawned" : " <red>not spawned")));
                    sender.sendRichMessage("<gray>" + sorted.size() + " managed NPC(s).");
                }
                case "adopt" -> {
                    if (args.length != 3) {
                        sender.sendRichMessage("<gray>/tnpc adopt <citizens id> <new id>");
                        return;
                    }
                    adopt(sender, args[1], args[2]);
                }
                default -> sender.sendRichMessage("<gray>/tnpc reload | list | adopt <citizens id> <new id>");
            }
        }

        @Override
        public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
            return args.length <= 1 ? List.of("reload", "list", "adopt") : List.of();
        }

        @Override
        public String permission() {
            return "terranovanpcs.admin";
        }
    }
}
