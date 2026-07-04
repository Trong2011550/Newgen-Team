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
    @Override public @NotNull String getAuthor() { return "Trong"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        Team team = teams.byPlayer(player.getUniqueId());
        if (team == null) {
            return switch (params.toLowerCase()) {
                case "name_display" -> "Chưa có team";
                case "name", "tag", "role" -> "";
                case "members", "level", "kills", "deaths" -> "0";
                case "kdr" -> "0.0";
                case "balance" -> "0";
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
            case "members" -> String.valueOf(team.size());
            case "level" -> String.valueOf(team.chestLevel());
            case "kills" -> String.valueOf(team.kills());
            case "deaths" -> String.valueOf(team.deaths());
            case "kdr" -> String.format("%.2f", team.kdr());
            case "balance" -> String.valueOf(team.balance());
            default -> null;
        };
    }
}
