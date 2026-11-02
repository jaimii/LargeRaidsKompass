package com.solarrabbit.largeraids;

import com.solarrabbit.largeraids.util.ChatColorUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

/**
 * Standardized logger for the plugin using Adventure & MiniMessage.
 */
public class PluginLogger {
    /**
     * Level of log message.
     */
    public enum Level {
        FAIL(NamedTextColor.RED, "<red>"),
        INFO(NamedTextColor.AQUA, "<aqua>"),
        WARN(NamedTextColor.YELLOW, "<yellow>"),
        SUCCESS(NamedTextColor.GREEN, "<green>");

        private final NamedTextColor color;
        private final String tag;

        Level(NamedTextColor color, String tag) {
            this.color = color;
            this.tag = tag;
        }

        public NamedTextColor getColor() {
            return this.color;
        }

        @Override
        public String toString() {
            return this.tag;
        }
    }

    /**
     * Uses {@link ConsoleCommandSender} to log color-coded Adventure components.
     *
     * @param message message to send to console
     * @param type    {@link PluginLogger.Level} associated with the message
     */
    public void sendMessage(String message, Level type) {
        Component prefix = Component.text("[LargeRaids] ", type.getColor(), TextDecoration.BOLD);
        Component body = Component.empty().color(type.getColor())
                .decoration(TextDecoration.BOLD, false)
                .append(ChatColorUtil.parse(message));
        Bukkit.getConsoleSender().sendMessage(prefix.append(body));
    }
}