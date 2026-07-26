package me.newgen.team.model;

import me.newgen.team.model.TeamRole;
import java.util.UUID;

public final class TeamMember {
    private final UUID uuid;
    private volatile String name;
    private volatile TeamRole role;
    private final long joinedAt;

    public TeamMember(UUID uuid, String name, TeamRole role, long joinedAt) {
        this.uuid = uuid;
        this.name = name;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID uuid() {
        return this.uuid;
    }

    public String name() {
        return this.name;
    }

    public void name(String name) {
        this.name = name;
    }

    public TeamRole role() {
        return this.role;
    }

    public void role(TeamRole role) {
        this.role = role;
    }

    public long joinedAt() {
        return this.joinedAt;
    }
}
