package me.newgen.team.service;

import me.newgen.team.model.Team;
import me.newgen.team.storage.StorageProvider;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class StatsService {

    private final TeamService teams;
    private final StorageProvider storage;

    public StatsService(TeamService teams, StorageProvider storage) {
        this.teams = teams;
        this.storage = storage;
    }

    public enum TopType { LEVEL, KILLS, DEATHS, KDR, BALANCE, MEMBERS }

    public void addKill(UUID killer) {
        Team team = teams.byPlayer(killer);
        if (team == null) return;
        team.kills(team.kills() + 1);
        storage.saveTeam(team);
    }

    public void addDeath(UUID victim) {
        Team team = teams.byPlayer(victim);
        if (team == null) return;
        team.deaths(team.deaths() + 1);
        storage.saveTeam(team);
    }

    public List<Team> top(TopType type, int limit) {
        Comparator<Team> cmp = switch (type) {
            case LEVEL -> Comparator.comparingInt(Team::chestLevel);
            case KILLS -> Comparator.comparingInt(Team::kills);
            case DEATHS -> Comparator.comparingInt(Team::deaths);
            case KDR -> Comparator.comparingDouble(Team::kdr);
            case BALANCE -> Comparator.comparingLong(Team::balance);
            case MEMBERS -> Comparator.comparingInt(Team::size);
        };
        return teams.all().stream()
                .sorted(cmp.reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public int rank(Team team, TopType type) {
        List<Team> all = top(type, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(team.id())) return i + 1;
        }
        return -1;
    }
}
