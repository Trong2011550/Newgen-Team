package me.newgen.team.storage;

import me.newgen.team.model.AuditLog;
import me.newgen.team.model.Team;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageProvider {

    CompletableFuture<Void> init();

    CompletableFuture<Collection<Team>> loadAllTeams();

    CompletableFuture<Void> saveTeam(Team team);

    CompletableFuture<Void> deleteTeam(UUID teamId);

    CompletableFuture<byte[]> loadChest(UUID teamId);

    CompletableFuture<Void> saveChest(UUID teamId, byte[] contents);

    CompletableFuture<Void> saveLog(AuditLog entry);

    CompletableFuture<List<AuditLog>> loadLogs(UUID teamId, int limit);

    CompletableFuture<Void> deleteLogs(UUID teamId);

    void shutdown();
}
