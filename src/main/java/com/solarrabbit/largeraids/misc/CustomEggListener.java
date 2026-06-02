package com.solarrabbit.largeraids.misc;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.util.CustomEggUtil;
import com.solarrabbit.largeraids.raid.mob.manager.AdjudicatorManager;
import com.solarrabbit.largeraids.raid.mob.manager.BomberManager;
import com.solarrabbit.largeraids.raid.mob.manager.FireworkPillagerManager;
import com.solarrabbit.largeraids.raid.mob.manager.KingRaiderManager;
import com.solarrabbit.largeraids.raid.mob.manager.MythicRaiderManager;
import com.solarrabbit.largeraids.raid.mob.manager.NecromancerManager;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CustomEggListener implements Listener {

    private final LargeRaids plugin;

    public CustomEggListener(LargeRaids plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        NamespacedKey key = new NamespacedKey(plugin, CustomEggUtil.PDC_KEY);
        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        String variant = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (variant == null) return;

        event.setCancelled(true);

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        Location spawnLoc = clickedBlock.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);

        spawnCustomMob(variant, spawnLoc);

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onMobDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        org.bukkit.entity.LivingEntity entity = event.getEntity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        NamespacedKey necromancerKey = new NamespacedKey(plugin, "necromancer");
        NamespacedKey bomberKey = new NamespacedKey(plugin, "bomber");
        NamespacedKey fireworkKey = new NamespacedKey(plugin, "firework_pillager");
        NamespacedKey kingKey = new NamespacedKey(plugin, "juggernaut_king");
        NamespacedKey adjudicatorKey = new NamespacedKey(plugin, "adjudicator");

        boolean hasCustomBanner = pdc.has(necromancerKey, PersistentDataType.BYTE) ||
                pdc.has(bomberKey, PersistentDataType.BYTE) ||
                pdc.has(fireworkKey, PersistentDataType.BYTE) ||
                pdc.has(kingKey, PersistentDataType.BYTE) ||
                pdc.has(adjudicatorKey, PersistentDataType.BYTE);

        if (hasCustomBanner && entity.getEquipment() != null) {
            ItemStack helmet = entity.getEquipment().getHelmet();
            if (helmet != null && helmet.getType() != Material.AIR) {
                if (!event.getDrops().contains(helmet)) {
                    event.getDrops().add(helmet);
                }
            }
        }
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        NamespacedKey key = new NamespacedKey(plugin, CustomEggUtil.PDC_KEY);
        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        String variant = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (variant == null) return;

        event.setCancelled(true);

        Block dispenserBlock = event.getBlock();
        org.bukkit.block.data.type.Dispenser dispenserData = (org.bukkit.block.data.type.Dispenser) dispenserBlock.getBlockData();
        BlockFace face = dispenserData.getFacing();
        Location spawnLoc = dispenserBlock.getRelative(face).getLocation().add(0.5, 0.0, 0.5);

        spawnCustomMob(variant, spawnLoc);

        org.bukkit.block.Dispenser dispenserState = (org.bukkit.block.Dispenser) dispenserBlock.getState();
        Inventory inv = dispenserState.getInventory();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack slotItem = inv.getItem(i);
                if (slotItem != null && slotItem.isSimilar(item)) {
                    slotItem.setAmount(slotItem.getAmount() - 1);
                    inv.setItem(i, slotItem);
                    break;
                }
            }
        });
    }

    private void spawnCustomMob(String variant, Location loc) {
        String lowerVariant = variant.toLowerCase(java.util.Locale.ROOT);

        if (lowerVariant.startsWith("mythic:")) {
            String mobName = variant.substring(7);
            MythicMob type = MythicBukkit.inst().getMobManager().getMythicMob(mobName).orElse(null);
            if (type != null) {
                MythicRaiderManager manager = new MythicRaiderManager();
                manager.loadSettings(plugin.getCustomMobsConfig());
                manager.spawn(loc, type);
            } else {
                plugin.getLogger().warning("Failed to spawn MythicRaider. Unknown MythicMob: " + mobName);
            }
            return;
        }

        switch (lowerVariant) {
            case "necromancer":
                NecromancerManager necromancerManager = new NecromancerManager();
                necromancerManager.loadSettings(plugin.getCustomMobsConfig());
                necromancerManager.spawn(loc);
                break;
            case "bomber":
                BomberManager bomberManager = new BomberManager();
                bomberManager.loadSettings(plugin.getCustomMobsConfig());
                bomberManager.spawn(loc);
                break;
            case "firework":
                FireworkPillagerManager fireworkManager = new FireworkPillagerManager();
                fireworkManager.loadSettings(plugin.getCustomMobsConfig());
                fireworkManager.spawn(loc);
                break;
            case "king":
                KingRaiderManager kingManager = new KingRaiderManager();
                kingManager.loadSettings(plugin.getCustomMobsConfig());
                kingManager.spawn(loc);
                break;
            case "adjudicator":
                AdjudicatorManager adjudicatorManager = new AdjudicatorManager();
                adjudicatorManager.loadSettings(plugin.getCustomMobsConfig());
                adjudicatorManager.spawnRider(loc); // Changed to spawn as a mounted rider
                break;
            default:
                plugin.getLogger().warning("Attempted to spawn unknown custom variant: " + variant);
                break;
        }
    }
}