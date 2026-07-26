package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after a player has voluntarily left a team. */
public final class TeamLeaveEvent extends TeamEvent {

    private final UUID player;

    public TeamLeaveEvent(Team team, UUID player) {
        super(team);
        this.player = player;
    }

    /** The player who left the team. */
    public UUID player() {
        return player;
    }
}
