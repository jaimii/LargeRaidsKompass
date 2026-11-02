package com.solarrabbit.largeraids.misc;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.raid.mob.Adjudicator;
import com.solarrabbit.largeraids.raid.mob.ArchIllager;
import com.solarrabbit.largeraids.raid.mob.Bomber;
import com.solarrabbit.largeraids.raid.mob.FireworkPillager;
import com.solarrabbit.largeraids.raid.mob.KingRaider;
import com.solarrabbit.largeraids.raid.mob.MythicRaider;
import com.solarrabbit.largeraids.raid.mob.Necromancer;
import com.solarrabbit.largeraids.raid.mob.manager.AdjudicatorManager;
import com.solarrabbit.largeraids.raid.mob.manager.ArchIllagerManager;
import com.solarrabbit.largeraids.raid.mob.manager.BomberManager;
import com.solarrabbit.largeraids.raid.mob.manager.FireworkPillagerManager;
import com.solarrabbit.largeraids.raid.mob.manager.KingRaiderManager;
import com.solarrabbit.largeraids.raid.mob.manager.MobManagers;
import com.solarrabbit.largeraids.raid.mob.manager.MythicRaiderManager;
import com.solarrabbit.largeraids.raid.mob.manager.NecromancerManager;
import com.solarrabbit.largeraids.util.CustomEggUtil;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CustomEggListener implements Listener {

    private final LargeRaids plugin;
    private final NamespacedKey eggPdcKey;
    private final NamespacedKey necromancerKey;
    private final NamespacedKey bomberKey;
    private final NamespacedKey fireworkKey;
    private final NamespacedKey kingKey;
    private final NamespacedKey adjudicatorKey;
    private final NamespacedKey archKey;

    public CustomEggListener(LargeRaids plugin) {
        this.plugin = plugin;
        this.eggPdcKey = new NamespacedKey(plugin, CustomEggUtil.PDC_KEY);
        this.necromancerKey = new NamespacedKey(plugin, "necromancer");
        this.bomberKey = new NamespacedKey(plugin, "bomber");
        this.fireworkKey = new NamespacedKey(plugin, "firework_pillager");
        this.kingKey = new NamespacedKey(plugin, "juggernaut_king");
        this.adjudicatorKey = new NamespacedKey(plugin, "adjudicator");
        this.archKey = new NamespacedKey(plugin, "arch_illager");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String variant = meta.getPersistentDataContainer().get(eggPdcKey, PersistentDataType.STRING);
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
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Raider))
            return;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        boolean hasCustomBanner = pdc.has(necromancerKey, PersistentDataType.BYTE) ||
                pdc.has(bomberKey, PersistentDataType.BYTE) ||
                pdc.has(fireworkKey, PersistentDataType.BYTE) ||
                pdc.has(kingKey, PersistentDataType.BYTE) ||
                pdc.has(adjudicatorKey, PersistentDataType.BYTE) ||
                pdc.has(archKey, PersistentDataType.BYTE);

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

        String variant = meta.getPersistentDataContainer().get(eggPdcKey, PersistentDataType.STRING);
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
        MobManagers mobManagers = plugin.getMobManagers();

        if (lowerVariant.startsWith("mythic:")) {
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
                plugin.getLogger().warning("MythicMobs is not enabled; cannot spawn: " + variant);
                return;
            }
            String mobName = variant.substring(7);
            MythicMob type = MythicBukkit.inst().getMobManager().getMythicMob(mobName).orElse(null);
            if (type != null) {
                MythicRaiderManager manager = (MythicRaiderManager) mobManagers.getMobManager(MythicRaider.class);
                manager.spawn(loc, type);
            } else {
                plugin.getLogger().warning("Failed to spawn MythicRaider. Unknown MythicMob: " + mobName);
            }
            return;
        }

        switch (lowerVariant) {
            case "necromancer":
                NecromancerManager necromancerManager = (NecromancerManager) mobManagers.getMobManager(Necromancer.class);
                necromancerManager.spawn(loc);
                break;
            case "bomber":
                BomberManager bomberManager = (BomberManager) mobManagers.getMobManager(Bomber.class);
                bomberManager.spawn(loc);
                break;
            case "firework":
                FireworkPillagerManager fireworkManager = (FireworkPillagerManager) mobManagers.getMobManager(FireworkPillager.class);
                fireworkManager.spawn(loc);
                break;
            case "king":
                KingRaiderManager kingManager = (KingRaiderManager) mobManagers.getMobManager(KingRaider.class);
                kingManager.spawn(loc);
                break;
            case "adjudicator":
                AdjudicatorManager adjudicatorManager = (AdjudicatorManager) mobManagers.getMobManager(Adjudicator.class);
                adjudicatorManager.spawnRider(loc);
                break;
            case "archillager":
            case "arch-illager":
            case "arch":
                ArchIllagerManager archManager = (ArchIllagerManager) mobManagers.getMobManager(ArchIllager.class);
                archManager.spawn(loc);
                break;
            default:
                plugin.getLogger().warning("Attempted to spawn unknown custom variant: " + variant);
                break;
        }
    }
}