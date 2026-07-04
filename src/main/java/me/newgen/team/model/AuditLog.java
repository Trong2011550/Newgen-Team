/*
 * Decompiled with CFR 0.152.
 */
package me.newgen.team.model;

import me.newgen.team.model.AuditAction;
import java.util.UUID;

public record AuditLog(long id, long timestamp, UUID teamId, String teamName, UUID actor, String actorName, AuditAction action, UUID target, String targetName, String extra) {
    public static AuditLog now(UUID teamId, String teamName, UUID actor, String actorName, AuditAction action, UUID target, String targetName, String extra) {
        return new AuditLog(0L, System.currentTimeMillis(), teamId, teamName, actor, actorName, action, target, targetName, extra);
    }
}
