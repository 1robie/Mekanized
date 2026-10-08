package fr.robie.machineengine.mekanized.paper.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.robie.machineengine.api.paper.PositionArguments;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.paperdispatch.command.CommandDispatch;
import fr.robie.paperdispatch.command.CommandResultType;
import fr.robie.paperdispatch.command.SubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * An admin {@code /mekanized} sub-command at {@code [<x y z>] [<world>]} ({@link PositionArguments}, the
 * block above the one looked at by default), run on the thread owning that position because it
 * touches the world there. Arguments given to the constructor come before the position.
 */
@SuppressWarnings("UnstableApiUsage")
final class PositionSubCommand extends SubCommand<Plugin> {
    static final String ADMIN = "machineengine.admin";

    /** What the command does at the position, on the thread owning it. */
    @FunctionalInterface
    interface Handler {
        void run(@NotNull CommandContext<CommandSourceStack> context, @NotNull World world, int x, int y, int z);
    }

    private final Handler handler;

    @SafeVarargs
    PositionSubCommand(Plugin plugin, String name, Handler handler, ArgumentBuilder<CommandSourceStack, ?>... before) {
        super(plugin, name);
        this.handler = handler;
        setPermission(ADMIN);
        for (ArgumentBuilder<CommandSourceStack, ?> argument : before) addRequiredArgument(argument);
        addOptionalArgument(PositionArguments.positionArgument());
        addOptionalArgument(PositionArguments.worldArgument());
    }

    @Override
    protected @NotNull CommandResultType perform(@NotNull CommandDispatch<Plugin> dispatch) {
        CommandContext<CommandSourceStack> context = dispatch.getContext();
        PositionArguments.Position at = PositionArguments.resolve(context, PositionArguments.Default.PLACEMENT);
        if (at == null) return CommandResultType.FAILURE;
        PaperWorld.scheduler().run(new Location(at.world(), at.x(), at.y(), at.z()),
                () -> this.handler.run(context, at.world(), at.x(), at.y(), at.z()));
        return CommandResultType.SUCCESS;
    }
}
