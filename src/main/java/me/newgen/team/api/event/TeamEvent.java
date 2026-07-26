package me.newgen.team.api.event;

import me.newgen.team.model.Team;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all NewGen Team API events.
 * Fired synchronously on the region/global thread that performed the action.
 */
public abstract class TeamEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Team team;

    protected TeamEvent(Team team) {
        this.team = team;
    }

    /** The team involved in this event. */
    public Team team() {
        return team;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
