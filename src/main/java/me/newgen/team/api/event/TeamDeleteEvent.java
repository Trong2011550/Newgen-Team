package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after a team has been disbanded/deleted. */
public final class TeamDeleteEvent extends TeamEvent {

    private final UUID actor;

    public TeamDeleteEvent(Team team, UUID actor) {
        super(team);
        this.actor = actor;
    }

    /** The player (or admin) who deleted the team; may be null for system actions. */
    public UUID actor() {
        return actor;
    }
}
