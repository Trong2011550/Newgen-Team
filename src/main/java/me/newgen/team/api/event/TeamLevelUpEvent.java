package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after a team has been upgraded to a new tier. */
public final class TeamLevelUpEvent extends TeamEvent {

    private final UUID actor;
    private final int oldTier;
    private final int newTier;

    public TeamLevelUpEvent(Team team, UUID actor, int oldTier, int newTier) {
        super(team);
        this.actor = actor;
        this.oldTier = oldTier;
        this.newTier = newTier;
    }

    /** The player who paid for / triggered the upgrade; may be null for admin actions. */
    public UUID actor() {
        return actor;
    }

    /** Tier before the upgrade. */
    public int oldTier() {
        return oldTier;
    }

    /** Tier after the upgrade. */
    public int newTier() {
        return newTier;
    }
}
