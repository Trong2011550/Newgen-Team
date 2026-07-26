package me.newgen.team.service;

import me.newgen.team.model.Team;
import me.newgen.team.storage.StorageProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class RelationService {

    private final TeamService teams;
    private final StorageProvider storage;

    private final Map<UUID, Map<UUID, Long>> allyRequests = new ConcurrentHashMap<>();
    private final long requestTtlMillis = TimeUnit.MINUTES.toMillis(5);

    public RelationService(TeamService teams, StorageProvider storage) {
        this.teams = teams;
        this.storage = storage;
    }

    public enum Result {
        SUCCESS, REQUEST_SENT, NO_TARGET, SELF, ALREADY_ALLY, ALREADY_ENEMY,
        NOT_ALLY, NOT_ENEMY, NO_REQUEST, REQUESTS_DISABLED
    }

    public Team.RelationType relation(Team from, Team other) {
        if (from == null || other == null) return null;
        return from.relations().get(other.id());
    }

    public Result requestAlly(Team from, Team target) {
        if (target == null) return Result.NO_TARGET;
        if (from.id().equals(target.id())) return Result.SELF;
        if (from.relations().get(target.id()) == Team.RelationType.ALLY) return Result.ALREADY_ALLY;
        if (!target.settings().allyRequests) return Result.REQUESTS_DISABLED;

        Map<UUID, Long> ourPending = allyRequests.get(from.id());
        if (ourPending != null && isValid(ourPending.get(target.id()))) {
            ourPending.remove(target.id());
            return acceptAlly(from, target);
        }

        allyRequests.computeIfAbsent(target.id(), k -> new ConcurrentHashMap<>())
                .put(from.id(), System.currentTimeMillis() + requestTtlMillis);
        return Result.REQUEST_SENT;
    }

    public Result acceptAlly(Team accepter, Team requester) {
        if (requester == null) return Result.NO_TARGET;
        Map<UUID, Long> pending = allyRequests.get(accepter.id());
        boolean hasReq = pending != null && isValid(pending.get(requester.id()));

        if (!hasReq) {
            Map<UUID, Long> reverse = allyRequests.get(requester.id());
            if (reverse == null || !isValid(reverse.get(accepter.id()))) {
                return Result.NO_REQUEST;
            }
        }
        if (pending != null) pending.remove(requester.id());
        Map<UUID, Long> reverse = allyRequests.get(requester.id());
        if (reverse != null) reverse.remove(accepter.id());

        accepter.relations().put(requester.id(), Team.RelationType.ALLY);
        requester.relations().put(accepter.id(), Team.RelationType.ALLY);
        teams.persist(accepter);
        teams.persist(requester);
        return Result.SUCCESS;
    }

    public Result removeAlly(Team from, Team other) {
        if (other == null) return Result.NO_TARGET;
        if (from.relations().get(other.id()) != Team.RelationType.ALLY) return Result.NOT_ALLY;
        from.relations().remove(other.id());
        other.relations().remove(from.id());
        teams.persist(from);
        teams.persist(other);
        return Result.SUCCESS;
    }

    public Result setEnemy(Team from, Team target) {
        if (target == null) return Result.NO_TARGET;
        if (from.id().equals(target.id())) return Result.SELF;
        if (from.relations().get(target.id()) == Team.RelationType.ENEMY) return Result.ALREADY_ENEMY;

        if (from.relations().get(target.id()) == Team.RelationType.ALLY) {
            target.relations().remove(from.id());
            teams.persist(target);
        }
        from.relations().put(target.id(), Team.RelationType.ENEMY);
        teams.persist(from);
        return Result.SUCCESS;
    }

    public Result removeEnemy(Team from, Team target) {
        if (target == null) return Result.NO_TARGET;
        if (from.relations().get(target.id()) != Team.RelationType.ENEMY) return Result.NOT_ENEMY;
        from.relations().remove(target.id());
        teams.persist(from);
        return Result.SUCCESS;
    }

    /** Withdraws an ally request previously sent by {@code from} to {@code target}. */
    public Result cancelRequest(Team from, Team target) {
        if (target == null) return Result.NO_TARGET;
        Map<UUID, Long> pending = allyRequests.get(target.id());
        if (pending == null || pending.remove(from.id()) == null) return Result.NO_REQUEST;
        return Result.SUCCESS;
    }

    /** Drops expired ally requests; called from the periodic sweep task. */
    public void sweepExpired() {
        long now = System.currentTimeMillis();
        for (Map<UUID, Long> m : allyRequests.values()) {
            m.entrySet().removeIf(e -> e.getValue() < now);
        }
        allyRequests.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public Map<UUID, Long> pendingRequests(UUID teamId) {
        return allyRequests.getOrDefault(teamId, Map.of());
    }

    private boolean isValid(Long expiry) {
        return expiry != null && expiry > System.currentTimeMillis();
    }
}
