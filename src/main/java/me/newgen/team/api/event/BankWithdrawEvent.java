package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after money has been withdrawn from a team bank. */
public final class BankWithdrawEvent extends TeamEvent {

    private final UUID player;
    private final long amount;
    private final long newBalance;

    public BankWithdrawEvent(Team team, UUID player, long amount, long newBalance) {
        super(team);
        this.player = player;
        this.amount = amount;
        this.newBalance = newBalance;
    }

    /** The player who withdrew the money. */
    public UUID player() {
        return player;
    }

    /** The amount withdrawn. */
    public long amount() {
        return amount;
    }

    /** Team balance after the withdrawal. */
    public long newBalance() {
        return newBalance;
    }
}
