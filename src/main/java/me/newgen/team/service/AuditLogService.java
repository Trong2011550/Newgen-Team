package me.newgen.team.service;

import me.newgen.team.model.AuditAction;
import me.newgen.team.model.AuditLog;
import me.newgen.team.model.Team;
import me.newgen.team.storage.StorageProvider;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AuditLogService {

    public static final int MAX_HISTORY = 1000;

    private final StorageProvider storage;
    private final Logger log;

    public AuditLogService(StorageProvider storage, Logger log) {
        this.storage = storage;
        this.log = log;
    }

    public void record(UUID teamId, String teamName,
                       UUID actor, String actorName,
                       AuditAction action,
                       UUID target, String targetName,
                       String extra) {
        AuditLog entry = AuditLog.now(teamId, teamName, actor, actorName, action,
                target, targetName, extra);
        try {
            // Audit writes must not silently vanish: log failures so admins can spot a
            // broken audit trail instead of assuming nothing happened.
            storage.saveLog(entry).exceptionally(ex -> {
                log.log(Level.WARNING, "Failed to write audit log " + action + " for team "
                        + teamId + ": " + ex.getMessage(), ex);
                return null;
            });
        } catch (Exception ex) {
            log.log(Level.WARNING, "Failed to queue audit log " + action + " for team "
                    + teamId, ex);
        }
    }

    public void record(Team team, UUID actor, String actorName,
                       AuditAction action, UUID target, String targetName, String extra) {
        if (team == null) return;
        record(team.id(), team.name(), actor, actorName, action, target, targetName, extra);
    }

    public void record(Team team, UUID actor, String actorName, AuditAction action) {
        record(team, actor, actorName, action, null, null, null);
    }

    public void record(Team team, UUID actor, String actorName, AuditAction action, String extra) {
        record(team, actor, actorName, action, null, null, extra);
    }

    public CompletableFuture<List<AuditLog>> history(UUID teamId) {
        if (teamId == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return storage.loadLogs(teamId, MAX_HISTORY)
                .exceptionally(ex -> {
                    log.log(Level.WARNING, "Failed to load audit logs for team " + teamId
                            + ": " + ex.getMessage(), ex);
                    return Collections.emptyList();
                });
    }

    public CompletableFuture<Void> purge(UUID teamId) {
        if (teamId == null) return CompletableFuture.completedFuture(null);
        return storage.deleteLogs(teamId).exceptionally(ex -> {
            log.log(Level.WARNING, "Failed to purge audit logs for team " + teamId
                    + ": " + ex.getMessage(), ex);
            return null;
        });
    }
}
