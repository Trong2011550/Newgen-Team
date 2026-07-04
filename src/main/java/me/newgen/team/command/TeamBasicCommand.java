package me.newgen.team.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class TeamBasicCommand implements BasicCommand {

    private final TeamCommand delegate;

    public TeamBasicCommand(TeamCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        delegate.onCommand(sender, null, "team", args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        CommandSender sender = source.getSender();
        List<String> out = delegate.onTabComplete(sender, null, "team", args);
        return out == null ? List.of() : out;
    }
}
