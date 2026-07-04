package me.newgen.team.service;

import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import me.newgen.team.model.TeamRole;
import me.newgen.team.model.TeamSettings;
import me.newgen.team.storage.StorageProvider;

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

    public TeamService(StorageProvider storage) {
        this.storage = storage;
    }

    public void setTiers(TierService tiers) {
        this.tiers = tiers;
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
        ALREADY_REQUESTED, NO_REQUEST
    }

    public Result createTeam(UUID leader, String leaderName, String name, String tag) {
        if (hasTeam(leader)) return Result.ALREADY_IN_TEAM;
        if (!isValidName(name)) return Result.NAME_INVALID;
        name = name.trim();
        if (tag != null && tag.length() > maxTagLength) return Result.NAME_INVALID;
        if (nameIndex.containsKey(name.toLowerCase(Locale.ROOT))) return Result.NAME_TAKEN;

        UUID id = UUID.randomUUID();
        Team team = new Team(id, name, tag == null ? name : tag, leader,
                System.currentTimeMillis(), new TeamSettings(), 1);
        team.addMember(new TeamMember(leader, leaderName, TeamRole.OWNER, System.currentTimeMillis()));

        teams.put(id, team);
        nameIndex.put(name.toLowerCase(Locale.ROOT), id);
        playerTeam.put(leader, id);
        storage.saveTeam(team);
        return Result.SUCCESS;
    }

    public Result disbandTeam(Team team) {
        if (team == null) return Result.NOT_IN_TEAM;
        for (TeamMember m : team.members()) {
            playerTeam.remove(m.uuid());
        }
        teams.remove(team.id());
        nameIndex.remove(team.name().toLowerCase(Locale.ROOT));
        joinRequests.remove(team.id());

        for (Team other : teams.values()) {
            other.relations().remove(team.id());
        }
        storage.deleteTeam(team.id());
        return Result.SUCCESS;
    }

    public Result renameTeam(Team team, String newName) {
        if (!isValidName(newName)) return Result.NAME_INVALID;
        newName = newName.trim();
        String key = newName.toLowerCase(Locale.ROOT);
        UUID existing = nameIndex.get(key);
        if (existing != null && !existing.equals(team.id())) return Result.NAME_TAKEN;
        nameIndex.remove(team.name().toLowerCase(Locale.ROOT));
        team.name(newName);
        nameIndex.put(key, team.id());
        storage.saveTeam(team);
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
        if (hasTeam(player)) return Result.ALREADY_IN_TEAM;
        if (!hasInvite(player, teamId)) return Result.NOT_INVITED;
        Team team = teams.get(teamId);
        if (team == null) { clearInvite(player, teamId); return Result.NOT_INVITED; }
        if (team.size() >= memberLimit(team)) return Result.TEAM_FULL;
        team.addMember(new TeamMember(player, playerName, TeamRole.RECRUIT, System.currentTimeMillis()));
        playerTeam.put(player, teamId);
        clearInvite(player, teamId);
        storage.saveTeam(team);
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
            return false;
        }
        return true;
    }

    public Map<UUID, Long> joinRequestsFor(UUID teamId) {
        Map<UUID, Long> m = joinRequests.get(teamId);
        if (m == null) return Collections.emptyMap();
        long now = System.currentTimeMillis();
        m.entrySet().removeIf(e -> e.getValue() < now);
        return Collections.unmodifiableMap(m);
    }

    public String joinRequestName(UUID applicant) {
        return joinRequestNames.get(applicant);
    }

    public Result acceptJoinRequest(Team team, UUID applicant) {
        if (team == null) return Result.NOT_IN_TEAM;
        if (!hasJoinRequest(team.id(), applicant)) return Result.NO_REQUEST;
        if (hasTeam(applicant)) { clearJoinRequest(team.id(), applicant); return Result.ALREADY_IN_TEAM; }
        if (team.size() >= memberLimit(team)) return Result.TEAM_FULL;
        String name = joinRequestNames.get(applicant);
        team.addMember(new TeamMember(applicant, name, TeamRole.RECRUIT, System.currentTimeMillis()));
        playerTeam.put(applicant, team.id());
        clearJoinRequest(team.id(), applicant);
        storage.saveTeam(team);
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
        storage.saveTeam(team);
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
        storage.saveTeam(team);
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
        storage.saveTeam(team);
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
        storage.saveTeam(team);
        return Result.SUCCESS;
    }

    private boolean isValidName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        int len = trimmed.length();
        if (len < 3 || len > maxNameLength) return false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '\u00A7') return false;
        }
        return true;
    }

    public int maxMembers() { return maxMembers; }

    public void putMemberIndex(UUID player, UUID teamId) {
        playerTeam.put(player, teamId);
    }

    public void removeMemberIndex(UUID player) {
        playerTeam.remove(player);
    }

    public void saveAll() {
        for (Team t : teams.values()) storage.saveTeam(t);
    }
}
