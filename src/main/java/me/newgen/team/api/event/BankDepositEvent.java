package me.newgen.team.api.event;

import me.newgen.team.model.Team;

import java.util.UUID;

/** Fired after money has been deposited into a team bank. */
public final class BankDepositEvent extends TeamEvent {

    private final UUID player;
    private final long amount;
    private final long newBalance;

    public BankDepositEvent(Team team, UUID player, long amount, long newBalance) {
        super(team);
        this.player = player;
        this.amount = amount;
        this.newBalance = newBalance;
    }

    /** The player who deposited the money. */
    public UUID player() {
        return player;
    }

    /** The amount deposited. */
    public long amount() {
        return amount;
    }

    /** Team balance after the deposit. */
    public long newBalance() {
        return newBalance;
    }
}
