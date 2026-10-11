package fr.robie.mekanized.paper.command;

import fr.robie.mekanized.paper.automation.PaperAutomation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.robie.paperdispatch.command.BaseCommand;
import fr.robie.paperdispatch.command.CommandDispatch;
import fr.robie.paperdispatch.command.CommandResultType;
import fr.robie.paperdispatch.command.SubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * The {@code /mekanized} command: {@code frequency <name>} (players), {@code build <structure>} (admins) and
 * {@code radiation} ({@link RadiationCommands}: sources, levels and doses). The server-side checks are in {@link MekanizedDebugCommands} (Mekanized
 * content) and {@link EngineCheckCommands} (engine features). Built with Paper-Dispatch, Mekanized's
 * own relocated copy (D35).
 */
@SuppressWarnings("UnstableApiUsage")
public final class MekanizedCommands extends BaseCommand<Plugin> {
    private static final String USAGE = "Usage: /mekanized <frequency|build|radiation|test|check-...>";

    public MekanizedCommands(Plugin plugin) {
        super(plugin, "mekanized");
        setDescription("Mekanized commands");
        // Players set frequencies; the rest is for admins.
        addSubCommand(SubCommand.builder(plugin, "frequency").playerOnly()
                .addRequiredArgument("name", StringArgumentType.word())
                .executes(dispatch -> {
                    PaperAutomation.setFrequency((Player) dispatch.getSender(), dispatch.getArgument("name", String.class));
                    return CommandResultType.SUCCESS;
                }).build());
        addSubCommand(structures(plugin));
        addSubCommand(RadiationCommands.command(plugin));
        new MekanizedDebugCommands(plugin).commands().forEach(this::addSubCommand);
        new EngineCheckCommands(plugin).commands().forEach(this::addSubCommand);
    }

    /** {@code /mekanized build <structure> [<x y z>]}: a working multiblock, its lowest corner there. */
    private static SubCommand<Plugin> structures(Plugin plugin) {
        var build = SubCommand.builder(plugin, "build").permission(PositionSubCommand.ADMIN);
        for (String name : MekanizedStructures.STRUCTURES.keySet()) {
            build.addSubCommand(new PositionSubCommand(plugin, name, (context, world, x, y, z) -> {
                String problem = MekanizedStructures.build(name, world, x, y, z);
                reply(context, problem == null ? "mekanized build: " + name.replace('_', ' ') + " built at " + x + " " + y + " " + z
                        : "mekanized build: " + problem);
            }));
        }
        return build.executes(dispatch -> usage(dispatch, "Usage: /mekanized build <" + String.join("|", MekanizedStructures.STRUCTURES.keySet()) + "> [<x y z>] [world]"))
                .build();
    }

    @Override
    protected @NotNull CommandResultType perform(@NotNull CommandDispatch<Plugin> dispatch) {
        return usage(dispatch, USAGE);
    }

    private static CommandResultType usage(CommandDispatch<Plugin> dispatch, String usage) {
        dispatch.getSender().sendMessage(Component.text(usage, NamedTextColor.RED));
        return CommandResultType.FAILURE;
    }

    static void reply(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().getSender().sendMessage(message);
    }
}
