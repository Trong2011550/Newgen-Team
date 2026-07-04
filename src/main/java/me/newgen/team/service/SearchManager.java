package me.newgen.team.service;

import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SearchManager {

    private static final int MAX_RESULTS = 90;

    private final TeamService teams;

    public SearchManager(TeamService teams) {
        this.teams = teams;
    }

    public record PlayerHit(UUID uuid, String name, Team team, boolean online) {}

    public List<Team> searchTeams(String query) {
        String q = normalize(query);
        List<Team> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (Team t : teams.all()) {
            if (out.size() >= MAX_RESULTS) break;
            if (normalize(t.name()).contains(q) || normalize(t.tag()).contains(q)) {
                out.add(t);
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    public List<PlayerHit> searchPlayers(String query) {
        String q = normalize(query);
        if (q.isEmpty()) return new ArrayList<>();

        Map<UUID, PlayerHit> hits = new LinkedHashMap<>();

        for (Team t : teams.all()) {
            for (TeamMember m : t.members()) {
                if (hits.size() >= MAX_RESULTS) break;
                if (m.name() != null && normalize(m.name()).contains(q)) {
                    boolean online = Bukkit.getPlayer(m.uuid()) != null;
                    hits.putIfAbsent(m.uuid(), new PlayerHit(m.uuid(), m.name(), t, online));
                }
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hits.size() >= MAX_RESULTS) break;
            if (normalize(p.getName()).contains(q) && !hits.containsKey(p.getUniqueId())) {
                Team team = teams.byPlayer(p.getUniqueId());
                hits.put(p.getUniqueId(), new PlayerHit(p.getUniqueId(), p.getName(), team, true));
            }
        }

        List<PlayerHit> out = new ArrayList<>(hits.values());
        out.sort((a, b) -> {
            boolean ax = a.name().equalsIgnoreCase(query);
            boolean bx = b.name().equalsIgnoreCase(query);
            if (ax != bx) return ax ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }
}
