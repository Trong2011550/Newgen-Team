package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after a team has been created. */
public final class TeamCreateEvent extends TeamEvent {

    private final UUID creator;

    public TeamCreateEvent(Team team, UUID creator) {
        super(team);
        this.creator = creator;
    }

    /** The player who created the team. */
    public UUID creator() {
        return creator;
    }
}
