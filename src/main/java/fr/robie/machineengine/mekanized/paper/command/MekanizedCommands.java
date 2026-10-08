package fr.robie.machineengine.mekanized.paper.command;

import fr.robie.machineengine.mekanized.paper.automation.PaperAutomation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.paper.PositionArguments;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The {@code /mekanized} command: {@code frequency <name>} (players), {@code build <structure>} and
 * {@code radiation} (admins). The server-side checks are in {@link MekanizedDebugCommands} (Mekanized
 * content) and {@link EngineCheckCommands} (engine features).
 */
@SuppressWarnings("UnstableApiUsage")
public final class MekanizedCommands {
    private final MekanizedDebugCommands debug;
    private final EngineCheckCommands engineChecks;

    public MekanizedCommands(Plugin plugin) {
        this.debug = new MekanizedDebugCommands(plugin);
        this.engineChecks = new EngineCheckCommands(plugin);
    }

    /** {@code /mekanized build <structure> [<x y z>]}: a working multiblock, its lowest corner there. */
    private LiteralArgumentBuilder<CommandSourceStack> structures() {
        var build = Commands.literal("build").requires(MekanizedCommands::admin);
        for (String name : MekanizedStructures.STRUCTURES.keySet()) {
            build.then(PositionArguments.optionalPosition(Commands.literal(name), PositionArguments.Default.PLACEMENT,
                    (context, world, x, y, z) -> {
                        PaperWorld.scheduler().run(new Location(world, x, y, z), () -> {
                            String problem = MekanizedStructures.build(name, world, x, y, z);
                            reply(context, problem == null ? "mekanized build: " + name.replace('_', ' ') + " built at " + x + " " + y + " " + z
                                    : "mekanized build: " + problem);
                        });
                        return Command.SINGLE_SUCCESS;
                    }));
        }
        return build;
    }

    private static boolean admin(CommandSourceStack source) {
        return source.getSender().hasPermission("machineengine.admin");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        // Players set frequencies; the tests are for admins.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mekanized")
                .then(Commands.literal("frequency")
                        .requires(source -> source.getSender() instanceof Player)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    PaperAutomation.setFrequency((Player) context.getSource().getSender(),
                                            StringArgumentType.getString(context, "name"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(structures())
                // Radiation for testing: a source where you stand (Sv/h at one block), or none left.
                .then(Commands.literal("radiation").requires(MekanizedCommands::admin)
                        .then(Commands.literal("clear").executes(context -> {
                            Radiation.sources().clear();
                            reply(context, "Every radiation source is gone");
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.argument("sieverts", DoubleArgumentType.doubleArg(0))
                                .executes(context -> {
                                    Location at = context.getSource().getLocation();
                                    double strength = DoubleArgumentType.getDouble(context, "sieverts");
                                    Radiation.release(PaperWorld.position(at.getBlock()), strength);
                                    reply(context, "Released " + strength + " Sv/h here");
                                    return Command.SINGLE_SUCCESS;
                                })));
        this.debug.addTo(root);
        this.engineChecks.addTo(root);
        return root.build();
    }

    static void reply(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().getSender().sendMessage(message);
    }
}
