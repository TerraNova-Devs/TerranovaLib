package de.mcterranova.terranovaLib;

import de.mcterranova.terranovaLib.crops.RicePlanting;
import de.mcterranova.terranovaLib.dialoguehud.DialogueHud;
import de.mcterranova.terranovaLib.ghostblocks.GhostBlocks;
import de.mcterranova.terranovaLib.npcs.NpcMapLayer;
import de.mcterranova.terranovaLib.npcs.Npcs;
import de.mcterranova.terranovaLib.roseGUI.RoseGUIListener;
import de.mcterranova.terranovaLib.utils.Chat;
import de.mcterranova.terranovaLib.utils.PluginHooks;
import de.mcterranova.terranovaLib.vote.VoteListener;
import de.mcterranova.terranovaLib.vote.VoteReminder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class TerranovaLib extends JavaPlugin {

    private DialogueHud hud;

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

        // blocks only some players see, switched by quests; a failure here must not take down the lib
        // and with it every plugin that depends on it
        try {
            GhostBlocks.enable(this);
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.SEVERE, "Ghost blocks stay off", e);
        }

        // NPCs defined as code; Npcs names the classes of Citizens, so only with Citizens
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                Npcs.enable(this);
            } catch (Exception | LinkageError e) {
                getLogger().log(Level.SEVERE, "NPCs stay off", e);
            }
            // NpcMapLayer names the classes of HeroicMap, so only with HeroicMap
            if (Bukkit.getPluginManager().isPluginEnabled("HeroicMap")) {
                try {
                    NpcMapLayer.enable(this);
                } catch (Exception | LinkageError e) {
                    getLogger().log(Level.SEVERE, "NPC map layer stays off", e);
                }
            }
        }

        // rice only in shallow water; RicePlanting names an event of Nexo, which loads after the lib (BountyfulSeas)
        PluginHooks.whenEnabled(this, "Nexo", () -> Bukkit.getPluginManager().registerEvents(new RicePlanting(this), this));

        // BetonQuest conversations above the hotbar; DialogueHud names the classes of BetonQuest, so
        // only where BetonQuest is installed. BetonQuest enables after the lib, the HUD hooks in then.
        if (Bukkit.getPluginManager().getPlugin("BetonQuest") != null) {
            try {
                hud = DialogueHud.enable(this);
            } catch (Exception | LinkageError e) {
                getLogger().log(Level.SEVERE, "Dialogue HUD stays off", e);
            }
        }
    }

    @Override
    public void onDisable() {
        // takes the dialogues off the players' screens
        if (hud != null) {
            hud.disable();
        }
    }
}
