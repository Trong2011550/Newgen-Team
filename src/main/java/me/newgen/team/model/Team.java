package me.newgen.team.model;

import me.newgen.team.model.TeamHome;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamSettings;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class Team {
    private final UUID id;
    private volatile String name;
    private volatile String tag;
    private volatile UUID leader;
    private final long createdAt;
    private final Map<UUID, TeamMember> members = new ConcurrentHashMap<UUID, TeamMember>();
    private final TeamSettings settings;
    private volatile TeamHome home;
    private final Map<String, TeamHome> namedHomes = new ConcurrentHashMap<String, TeamHome>();
    private volatile int tier;
    private volatile long balance;
    private volatile int kills;
    private volatile int deaths;
    private final Map<UUID, RelationType> relations = new ConcurrentHashMap<UUID, RelationType>();
    public final ReentrantLock chestLock = new ReentrantLock();

    public Team(UUID id, String name, String tag, UUID leader, long createdAt, TeamSettings settings, int tier) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.createdAt = createdAt;
        this.settings = settings;
        this.tier = Math.max(1, tier);
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String tag() {
        return this.tag;
    }

    public void tag(String tag) {
        this.tag = tag;
    }

    public UUID leader() {
        return this.leader;
    }

    public void leader(UUID leader) {
        this.leader = leader;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public TeamSettings settings() {
        return this.settings;
    }

    public TeamHome home() {
        return this.home;
    }

    public void home(TeamHome home) {
        this.home = home;
    }

    public Map<String, TeamHome> namedHomes() {
        return this.namedHomes;
    }

    public int tier() {
        return this.tier;
    }

    public void tier(int t) {
        this.tier = Math.max(1, t);
    }

    public int chestLevel() {
        return this.tier;
    }

    public void chestLevel(int lvl) {
        this.tier = Math.max(1, lvl);
    }

    public long balance() {
        return this.balance;
    }

    public void balance(long b) {
        this.balance = b;
    }

    public int kills() {
        return this.kills;
    }

    public void kills(int k) {
        this.kills = k;
    }

    public int deaths() {
        return this.deaths;
    }

    public void deaths(int d) {
        this.deaths = d;
    }

    public double kdr() {
        return this.deaths == 0 ? (double)this.kills : (double)this.kills / (double)this.deaths;
    }

    public Map<UUID, TeamMember> membersMap() {
        return this.members;
    }

    public Collection<TeamMember> members() {
        return this.members.values();
    }

    public TeamMember member(UUID uuid) {
        return this.members.get(uuid);
    }

    public boolean isMember(UUID uuid) {
        return this.members.containsKey(uuid);
    }

    public void addMember(TeamMember m) {
        this.members.put(m.uuid(), m);
    }

    public void removeMember(UUID uuid) {
        this.members.remove(uuid);
    }

    public int size() {
        return this.members.size();
    }

    public Map<UUID, RelationType> relations() {
        return this.relations;
    }

    public static enum RelationType {
        ALLY,
        ENEMY;

    }
}
