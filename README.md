<div align="center">

# NewGen Team

**A modern team, faction and guild plugin for Paper and Folia servers.**

Built for Minecraft 1.21+ on Java 21.

[![Paper](https://img.shields.io/badge/Paper-1.21.4%2B-2f6fdb)](https://papermc.io)
[![Folia](https://img.shields.io/badge/Folia-supported-27ae60)](https://papermc.io/software/folia)
[![Java](https://img.shields.io/badge/Java-21-e67e22)](https://adoptium.net)
[![bStats](https://img.shields.io/badge/bStats-32387-00695c)](https://bstats.org/plugin/bukkit/NewGenTeam)
[![Discord](https://img.shields.io/badge/Discord-community-5865F2)](https://discord.gg/twKAP7JjW)

[Features](#features) · [Quick start](#quick-start) · [Commands](#commands) · [Configuration](#configuration) · [Placeholders](#placeholderapi) · [Developer API](#developer-api) · [FAQ](#faq)

</div>

---

## Features

**GUI-driven administration.** Over 30 configurable menus cover members, invites, join requests, bank, chest, homes, settings, relations, leaderboards and a complete staff panel. Menu layouts, materials, slots and custom model data are all defined in YAML.

**Private team chat.** Toggle with `/team chat` or send one-off messages. Messages are cancelled at the earliest event priority and removed from the viewer list, so they never reach public chat or Discord bridges such as DiscordSRV. Team chat is mirrored to the console for moderation.

**Team bank and tier progression.** A Vault-backed shared bank with role-based withdraw permissions, plus PlayerPoints-based tier upgrades that raise member, chest and home limits per level.

**Paged team chest.** Shared storage with per-role deposit and withdraw control. Items are never destroyed: if capacity is reduced, overflow is retained in storage and restored when capacity returns.

**Team homes.** Multiple named homes per team with teleport warmup, cooldown, and cancel-on-move.

**Relations and combat protection.** Ally and enemy relations with friendly-fire protection covering melee, projectiles, TNT, lingering potions and tamed pets.

**Asynchronous multi-database storage.** SQLite (zero configuration), MySQL, MariaDB and PostgreSQL through HikariCP. Drivers are bundled. All I/O runs off the main thread and pending writes are drained on shutdown.

**Theme and language system.** A single `theme.yml` recolors every message, title and GUI. English and Vietnamese ship by default; custom languages are drop-in files with per-key fallback.

**Developer API.** Eight Bukkit events, a PlaceholderAPI expansion and audit logging.

**Folia support.** Region-aware scheduling throughout, declared with `folia-supported: true`.

---

## Quick start

Requirements: Paper or Folia **1.21.4+**, **Java 21**, [PacketEvents](https://modrinth.com/plugin/packetevents).

1. Download `NewGenTeam-x.x.x.jar` from [Releases](../../releases).
2. Install PacketEvents (required). Optionally install Vault, PlayerPoints and PlaceholderAPI.
3. Place the jars in `plugins/` and start the server.
4. Adjust the generated files in `plugins/NewGenTeam/`, then run `/team reload`.
5. Use `/team` in game.

---

## Commands

Main command: `/team` - aliases: `/t`, `/faction`, `/f`, `/guild`, `/g`

| Command | Description |
|---|---|
| `/team` | Open the main team menu |
| `/team create <name>` | Create a team |
| `/team rename <name>` | Rename your team |
| `/team invite <player>` | Invite a player |
| `/team accept [team]` / `/team deny [team]` | Accept or decline an invite |
| `/team join <team>` | Request to join a team |
| `/team leave` | Leave your team |
| `/team kick <member>` | Kick a member |
| `/team chat [message]` | Toggle team chat, or send a single team message |
| `/team home` / `/team sethome` | Teleport to or set the team home |
| `/team info` / `/team top` / `/team list` | Stats, leaderboards, team list |
| `/team admin` | Open the admin panel |
| `/team reload` | Reload configs, theme, language and menus |

---

## Permissions

| Permission | Grants |
|---|---|
| `newgenteam.use` | Basic `/team` usage |
| `newgenteam.admin` | `/team reload` and full admin access |
| `newgenteam.admin.view` | Open the admin panel |
| `newgenteam.admin.search` | Search teams and players in the admin panel |
| `newgenteam.admin.edit` | Edit teams (rename, tag, tier, settings) |
| `newgenteam.admin.delete` | Disband teams |
| `newgenteam.admin.logs` | View team audit logs |
| `newgenteam.admin.bank` / `.home` / `.chest` / `.relation` | Manage the respective team feature |

In-team permissions (invite, kick, chest access, bank withdraw, homes, team chat, relations) are role-based (Owner, Co-Owner, Manager, Member, Recruit) and are managed in game through the permission menu. No permission plugin is required for them.

---

## Configuration

All files are generated in `plugins/NewGenTeam/` with bilingual comments on first run.

| File | Purpose |
|---|---|
| `config.yml` | Language selection (`vi_VN` / `en_US`) |
| `settings.yml` | Core gameplay settings and team chat |
| `levels.yml` | Tier levels, member limits, upgrade costs |
| `homes.yml` | Home warmup and cooldown rules |
| `chest.yml` | Chest tiers and flush interval |
| `database.yml` | Storage backend and connection pool |
| `hooks.yml` | Enable or disable each integration |
| `logs.yml` | File logging and debug mode |
| `theme.yml` | Plugin-wide color tokens |
| `lang/*.yml` | Every player-facing string |
| `menus/*.yml` | Per-menu GUI layout |

### Team chat

```yaml
# settings.yml
chat:
  # Placeholders: %tag%, %team%, %player%, %message%
  team-format: "&d[%tag%] &7%player%&8: &f%message%"
  # Mirror team chat to the console so staff can moderate it
  log-to-console: true
```

Team chat is cancelled at `LOWEST` event priority with its viewer list cleared, so other chat plugins and Discord bridges never see it as public chat. If team messages still appear in your Discord, verify that `ReportCanceledChatEvents` is `false` (the default) in DiscordSRV's `config.yml`.

### Theme

`theme.yml` defines MiniMessage tokens used across the entire plugin: `<primary>`, `<secondary>`, `<accent>`, `<muted>`, `<danger>`, `<success>`, `<warning>`, `<info>`. Changing one value recolors every message, title and GUI.

### Language

Set `language:` in `config.yml`. Missing keys fall back to `en_US` per key. To add a language, copy `lang/en_US.yml` to for example `lang/de_DE.yml`, translate it, set `language: de_DE` and run `/team reload`.

### GUI layout

Each menu file in `menus/` supports:

```yaml
title: "<primary>My Custom Title"   # MiniMessage and theme tokens
rows: 6
items:
  bank:
    slot: 31
    material: GOLD_BLOCK
    glow: true
    custom-model-data: 1234
    enabled: true                   # false hides the item
```

GUI text comes from `lang/*.yml` (`gui.*` keys); layout and wording are fully separated.

### Database

```yaml
# database.yml
type: SQLITE   # SQLITE | MYSQL | MARIADB | POSTGRESQL
host: localhost
port: 3306
database: newgenteam
username: root
password: ""
pool-size: 10
connection-timeout: 5000
```

SQLite is the zero-setup default, stored as `newgenteam.db`. MySQL, MariaDB and PostgreSQL use a HikariCP pool with bundled drivers. Invalid settings fall back to SQLite. All I/O runs on a dedicated storage thread and pending writes are drained on shutdown.

### Logging

With `logs.yml` enabled, files are written to `plugins/NewGenTeam/logs/<category>/<yyyy-MM-dd>.log`:

| Folder | Contents |
|---|---|
| `daily/` | General activity |
| `errors/` | Storage and hook failures |
| `transactions/` | Bank deposits and withdrawals |
| `admin/` | Admin actions and reloads |

Set `debug: true` for additional console diagnostics.

---

## PlaceholderAPI

Identifier: `newgenteam`. All placeholders read from the in-memory cache; no database queries are performed.

| Placeholder | Value |
|---|---|
| `%newgenteam_name%` | Team name (empty if none) |
| `%newgenteam_name_display%` | Team name or "No team" |
| `%newgenteam_tag%` | Team tag |
| `%newgenteam_role%` | Player's role |
| `%newgenteam_owner%` | Team owner's name |
| `%newgenteam_members%` / `%newgenteam_members_max%` | Member count / limit |
| `%newgenteam_online%` | Online members |
| `%newgenteam_level%` | Team tier |
| `%newgenteam_bank%` (alias `balance`) | Bank balance |
| `%newgenteam_kills%` / `%newgenteam_deaths%` / `%newgenteam_kdr%` | Combat statistics |
| `%newgenteam_rank%` | Leaderboard position |

---

## Integrations

| Plugin | Used for | Required |
|---|---|---|
| [PacketEvents](https://modrinth.com/plugin/packetevents) | Sign-based input (amounts, names) | Yes |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Team bank economy | No |
| [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/) | Tier upgrade currency | No |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | `%newgenteam_*%` placeholders | No |

Each integration can be toggled individually in `hooks.yml`.

---

## Developer API

Package `me.newgen.team.api.event` provides standard Bukkit events:

`TeamCreateEvent`, `TeamDeleteEvent`, `TeamJoinEvent`, `TeamLeaveEvent`, `TeamKickEvent`, `TeamLevelUpEvent`, `BankDepositEvent`, `BankWithdrawEvent`

```java
@EventHandler
public void onDeposit(BankDepositEvent event) {
    getLogger().info(event.getTeam().name() + " received " + event.getAmount());
}
```

---

## FAQ

**Does it run on Folia?**
Yes. Scheduling is region-aware throughout and `folia-supported: true` is declared.

**Team chat appears in Discord (DiscordSRV)?**
It should not: team chat is cancelled at `LOWEST` priority with its viewers cleared. If it still leaks, set `ReportCanceledChatEvents: false` in DiscordSRV's `config.yml` (this is the default).

**Can I switch databases later?**
Yes: change `database.yml` and restart. There is no built-in cross-database transfer, so migrate existing data manually if you already have teams.

**What happens to chest items when a tier is lowered?**
Items that no longer fit are retained in storage and reappear once capacity is restored.

**Where is the data stored?**
SQLite: `plugins/NewGenTeam/newgenteam.db`. External databases: wherever `database.yml` points.

---

## Building from source

```bash
git clone <this-repo>
cd NewGenTeam
./gradlew build      # Windows: gradlew.bat build
```

The shaded jar is produced at `build/libs/NewGenTeam-<version>.jar`.

---

## Support

- Bug reports and feature requests: [GitHub Issues](../../issues)
- Community and help: [Discord](https://discord.gg/twKAP7JjW)
- Anonymous usage metrics via [bStats](https://bstats.org/plugin/bukkit/NewGenTeam); opt out in `plugins/bStats/config.yml`

<div align="center">
<br>

Developed by **NewGen Studio**

</div>
