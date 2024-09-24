package de.mcterranova.terranovaLib.utils;

import de.mcterranova.terranovaLib.violetPDC.DataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Random;

public class Chat {
    public Chat() {
    }

    public static void sendMessage(Player p, String message) {
        p.sendMessage(MiniMessage.miniMessage().deserialize("<gradient:#88EBFF:#C9FFC2>" + message + "</gradient>"));
    }

    public static void sendErrorMessage(Player p, String message) {
        p.sendMessage(MiniMessage.miniMessage().deserialize("<gradient:#e3173c:#ff0000>" + message + "</gradient>"));
    }

    public static void sendSuccessMessage(Player p, String message) {
        p.sendMessage(MiniMessage.miniMessage().deserialize("<gradient:#17e373:#17e332>" + message + "</gradient>"));
    }

    public static Component stringToComponent(String message) {
        return MiniMessage.miniMessage().deserialize(message);
    }

    public static Component redFade(String message) {
        return stringToComponent(String.format("<gradient:#e3173c:#9f17e3>%s</gradient>", message));
    }

    public static Component errorFade(String message) {
        return stringToComponent(String.format("<b><#A30505>ERROR:</b> <#FFD7FE>%s", message));
    }

    public static Component greenFade(String message) {
        return stringToComponent(String.format("<gradient:#17e373:#17e332>%s</gradient>", message));
    }

    public static Component blueFade(String message) {
        return stringToComponent(String.format("<gradient:#1669f7:#16d6f7>%s</gradient>", message));
    }

    public static Component yellowFade(String message) {
        return stringToComponent(String.format("<gradient:#F4F30F:#F5BD0F>%s</gradient>", message));
    }

    public static Component cottonCandy(String message) {
        return stringToComponent(String.format("<gradient:#AAE3E9:#DFBDEA>%s</gradient>", message));
    }

    public static Component inputSaveComponent(String message) {
        return PlainTextComponentSerializer.plainText().deserialize(message.replaceAll("", "<").replaceAll("", ">"));
    }

    public static String componentToLegacy(Component component) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    public static String getRandomColor() {
        Random random = new Random();
        int nextInt = random.nextInt(16777216);
        return String.format("#%06x", nextInt);
    }
}
