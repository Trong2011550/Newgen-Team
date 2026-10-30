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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    // Leaderboard ranks are recomputed at most once per RANK_CACHE_MS. %newgenteam_rank%
    // can be requested for every player on every scoreboard refresh, and a full sort of
    // all teams per request is an O(n log n) main-thread cost we do not want to repeat.
    private static final long RANK_CACHE_MS = 5000L;
    private volatile Map<UUID, Integer> rankCache = Collections.emptyMap();
    private volatile long rankCacheAt = 0L;

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

    /** 1-based leaderboard position by kills, from a short-lived cached ranking. */
    private int rankOf(Team team) {
        long now = System.currentTimeMillis();
        Map<UUID, Integer> cache = rankCache;
        if (cache.isEmpty() || now - rankCacheAt > RANK_CACHE_MS) {
            List<Team> all = new ArrayList<>(teams.all());
            all.sort(Comparator.comparingInt((Team t) -> t.kills()).reversed());
            Map<UUID, Integer> next = new HashMap<>(all.size() * 2);
            for (int i = 0; i < all.size(); i++) next.put(all.get(i).id(), i + 1);
            rankCache = next;
            rankCacheAt = now;
            cache = next;
        }
        Integer rank = cache.get(team.id());
        return rank == null ? 0 : rank;
    }
}
