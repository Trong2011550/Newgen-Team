package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after a player has joined a team (invite accepted or join request approved). */
public final class TeamJoinEvent extends TeamEvent {

    private final UUID player;

    public TeamJoinEvent(Team team, UUID player) {
        super(team);
        this.player = player;
    }

    /** The player who joined the team. */
    public UUID player() {
        return player;
    }
}
