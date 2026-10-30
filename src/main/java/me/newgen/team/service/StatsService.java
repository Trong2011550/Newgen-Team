package me.newgen.team.service;

import me.newgen.team.model.Team;
import me.newgen.team.storage.StorageProvider;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsService {

    private final TeamService teams;
    private final StorageProvider storage;

    public StatsService(TeamService teams, StorageProvider storage) {
        this.teams = teams;
        this.storage = storage;
    }

    public enum TopType { LEVEL, KILLS, DEATHS, KDR, BALANCE, MEMBERS }

    // Leaderboard snapshots are cached per type for a short TTL. Computing top()/rank()
    // sorts every team, and both are hit on the main thread from menus and placeholders.
    private static final long CACHE_MS = 5000L;
    private final Map<TopType, Snapshot> cache = new ConcurrentHashMap<>();

    private static final class Snapshot {
        final List<Team> list;
        final Map<UUID, Integer> rank;
        final long at;
        Snapshot(List<Team> list, Map<UUID, Integer> rank, long at) {
            this.list = list; this.rank = rank; this.at = at;
        }
    }

    private Snapshot snapshot(TopType type) {
        long now = System.currentTimeMillis();
        Snapshot s = cache.get(type);
        if (s != null && now - s.at <= CACHE_MS) return s;
        Comparator<Team> cmp = switch (type) {
            case LEVEL -> Comparator.comparingInt(Team::chestLevel);
            case KILLS -> Comparator.comparingInt(Team::kills);
            case DEATHS -> Comparator.comparingInt(Team::deaths);
            case KDR -> Comparator.comparingDouble(Team::kdr);
            case BALANCE -> Comparator.comparingLong(Team::balance);
            case MEMBERS -> Comparator.comparingInt(Team::size);
        };
        List<Team> list = teams.all().stream().sorted(cmp.reversed()).toList();
        Map<UUID, Integer> rank = new HashMap<>(list.size() * 2);
        for (int i = 0; i < list.size(); i++) rank.put(list.get(i).id(), i + 1);
        Snapshot next = new Snapshot(list, rank, now);
        cache.put(type, next);
        return next;
    }

    /**
     * Teams with stat changes not yet written. Mutations update the in-memory
     * team (the source of truth read by top()/rank()/placeholders) immediately
     * and only mark it dirty; a periodic flush coalesces many kills/deaths per
     * team into one write. On a clean shutdown TeamService.saveAll() persists
     * everything, so nothing is lost - only a hard crash inside one flush window
     * can drop the most recent increments.
     */
    private final java.util.Set<UUID> dirty = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void addKill(UUID killer) {
        Team team = teams.byPlayer(killer);
        if (team == null) return;
        // Guards concurrent increments from different region threads.
        synchronized (team) {
            team.kills(team.kills() + 1);
        }
        dirty.add(team.id());
    }

    public void addDeath(UUID victim) {
        Team team = teams.byPlayer(victim);
        if (team == null) return;
        synchronized (team) {
            team.deaths(team.deaths() + 1);
        }
        dirty.add(team.id());
    }

    /** Writes all teams with pending stat changes. Safe to call from an async thread. */
    public void flushDirty() {
        if (dirty.isEmpty()) return;
        for (UUID id : new java.util.ArrayList<>(dirty)) {
            if (!dirty.remove(id)) continue; // claimed by a concurrent flush
            Team team = teams.byId(id);
            if (team != null) teams.persist(team);
        }
    }

    public List<Team> top(TopType type, int limit) {
        List<Team> all = snapshot(type).list;
        int n = Math.max(1, limit);
        if (n >= all.size()) return all;
        return List.copyOf(all.subList(0, n));
    }

    public int rank(Team team, TopType type) {
        Integer r = snapshot(type).rank.get(team.id());
        return r == null ? -1 : r;
    }
}
