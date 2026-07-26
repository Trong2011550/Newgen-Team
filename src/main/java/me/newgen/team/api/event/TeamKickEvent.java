package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after a player has been kicked from a team. */
public final class TeamKickEvent extends TeamEvent {

    private final UUID target;
    private final UUID kicker;

    public TeamKickEvent(Team team, UUID target, UUID kicker) {
        super(team);
        this.target = target;
        this.kicker = kicker;
    }

    /** The player who was kicked. */
    public UUID target() {
        return target;
    }

    /** The player who performed the kick; may be null for admin/system actions. */
    public UUID kicker() {
        return kicker;
    }
}
