package me.newgen.team.hook;

import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.service.TeamService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PlaceholderAPI expansion. Identifier: newgenteam.
 *
 * Placeholders (all read from the in-memory team cache; no database queries):
 *   %newgenteam_name%          - team name (empty when not in a team)
 *   %newgenteam_name_display%  - team name or the "no team" text
 *   %newgenteam_tag%           - team tag
 *   %newgenteam_role%          - the player's role display name
 *   %newgenteam_owner%         - team owner's name
 *   %newgenteam_members%       - current member count
 *   %newgenteam_members_max%   - member limit for the current tier
 *   %newgenteam_online%        - online member count
 *   %newgenteam_level%         - team tier
 *   %newgenteam_bank%          - team bank balance (alias: balance)
 *   %newgenteam_kills%         - team kills
 *   %newgenteam_deaths%        - team deaths
 *   %newgenteam_kdr%           - kill/death ratio
 *   %newgenteam_rank%          - leaderboard position by kills (1 = best)
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final Plugin plugin;
    private final TeamService teams;
    private boolean registered;

    public PlaceholderHook(Plugin plugin, TeamService teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    public boolean tryRegister() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return false;
        }
        registered = register();
        return registered;
    }

    public boolean hooked() { return registered; }

    @Override public @NotNull String getIdentifier() { return "newgenteam"; }
    @Override public @NotNull String getAuthor() { return "NewGen"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        Team team = teams.byPlayer(player.getUniqueId());
        if (team == null) {
            return switch (params.toLowerCase()) {
                case "name_display" -> "No team";
                case "name", "tag", "role", "owner" -> "";
                case "members", "members_max", "online", "level",
                     "kills", "deaths", "balance", "bank", "rank" -> "0";
                case "kdr" -> "0.00";
                default -> null;
            };
        }
        return switch (params.toLowerCase()) {
            case "name", "name_display" -> team.name();
            case "tag" -> team.tag();
            case "role" -> {
                TeamMember m = team.member(player.getUniqueId());
                yield m == null ? "" : m.role().display();
            }
            case "owner" -> {
                TeamMember leader = team.member(team.leader());
                yield leader == null || leader.name() == null ? "" : leader.name();
            }
            case "members" -> String.valueOf(team.size());
            case "members_max" -> String.valueOf(teams.memberLimit(team));
            case "online" -> String.valueOf(onlineCount(team));
            case "level" -> String.valueOf(team.tier());
            case "kills" -> String.valueOf(team.kills());
            case "deaths" -> String.valueOf(team.deaths());
            case "kdr" -> String.format("%.2f", team.kdr());
            case "balance", "bank" -> String.valueOf(team.balance());
            case "rank" -> String.valueOf(rankOf(team));
            default -> null;
        };
    }

    private int onlineCount(Team team) {
        int online = 0;
        for (TeamMember m : team.members()) {
            if (Bukkit.getPlayer(m.uuid()) != null) online++;
        }
        return online;
    }

    /** 1-based leaderboard position by kills, computed from the in-memory cache. */
    private int rankOf(Team team) {
        List<Team> all = new ArrayList<>(teams.all());
        all.sort(Comparator.comparingInt((Team t) -> t.kills()).reversed());
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(team.id())) return i + 1;
        }
        return 0;
    }
}
