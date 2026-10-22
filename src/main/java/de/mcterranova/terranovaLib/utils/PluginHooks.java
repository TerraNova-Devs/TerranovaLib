package de.mcterranova.terranovaLib.utils;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Runs code once another plugin is enabled, without a load order on it.
 * <p>
 * A softdepend would wait for the other plugin too, but it also forces the order, and the lib has to
 * load early: BountyfulSeas needs TerranovaLib before it and Nexo after it, and BetonQuest loads after
 * Nexo. A softdepend on BetonQuest closed that circle, and the lib then started first anyway.
 */
public final class PluginHooks {

    private PluginHooks() {
    }

    /** Runs {@code hook} now if {@code name} is enabled, otherwise right after it enables. */
    public static void whenEnabled(Plugin owner, String name, Runnable hook) {
        if (Bukkit.getPluginManager().isPluginEnabled(name)) {
            run(owner, name, hook);
            return;
        }
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if (event.getPlugin().getName().equals(name)) {
                    HandlerList.unregisterAll(this);
                    run(owner, name, hook);
                }
            }
        }, owner);
    }

    // a hook that fails must not take down the lib and everything that depends on it
    private static void run(Plugin owner, String name, Runnable hook) {
        try {
            hook.run();
        } catch (Exception | LinkageError e) {
            owner.getLogger().log(Level.SEVERE, "Hook into " + name + " failed", e);
        }
    }
}
