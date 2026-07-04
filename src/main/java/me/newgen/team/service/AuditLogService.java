package me.newgen.team.service;

import me.newgen.team.model.AuditAction;
import me.newgen.team.model.AuditLog;
import me.newgen.team.model.Team;
import me.newgen.team.storage.StorageProvider;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AuditLogService {

    public static final int MAX_HISTORY = 1000;

    private final StorageProvider storage;

    public AuditLogService(StorageProvider storage) {
        this.storage = storage;
    }

    public void record(UUID teamId, String teamName,
                       UUID actor, String actorName,
                       AuditAction action,
                       UUID target, String targetName,
                       String extra) {
        AuditLog entry = AuditLog.now(teamId, teamName, actor, actorName, action,
                target, targetName, extra);
        try {
            storage.saveLog(entry).exceptionally(ex -> null);
        } catch (Exception ignored) {

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
                .exceptionally(ex -> Collections.emptyList());
    }

    public void history(UUID teamId, Consumer<List<AuditLog>> onResult) {
        history(teamId).thenAccept(list ->
                onResult.accept(list == null ? Collections.emptyList() : list));
    }

    public CompletableFuture<Void> purge(UUID teamId) {
        if (teamId == null) return CompletableFuture.completedFuture(null);
        return storage.deleteLogs(teamId).exceptionally(ex -> null);
    }
}
