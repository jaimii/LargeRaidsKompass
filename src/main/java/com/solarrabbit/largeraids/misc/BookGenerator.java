package com.solarrabbit.largeraids.misc;

import java.io.InputStream;
import java.util.List;

import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.ConfigUtil;

import net.kyori.adventure.text.Component;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class BookGenerator {
    private final ItemStack bookTemplate;

    public BookGenerator(InputStream resource) {
        YamlConfiguration config = ConfigUtil.getYamlConfig(resource);
        String rawTitle = config.getString("title", "Raid Book");
        String author = config.getString("author", "Unknown");
        List<String> pages = config.getStringList("pages");

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle(rawTitle);
            meta.displayName(ChatColorUtil.parseItem("<gold>" + rawTitle));
            meta.setAuthor(author);
            for (String page : pages) {
                Component pageComponent = ChatColorUtil.parse(page);
                meta.addPages(pageComponent);
            }
            book.setItemMeta(meta);
        }
        this.bookTemplate = book;
    }

    public ItemStack getBook() {
        return bookTemplate.clone();
    }
}