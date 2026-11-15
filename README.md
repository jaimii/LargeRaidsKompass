# LargeRaids

[![](https://jitpack.io/v/zhenghanlee/LargeRaids-API.svg)](https://github.com/zhenghanlee/LargeRaids-API)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/e2b8ef0d41e3404b91a62a35196c7e9e)](https://www.codacy.com/gh/zhenghanlee/LargeRaids/dashboard?utm_source=github.com&utm_medium=referral&utm_content=zhenghanlee/LargeRaids&utm_campaign=Badge_Grade)
[![License](https://img.shields.io/github/license/zhenghanlee/LargeRaids)](https://img.shields.io/github/license/zhenghanlee/LargeRaids)
[![Spigot Downloads](http://badge.henrya.org/spigotbukkit/downloads?spigot=95422&name=spigot_downloads)](https://www.spigotmc.org/resources/largeraids-1-14-x-1-17-x.95422/)
[![Commit Activity](https://img.shields.io/github/commit-activity/m/zhenghanlee/LargeRaids)](https://img.shields.io/github/commit-activity/m/zhenghanlee/LargeRaids)
[![Discord](https://img.shields.io/discord/846941711741222922.svg?logo=discord)](https://discord.gg/YSv7pptDjE)

## Introduction

**LargeRaids** is a vanilla game experience enhancement plugin for [raids](https://minecraft.wiki/w/Raid), expanding Minecraft's raid system to accommodate multiplayer survival servers with higher difficulty, higher Raid Omen levels, custom wave compositions, unique illager variants, boss encounters, and configurable rewards.

This is a refork of the original LargeRaids plugin by [solarrabbit99](https://github.com/solarrabbit99), which was later forked and maintained by [ThisTestUser](https://github.com/ThisTestUser) for newer Minecraft releases (previous builds from 1.21.5 can be found in ThisTestUser's fork [here](https://github.com/ThisTestUser/LargeRaids)). This fork is built and maintained specifically for our Minecraft Survival Multiplayer server, **Project: Kompass**, running on **Paper 26.3** (Java 25).

> **Note:** Paper (26.3+) is required for this fork, as it relies on Paper-native features including Adventure MiniMessage formatting, Data Components, entity pathfinding, and optional MythicMobs support.

---

## Permissions

| Permission | Default | Description |
| :--- | :---: | :--- |
| `largeraids.admin.*` | `op` | Grants access to all LargeRaids admin commands. |
| `largeraids.admin.giveegg` | `op` | Allows giving custom raider spawn eggs. |
| `largeraids.start` | `op` | Allows manually starting a large raid. |
| `largeraids.skip` | `op` | Allows skipping the current wave of a large raid. |
| `largeraids.stop` | `op` | Allows stopping an active large raid. |
| `largeraids.show` | `true` | Allows viewing the current wave of an ongoing raid. |
| `largeraids.give` | `op` | Allows giving the configurable raid summon item. |
| `largeraids.reload` | `op` | Allows reloading plugin configuration files. |
| `largeraids.centers` | `op` | Allows managing and visualizing artificial village centers. |
| `largeraids.glow` | `op` | Allows outlining raiders in an active raid. |
| `largeraids.attack` | `op` | Allows adding target entity types to active wave raiders. |
| `largeraids.config` | `op` | Allows overriding spawn and target locations for an active raid. |
| `largeraids.mobcontrol` | `op` | Allows directing nearby mobs to pathfind toward a target point. |

---

## Commands

| Command | Usage | Description |
| :--- | :--- | :--- |
| `/lrstart` | `/lrstart [player\|center] [name]` | Starts a large raid at your location, a target player, or a registered village center. |
| `/lrstop` | `/lrstop [player\|center] [name]` | Immediately stops an active large raid and cleans up its raiders. |
| `/lrskip` | `/lrskip` | Skips the current wave and immediately triggers the next wave. |
| `/lrshow` | `/lrshow` | Displays the current wave and total waves for either a normal or large raid. |
| `/lrgive` | `/lrgive <player> [amount]` | Gives the configured lava-drop raid summon item to a player. |
| `/lregg` | `/lregg <player> <variant> [amount]` | Gives custom spawn eggs (`firework`, `bomber`, `necromancer`, `adjudicator`, `king`, `archillager`, or `mythic:<mob>`). |
| `/lrreload` | `/lrreload` | Reloads all LargeRaids configuration files. |
| `/lrcenters` | `/lrcenters [add\|remove\|show\|hide] [name]` | Lists (with click-to-teleport), adds, removes, or displays a 96-block particle boundary around artificial village centers. |
| `/lrglow` | `/lrglow <seconds\|toggle>` | Outlines raiders in an ongoing raid for a set duration or toggles persistent outlines. |
| `/lrattack` | `/lrattack <priority> <throughWalls> <entity>` | Instructs all raiders in the current wave to attack a specific entity type. |
| `/lrconfig` | `/lrconfig <spawn\|target> [args...]` | Sets or clears custom wave spawn coordinates or target destinations for an active raid. |
| `/lrmobcontrol` | `/lrmobcontrol <entity> <range> <clear\|x y z radius speed prio once>` | Directs nearby mobs to pathfind toward a chosen location. |

---

## Features

### Expanded Multiplayer Raid Mechanics
* **Scalable Waves & Omen Levels**: Supports configurable multi-wave raids (e.g., 20+ waves) with per-wave mob rosters, custom titles/chat announcements (powered by Adventure & MiniMessage), and custom summon/victory/defeat sounds.
* **Multiple Raid Triggers**:
  * **Village Omen Absorption**: Absorbs vanilla Raid Omen levels when entering villages, with an option to disable standard vanilla raids or restrict large raids to registered artificial villages.
  * **Lava Summon Item**: Drop a configurable summon item into lava to trigger or extend a raid.
  * **Time-Based Raids**: Optional scheduled raids triggered automatically at configured world times.
* **Artificial Village Centers**: Register custom village centers anywhere in the world (persisted in SQLite) and preview their 96-block perimeter in-game.
* **Persistent Raids Across Restarts**: Active large raids—including current wave progress, omen level, triggerer death counts, player kill/damage stats, active custom raiders/mounts, and custom spawn/target settings—are saved to disk and automatically restored on server restart so ongoing raids never revert to vanilla raids.
* **Outlining & Bell Integration**: Ringing a bell outlines raiders (configurable for normal and/or large raids) alongside `/lrglow`.
* **Custom Rewards & Lore Drops**: Reward participating players who meet minimum kill and damage thresholds with custom items, Hero of the Village effects, and console commands. Wandering Traders also have a configurable chance to drop a custom lore book.
* **Spawn Protection & Out-of-Bounds Safeguards**: Prevents wave raiders from spawning on village centers or outside valid raid bounds (including snowy terrain), and automatically cleans up ghost entities left behind by mounted mobs.

---

### Custom Illager Variants & Enhanced Raid AI

* **Firework Pillager (*Enforcer*)**:
  * Wears a custom yellow Enforcer Banner and fires multi-effect firework rockets from its offhand without harming fellow raiders.
  * Drops **5 Levels** of XP and its banner on death.
* **Bomber (*Rampart*)**:
  * Evoker wearing a custom Rampart Banner whose fangs detonate into explosions (configurable power and block damage) without hurting fellow raiders.
  * Summons *Drone Vexes* holding TNT that drop primed TNT on attack.
  * Drops **15 Levels** of XP and its banner on death.
* **Necromancer**:
  * Evoker wearing a custom Necromancer Banner that calls down lightning instead of fangs (without harming fellow raiders, starting fires, or transforming villagers into witches).
  * Converts Vex summons into chainmail-armored Skeletons (against players) or pumpkin-headed Zombies (against villagers).
  * Drops **25 Levels** of XP and its banner on death.
* **Adjudicator (Foot & Horse-Mounted)**:
  * Vindicator wearing a custom Adjudicator Banner, wielding an Iron Spear and custom shield.
  * Charges targets with a raised spear lunge that pierces up to 3 targets in a line, then retreats between strikes.
  * Mounted variants ride an armored *Adjudicator's Steed* with momentum damage scaling and remain protected from damage and forced dismounts until their steed is defeated.
  * Drops **20 Levels** of XP and its banner on death.
* **Enhanced Witch & Evoker Raid AI**:
  * Custom Evoker variants are constrained to never leave the raid area while fleeing from players at a predictable pace.
  * Raid Witches actively stay grouped with fellow raiders, prioritize healing and buffing injured allies, and throw **lingering potions** whose clouds filter beneficial effects exclusively to raiders and harmful effects away from raiders.
* **King Raider (*Raid Captain* & *Juggernaut Ravager*)**:
  * Dual-boss encounter featuring a Raid Captain Evoker mounted on an armored, regenerating Juggernaut Ravager with dual red boss bars.
  * The Captain is immune while mounted and casts fiery fangs and enchanted-sword Vexes; the Ravager unleashes a Warden-style Sonic Boom every 100 HP lost or when blocked by a shield.
  * Drops **50 Levels** of XP (Raid Captain) and **100 Levels** of XP (Juggernaut Ravager) plus the Raid Captain Banner.

---

### Secret Final Boss: The Arch-Illager

* **Unlock Conditions & Surprise Wave**:
  * Wave 20 still plays out as the normal `FINAL WAVE` featuring the Raid Captain.
  * If the raid began at the **maximum Omen level** and the player who triggered the raid died **fewer than 3 times** throughout the raid, clearing Wave 20 unlocks a surprise secret wave (**`FINAL WAVE?`**) with unique boss dialogue.
  * Spawns with his Juggernaut Ravager, **2 Horse-Mounted Adjudicators**, and **2 Ravager-Mounted Pillager Patrol Leaders** carrying Ominous Banners.
* **Four-Phase Boss Fight (Blue Theme & Blue Boss Bar)**:
  * **Phase 1 (Mounted)**: Rides his Juggernaut Ravager, completely immune to all damage, firing Flame-enchanted arrows until the Ravager is slain.
  * **Phase 2 (500 HP, Netherite Axe)**: Dismounts, swaps to a Netherite Axe, gains complete **immunity to projectiles** (melee-only), summons 3 Pillagers, and alternates between chasing players and casting condensed **6-Star** or **3-Wave Expanding Circle** armor-ignoring fiery Evoker Fangs (2 hearts). Every 50 HP lost, he splits into **3 hittable Illusioners** in a triangle (2 glowing fakes, 1 non-glowing real) shooting straight-line fangs—hitting a fake spawns 3 Vexes, while hitting the real boss deals +20 bonus damage and stuns him for 3 seconds.
  * **Phase 3 (500 HP)**: Triggers a floating, spinning Wither-style Totem regeneration with circular Totem particle rings before slamming down with lightning—clearing foliage/leaves and turning grass to dirt in an 8x8 circular area while unleashing an armor-ignoring explosion (5 damage to players, 100 damage to Iron Golems). Every 100 HP lost he summons 3 Pillagers and 3 Vindicators, splits into **5 Illusioners** every 50 HP lost, and empowers fangs to 4 hearts (+8 extra fangs).
  * **Phase 4 (500 HP)**: Triggers a more violent floating Totem regeneration with circular Sculk particle rings, multi-lightning ground slam turning the circular terrain into **Sculk**, an armor-ignoring explosion (10 damage to players, 100 damage to Iron Golems), and summons **2 foot Adjudicators**. Leaves a trail of Sculk beneath his feet, casts condensed **Crisscross** or **Inner/Outer Ring** fangs (+16 extra fangs, 4 hearts), summons Pillagers + Vindicators + 2 Evokers every 100 HP lost, and splits into **9 Illusioners** in a circle around the player every 50 HP lost—where hitting an incorrect clone triggers a charged **Sonic Blast**.
  * **Death & Rewards**: Ascends in a Bedrock Wither-style spinning, sparkling circular explosion, spreads Sculk across the impact circle, and drops **5 Echo Shards**, **1 Netherite Ingot**, the **Arch-Illager Standard**, and **100 Levels** of XP.

---

### Integrations & Custom Spawn Eggs
* **Custom Spawn Eggs**: Every custom variant (including the Arch-Illager and MythicMobs variants) can be spawned directly by players or dispensers via `/lregg` and drops its custom banner on death.
* **MythicMobs**: Configure MythicMobs raiders directly in your wave rosters.
* **PlaceholderAPI**: Exposes `largeraids` placeholders (`in_range`, `wave`, `total_waves`, `remaining_raiders`, `omen_level`, `player_kills`, and `debug_total_registered`).

---

## Known Bugs
* For any large raid, the minimum Hero of the Village effect given is level 2. This affects all versions of LargeRaids and occurs because vanilla awards the effect before the plugin's victory handler runs.

---

## Updating to Newer Versions

1. Update the Minecraft target version and Java toolchain (if required by the new Minecraft release) in the Gradle build configuration and plugin manifest.
2. Update the supported server version check in the plugin's main startup configuration (minor hotfix versions such as `26.3.1` are accepted automatically).
3. Run the Gradle build task (`gradlew build` or `./gradlew build`) to verify compatibility.
4. Because Minecraft `26.1+` ships completely unobfuscated at runtime, manual obfuscation mapping lookups are no longer required; however, any renamed or changed Paper/Minecraft internal APIs flagged during compilation will still need to be adjusted manually.

---

## Getting the Plugin

### Building & Installation
1. Ensure you have **Java 25** installed and are running a **Paper 26.3+** server.
2. Clone the repository and run `gradlew build` (Windows) or `./gradlew build` (Linux/macOS).
3. Copy the compiled JAR from the build output directory into your server's `plugins` folder and restart the server.
4. *(Optional)* Install **PlaceholderAPI** and/or **MythicMobs** for placeholder and custom MythicMob wave support.

### Using the API
The plugin's API is maintained in a separate [LargeRaids-API repository](https://github.com/zhenghanlee/LargeRaids-API) and distributed via **JitPack**. To integrate with LargeRaids in another plugin, add the JitPack repository (`https://jitpack.io`) and depend on `com.github.zhenghanlee:LargeRaids-API` using the version shown in the JitPack badge at the top of this document.

### Statistics
[![](https://bstats.org/signatures/bukkit/LargeRaids.svg)](https://bstats.org/plugin/bukkit/LargeRaids/13910)
