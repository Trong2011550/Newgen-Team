package me.newgen.team.service;

import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamRole;
import me.newgen.team.model.TeamSettings;
import me.newgen.team.api.event.TeamCreateEvent;
import me.newgen.team.api.event.TeamDeleteEvent;
import me.newgen.team.api.event.TeamJoinEvent;
import me.newgen.team.api.event.TeamKickEvent;
import me.newgen.team.api.event.TeamLeaveEvent;
import me.newgen.team.storage.StorageProvider;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class TeamService {

    private final StorageProvider storage;

    private final Map<UUID, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerTeam = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    private final Map<UUID, Map<UUID, Long>> invites = new ConcurrentHashMap<>();
    private long inviteTtlMillis = TimeUnit.MINUTES.toMillis(2);

    private final Map<UUID, Map<UUID, Long>> joinRequests = new ConcurrentHashMap<>();

    private final Map<UUID, String> joinRequestNames = new ConcurrentHashMap<>();

    private int maxNameLength = 16;
    private int maxTagLength = 6;
    private int maxMembers = 30;

    private TierService tiers;
    private ChestService chests;

    public TeamService(StorageProvider storage) {
        this.storage = storage;
    }

    public void setTiers(TierService tiers) {
        this.tiers = tiers;
    }

    public void setChests(ChestService chests) {
        this.chests = chests;
    }

    private LogService logs;

    public void setLogs(LogService logs) {
        this.logs = logs;
    }

    /** Persists a team; failures are reported to the error log. */
    public void persist(Team team) {
        // Saving a team that was disbanded meanwhile would resurrect its row.
        if (ready && !teams.containsKey(team.id())) {
            if (logs != null) {
                logs.error("Dropped persist for disbanded team " + team.name() + " (" + team.id() + ")");
            }
            return;
        }
        storage.saveTeam(team).exceptionally(ex -> {
            if (logs != null) {
                logs.error("Failed to persist team " + team.name() + " (" + team.id() + "): " + ex.getMessage());
            }
            return null;
        });
    }

    public int memberLimit(Team team) {
        if (tiers != null && team != null) return tiers.memberLimit(team);
        return maxMembers;
    }

    public void configure(int maxNameLength, int maxTagLength, int maxMembers, long inviteTtlSeconds) {
        this.maxNameLength = maxNameLength;
        this.maxTagLength = maxTagLength;
        this.maxMembers = maxMembers;
        this.inviteTtlMillis = TimeUnit.SECONDS.toMillis(inviteTtlSeconds);
    }

    /** False until loadAll() has populated the in-memory maps. */
    private volatile boolean ready = false;

    public boolean isReady() { return ready; }

    public void loadAll(Collection<Team> loaded) {
        teams.clear();
        playerTeam.clear();
        nameIndex.clear();
        for (Team t : loaded) {
            teams.put(t.id(), t);
            nameIndex.put(t.name().toLowerCase(Locale.ROOT), t.id());
            for (TeamMember m : t.members()) {
                playerTeam.put(m.uuid(), t.id());
            }
        }
        ready = true;
    }

    public Team byId(UUID id) { return teams.get(id); }

    public Team byPlayer(UUID player) {
        UUID tid = playerTeam.get(player);
        return tid == null ? null : teams.get(tid);
    }

    public Team byName(String name) {
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : teams.get(id);
    }

    public boolean hasTeam(UUID player) { return playerTeam.containsKey(player); }

    public Collection<Team> all() { return Collections.unmodifiableCollection(teams.values()); }

    public enum Result {
        SUCCESS, ALREADY_IN_TEAM, NAME_TAKEN, NAME_INVALID, TEAM_FULL,
        NOT_IN_TEAM, NOT_INVITED, NO_PERMISSION, TARGET_NOT_FOUND,
        CANNOT_TARGET_SELF, INVITE_EXPIRED, NOT_LEADER, TARGET_IN_TEAM,
        ALREADY_REQUESTED, NO_REQUEST, NOT_READY
    }

    public Result createTeam(UUID leader, String leaderName, String name, String tag) {
        // No writes until loadAll() has finished.
        if (!ready) return Result.NOT_READY;
        if (hasTeam(leader)) return Result.ALREADY_IN_TEAM;
        if (!isValidName(name)) return Result.NAME_INVALID;
        name = name.trim();
        if (tag != null && tag.length() > maxTagLength) return Result.NAME_INVALID;
        UUID id = UUID.randomUUID();
        // Two concurrent creates with the same name cannot both pass.
        if (nameIndex.putIfAbsent(name.toLowerCase(Locale.ROOT), id) != null) return Result.NAME_TAKEN;

        // A missing tag falls back to the truncated name.
        String effectiveTag = tag == null
                ? name.substring(0, Math.min(name.length(), maxTagLength)) : tag;
        Team team = new Team(id, name, effectiveTag, leader,
                System.currentTimeMillis(), new TeamSettings(), 1);
        team.addMember(new TeamMember(leader, leaderName, TeamRole.OWNER, System.currentTimeMillis()));

        teams.put(id, team);
        playerTeam.put(leader, id);
        persist(team);
        Bukkit.getPluginManager().callEvent(new TeamCreateEvent(team, leader));
        return Result.SUCCESS;
    }

    public Result disbandTeam(Team team) {
        if (team == null) return Result.NOT_IN_TEAM;
        // Serialized against acceptInvite/acceptJoinRequest.
        synchronized (team) {
            teams.remove(team.id());
            for (TeamMember m : team.members()) {
                playerTeam.remove(m.uuid(), team.id());
            }
        }
        nameIndex.remove(team.name().toLowerCase(Locale.ROOT), team.id());
        Map<UUID, Long> reqs = joinRequests.remove(team.id());
        if (reqs != null) {
            for (UUID applicant : reqs.keySet()) joinRequestNames.remove(applicant);
        }
        // Flush and drop the cached chest before deleteTeam.
        if (chests != null) chests.invalidate(team);

        for (Team other : teams.values()) {
            // Persist teams whose relations changed.
            if (other.relations().remove(team.id()) != null) {
                persist(other);
            }
        }
        storage.deleteTeam(team.id()).exceptionally(ex -> {
            if (logs != null) {
                logs.error("Failed to delete team " + team.name() + " (" + team.id() + ") from storage: " + ex.getMessage());
            }
            return null;
        });
        Bukkit.getPluginManager().callEvent(new TeamDeleteEvent(team, null));
        return Result.SUCCESS;
    }

    public Result renameTeam(Team team, String newName) {
        if (team == null || !teams.containsKey(team.id())) return Result.NOT_IN_TEAM;
        if (!isValidName(newName)) return Result.NAME_INVALID;
        newName = newName.trim();
        String key = newName.toLowerCase(Locale.ROOT);
        String oldKey = team.name().toLowerCase(Locale.ROOT);
        if (!key.equals(oldKey)) {
            // Claim the new name first; only one concurrent rename wins.
            UUID existing = nameIndex.putIfAbsent(key, team.id());
            if (existing != null && !existing.equals(team.id())) return Result.NAME_TAKEN;
            nameIndex.remove(oldKey, team.id());
        }
        team.name(newName);
        persist(team);
        return Result.SUCCESS;
    }

    public Result invite(Team team, UUID target) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (team.size() >= memberLimit(team)) return Result.TEAM_FULL;
        if (hasTeam(target)) return Result.TARGET_IN_TEAM;
        invites.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                .put(team.id(), System.currentTimeMillis() + inviteTtlMillis);
        return Result.SUCCESS;
    }

    public boolean hasInvite(UUID player, UUID teamId) {
        Map<UUID, Long> m = invites.get(player);
        if (m == null) return false;
        Long exp = m.get(teamId);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            m.remove(teamId);
            return false;
        }
        return true;
    }

    public Map<UUID, Long> invitesFor(UUID player) {
        return invites.getOrDefault(player, Collections.emptyMap());
    }

    public Result acceptInvite(UUID player, String playerName, UUID teamId) {
        if (!hasInvite(player, teamId)) return Result.NOT_INVITED;
        Team team = teams.get(teamId);
        if (team == null) { clearInvite(player, teamId); return Result.NOT_INVITED; }
        // Claim the player slot first; two concurrent accepts cannot both add.
        if (playerTeam.putIfAbsent(player, teamId) != null) return Result.ALREADY_IN_TEAM;
        // Concurrent joins must not push the team past its limit.
        synchronized (team) {
            if (teams.get(teamId) != team) {
                // Team was disbanded between the invite check and here.
                playerTeam.remove(player, teamId);
                clearInvite(player, teamId);
                return Result.NOT_INVITED;
            }
            if (team.size() >= memberLimit(team)) {
                playerTeam.remove(player, teamId);
                return Result.TEAM_FULL;
            }
            team.addMember(new TeamMember(player, playerName, TeamRole.RECRUIT, System.currentTimeMillis()));
        }
        clearInvite(player, teamId);
        persist(team);
        Bukkit.getPluginManager().callEvent(new TeamJoinEvent(team, player));
        return Result.SUCCESS;
    }

    public Result declineInvite(UUID player, UUID teamId) {
        if (!hasInvite(player, teamId)) return Result.NOT_INVITED;
        clearInvite(player, teamId);
        return Result.SUCCESS;
    }

    private void clearInvite(UUID player, UUID teamId) {
        Map<UUID, Long> m = invites.get(player);
        if (m != null) {
            m.remove(teamId);
            if (m.isEmpty()) invites.remove(player);
        }
    }

    public Result requestJoin(UUID applicant, String applicantName, Team team) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (hasTeam(applicant)) return Result.ALREADY_IN_TEAM;
        if (team.isMember(applicant)) return Result.ALREADY_IN_TEAM;
        if (team.size() >= memberLimit(team)) return Result.TEAM_FULL;
        Map<UUID, Long> reqs = joinRequests.computeIfAbsent(team.id(), k -> new ConcurrentHashMap<>());
        if (hasJoinRequest(team.id(), applicant)) return Result.ALREADY_REQUESTED;
        reqs.put(applicant, System.currentTimeMillis() + inviteTtlMillis);
        if (applicantName != null) joinRequestNames.put(applicant, applicantName);
        return Result.SUCCESS;
    }

    public boolean hasJoinRequest(UUID teamId, UUID applicant) {
        Map<UUID, Long> m = joinRequests.get(teamId);
        if (m == null) return false;
        Long exp = m.get(applicant);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            m.remove(applicant);
            joinRequestNames.remove(applicant);
            return false;
        }
        return true;
    }

    public Map<UUID, Long> joinRequestsFor(UUID teamId) {
        Map<UUID, Long> m = joinRequests.get(teamId);
        if (m == null) return Collections.emptyMap();
        long now = System.currentTimeMillis();
        m.entrySet().removeIf(e -> {
            if (e.getValue() < now) {
                joinRequestNames.remove(e.getKey());
                return true;
            }
            return false;
        });
        return Collections.unmodifiableMap(m);
    }

    public String joinRequestName(UUID applicant) {
        return joinRequestNames.get(applicant);
    }

    public Result acceptJoinRequest(Team team, UUID applicant) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (!hasJoinRequest(team.id(), applicant)) return Result.NO_REQUEST;
        String name = joinRequestNames.get(applicant);
        // Claim the player slot first; two concurrent accepts cannot both add.
        if (playerTeam.putIfAbsent(applicant, team.id()) != null) {
            clearJoinRequest(team.id(), applicant);
            return Result.ALREADY_IN_TEAM;
        }
        // Concurrent joins must not push the team past its limit.
        synchronized (team) {
            if (teams.get(team.id()) != team) {
                // Team was disbanded between the request check and here.
                playerTeam.remove(applicant, team.id());
                clearJoinRequest(team.id(), applicant);
                return Result.NO_REQUEST;
            }
            if (team.size() >= memberLimit(team)) {
                playerTeam.remove(applicant, team.id());
                return Result.TEAM_FULL;
            }
            team.addMember(new TeamMember(applicant, name, TeamRole.RECRUIT, System.currentTimeMillis()));
        }
        clearJoinRequest(team.id(), applicant);
        persist(team);
        Bukkit.getPluginManager().callEvent(new TeamJoinEvent(team, applicant));
        return Result.SUCCESS;
    }

    public Result declineJoinRequest(Team team, UUID applicant) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (!hasJoinRequest(team.id(), applicant)) return Result.NO_REQUEST;
        clearJoinRequest(team.id(), applicant);
        return Result.SUCCESS;
    }

    private void clearJoinRequest(UUID teamId, UUID applicant) {
        Map<UUID, Long> m = joinRequests.get(teamId);
        if (m != null) {
            m.remove(applicant);
            if (m.isEmpty()) joinRequests.remove(teamId);
        }
        joinRequestNames.remove(applicant);
    }

    public Result leave(Team team, UUID player) {
        if (team == null || !team.isMember(player)) return Result.NOT_IN_TEAM;
        if (team.leader().equals(player)) return Result.NOT_LEADER;
        team.removeMember(player);
        playerTeam.remove(player);
        persist(team);
        Bukkit.getPluginManager().callEvent(new TeamLeaveEvent(team, player));
        return Result.SUCCESS;
    }

    public Result kick(Team team, UUID actor, UUID target) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (actor.equals(target)) return Result.CANNOT_TARGET_SELF;
        TeamMember actorM = team.member(actor);
        TeamMember targetM = team.member(target);
        if (actorM == null || targetM == null) return Result.TARGET_NOT_FOUND;
        if (!actorM.role().canManage(targetM.role())) return Result.NO_PERMISSION;
        team.removeMember(target);
        playerTeam.remove(target);
        persist(team);
        Bukkit.getPluginManager().callEvent(new TeamKickEvent(team, target, actor));
        return Result.SUCCESS;
    }

    public Result setRole(Team team, UUID actor, UUID target, TeamRole newRole) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (actor.equals(target)) return Result.CANNOT_TARGET_SELF;
        TeamMember actorM = team.member(actor);
        TeamMember targetM = team.member(target);
        if (actorM == null || targetM == null) return Result.TARGET_NOT_FOUND;
        if (newRole == TeamRole.OWNER) return Result.NO_PERMISSION;

        if (!actorM.role().canManage(targetM.role()) || !actorM.role().canManage(newRole)) {
            return Result.NO_PERMISSION;
        }
        targetM.role(newRole);
        persist(team);
        return Result.SUCCESS;
    }

    public Result transferLeader(Team team, UUID currentLeader, UUID newLeader) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (!team.leader().equals(currentLeader)) return Result.NOT_LEADER;
        if (currentLeader.equals(newLeader)) return Result.CANNOT_TARGET_SELF;
        TeamMember newM = team.member(newLeader);
        if (newM == null) return Result.TARGET_NOT_FOUND;
        TeamMember oldM = team.member(currentLeader);
        if (oldM != null) oldM.role(TeamRole.CO_OWNER);
        newM.role(TeamRole.OWNER);
        team.leader(newLeader);
        persist(team);
        return Result.SUCCESS;
    }

    private boolean isValidName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        int len = trimmed.length();
        if (len < 3 || len > maxNameLength) return false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            // '<' and '>' are blocked: names are substituted into MiniMessage templates.
            if (c < 0x20 || c == 0x7F || c == '\u00A7' || c == '<' || c == '>') return false;
        }
        return true;
    }

    /** Drops expired invites and join requests. */
    public void sweepExpired() {
        long now = System.currentTimeMillis();
        for (Map<UUID, Long> m : invites.values()) {
            m.entrySet().removeIf(e -> e.getValue() < now);
        }
        invites.entrySet().removeIf(e -> e.getValue().isEmpty());
        for (Map<UUID, Long> m : joinRequests.values()) {
            m.entrySet().removeIf(e -> {
                if (e.getValue() < now) {
                    joinRequestNames.remove(e.getKey());
                    return true;
                }
                return false;
            });
        }
        joinRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public int maxMembers() { return maxMembers; }

    public void putMemberIndex(UUID player, UUID teamId) {
        playerTeam.put(player, teamId);
    }

    public void removeMemberIndex(UUID player) {
        playerTeam.remove(player);
    }

    public void saveAll() {
        for (Team t : teams.values()) persist(t);
    }
}
