package com.solarrabbit.largeraids.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.CommandSender;

public class ChatColorUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Parses a string containing MiniMessage tags (and/or legacy '&'/'§' codes) into an Adventure Component.
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(legacyToMiniMessage(input));
    }

    /**
     * Parses a string for item display names or lore with default italics disabled unless explicitly set.
     */
    public static Component parseItem(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Sends a MiniMessage-formatted message to a CommandSender.
     */
    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null) {
            sender.sendMessage(parse(message));
        }
    }

    /**
     * Sends a message to a CommandSender with a default fallback NamedTextColor.
     */
    public static void send(CommandSender sender, NamedTextColor defaultColor, String message) {
        if (sender != null && message != null) {
            sender.sendMessage(Component.empty().color(defaultColor).append(parse(message)));
        }
    }

    /**
     * Converts legacy '&' or '§' color/format codes into MiniMessage tags.
     */
    public static String colorize(String str) {
        return legacyToMiniMessage(str);
    }

    public static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String replacement = mapLegacyCode(code);
                if (replacement != null) {
                    sb.append(replacement);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String mapLegacyCode(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }
}