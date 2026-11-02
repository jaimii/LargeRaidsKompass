package com.solarrabbit.largeraids.raid.mob.manager;

import com.solarrabbit.largeraids.LargeRaids;
import com.solarrabbit.largeraids.config.custommobs.CustomMobsConfig;
import com.solarrabbit.largeraids.raid.mob.Bomber;
import com.solarrabbit.largeraids.util.ChatColorUtil;
import com.solarrabbit.largeraids.util.VersionUtil;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;

import net.kyori.adventure.text.Component;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class BomberManager implements CustomRaiderManager, Listener {
    private static final int DEATH_XP_POINTS = getXpForLevel(15);
    private static final Component BOMBER_NAME = ChatColorUtil.parse("<red>Rampart");
    private static final Component VEX_NAME = ChatColorUtil.parse("<red>Drone Vex");

    private final LargeRaids plugin;
    private final NamespacedKey bomberKey;
    private final NamespacedKey vexKey;
    private final NamespacedKey tntKey;
    private final ItemStack bannerTemplate;
    private final ItemStack tntItemTemplate;

    private float bomberExplosivePower;
    private float tntExplosivePower;
    private int primedTntTicks;
    private boolean tntBreakBlocks;

    public BomberManager() {
        this.plugin = JavaPlugin.getPlugin(LargeRaids.class);
        this.bomberKey = new NamespacedKey(plugin, "bomber");
        this.vexKey = new NamespacedKey(plugin, "bomber_vex");
        this.tntKey = new NamespacedKey(plugin, "bomber_vex_tnt");
        this.bannerTemplate = createDefaultBanner();
        this.tntItemTemplate = new ItemStack(Material.TNT);
    }

    @Override
    public void loadSettings(CustomMobsConfig config) {
        bomberExplosivePower = config.getBomberConfig().getBomberExplosivePower();
        tntExplosivePower = config.getBomberConfig().getTntExplosivePower();
        primedTntTicks = config.getBomberConfig().getPrimedTntTicks();
        tntBreakBlocks = config.getBomberConfig().shouldTntBreakBlocks();
    }

    @Override
    public Bomber spawn(Location location) {
        Spellcaster evoker = (Spellcaster) location.getWorld().spawnEntity(location, EntityType.EVOKER);
        EntityEquipment equipment = evoker.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(bannerTemplate.clone());
            equipment.setHelmetDropChance(1.0f);
        }

        evoker.setPatrolLeader(true);
        evoker.getPersistentDataContainer().set(bomberKey, PersistentDataType.BYTE, (byte) 0);
        evoker.customName(BOMBER_NAME);

        EvokerGoalUtil.injectRaidFleeGoal(evoker, location);

        return new Bomber(evoker);
    }

    @EventHandler
    private void onFangSpawn(EntitySpawnEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER_FANGS)
            return;
        EvokerFangs fangs = (EvokerFangs) evt.getEntity();
        LivingEntity owner = fangs.getOwner();
        if (owner instanceof Spellcaster evoker && isBomber(evoker)) {
            evt.setCancelled(true);
            fangs.getWorld().createExplosion(fangs.getLocation(), bomberExplosivePower, false, tntBreakBlocks, owner);
        }
    }

    @EventHandler
    private void onDamageRaider(EntityDamageByEntityEvent evt) {
        if (evt.getCause() != DamageCause.ENTITY_EXPLOSION)
            return;
        if (evt.getDamager().getType() != EntityType.EVOKER)
            return;
        if (!(evt.getEntity() instanceof Raider))
            return;
        Spellcaster evoker = (Spellcaster) evt.getDamager();
        if (isBomber(evoker))
            evt.setCancelled(true);
    }

    @EventHandler
    private void onSummonVex(CreatureSpawnEvent evt) {
        if (evt.getEntityType() != EntityType.VEX)
            return;
        Vex vex = (Vex) evt.getEntity();
        LivingEntity owner = VersionUtil.getCraftVexWrapper(vex).getOwner();
        if (owner instanceof Spellcaster evoker && isBomber(evoker)) {
            vex.getPersistentDataContainer().set(vexKey, PersistentDataType.BYTE, (byte) 0);
            if (vex.getEquipment() != null) {
                vex.getEquipment().setItemInMainHand(tntItemTemplate.clone());
            }
            vex.customName(VEX_NAME);
        }
    }

    @EventHandler
    private void onVexAttack(EntityDamageByEntityEvent evt) {
        if (evt.getDamager().getType() != EntityType.VEX)
            return;
        Vex damager = (Vex) evt.getDamager();
        if (!isBomberVex(damager))
            return;
        evt.setCancelled(true);
        TNTPrimed tnt = (TNTPrimed) damager.getWorld().spawnEntity(damager.getLocation(), EntityType.TNT);
        tnt.setFuseTicks(primedTntTicks);
        tnt.getPersistentDataContainer().set(tntKey, PersistentDataType.BYTE, (byte) 0);
    }

    @EventHandler
    private void onTNTDetonate(EntityExplodeEvent evt) {
        if (evt.getEntityType() != EntityType.TNT)
            return;
        TNTPrimed tnt = (TNTPrimed) evt.getEntity();
        if (!isVexTNT(tnt))
            return;
        evt.setCancelled(true);
        tnt.getWorld().createExplosion(tnt.getLocation(), tntExplosivePower, false, false);
    }

    @EventHandler
    private void onTNTDamage(EntityDamageByEntityEvent evt) {
        if (evt.getDamager().getType() != EntityType.TNT)
            return;
        TNTPrimed tnt = (TNTPrimed) evt.getDamager();
        if (!isVexTNT(tnt))
            return;
        evt.setCancelled(true);
    }

    @EventHandler
    private void onDeath(EntityDeathEvent evt) {
        if (evt.getEntityType() != EntityType.EVOKER)
            return;
        Spellcaster evoker = (Spellcaster) evt.getEntity();
        if (isBomber(evoker)) {
            evt.setDroppedExp(DEATH_XP_POINTS);
        }
    }

    private static int getXpForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }

    private static ItemStack createDefaultBanner() {
        ItemStack banner = new ItemStack(Material.LIGHT_GRAY_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(DyeColor.ORANGE, PatternType.RHOMBUS));
            meta.addPattern(new Pattern(DyeColor.YELLOW, PatternType.FLOWER));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_UP_LEFT));
            meta.addPattern(new Pattern(DyeColor.LIGHT_GRAY, PatternType.DIAGONAL_RIGHT));
            meta.addPattern(new Pattern(DyeColor.RED, PatternType.GRADIENT_UP));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.CIRCLE));
            meta.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));
            meta.displayName(ChatColorUtil.parseItem("<gold><italic><red>Rampart Banner"));
            banner.setItemMeta(meta);
        }
        banner.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        return banner;
    }

    private boolean isBomber(Spellcaster entity) {
        return entity.getPersistentDataContainer().has(bomberKey, PersistentDataType.BYTE);
    }

    private boolean isBomberVex(Vex entity) {
        return entity.getPersistentDataContainer().has(vexKey, PersistentDataType.BYTE);
    }

    private boolean isVexTNT(TNTPrimed entity) {
        return entity.getPersistentDataContainer().has(tntKey, PersistentDataType.BYTE);
    }
}