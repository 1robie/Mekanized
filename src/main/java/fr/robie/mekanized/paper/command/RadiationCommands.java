package fr.robie.mekanized.paper.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.robie.machineengine.api.player.MachinePlayer;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.mekanized.MekanizedPlayers;
import fr.robie.mekanized.machine.Radiation;
import fr.robie.mekanized.paper.PaperWorld;
import fr.robie.paperdispatch.command.CommandDispatch;
import fr.robie.paperdispatch.command.CommandResultType;
import fr.robie.paperdispatch.command.SubCommand;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /mekanized radiation …}: the radiation sources of the world and the players' doses.
 * <ul>
 *   <li>{@code radiation <Sv/h>}, {@code add <Sv/h>}: a source where you stand (admins);</li>
 *   <li>{@code clear}: every source gone (admins); {@code sources}: the list, strongest first (admins);</li>
 *   <li>{@code level}: the radiation where you stand ({@code mekanized.radiation.dose});</li>
 *   <li>{@code dose}: your dose ({@code mekanized.radiation.dose}); {@code dose <player> [set|add <Sv> | clear]}:
 *       anyone's (admins).</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
final class RadiationCommands {
    static final String SEE = "mekanized.radiation.dose";
    private static final String DOSE_USAGE = "Usage: /mekanized radiation dose [<player> [set <Sv> | add <Sv> | clear]]";

    private RadiationCommands() {
    }

    static SubCommand<Plugin> command(Plugin plugin) {
        return SubCommand.builder(plugin, "radiation")
                .addSubCommand(SubCommand.builder(plugin, "add").permission(PositionSubCommand.ADMIN)
                        .addRequiredArgument("sieverts", DoubleArgumentType.doubleArg(0))
                        .executes(RadiationCommands::release).build())
                .addSubCommand(SubCommand.builder(plugin, "clear").permission(PositionSubCommand.ADMIN).executes(dispatch -> {
                    int count = Radiation.sources().size();
                    Radiation.sources().clear();
                    dispatch.getSender().sendMessage(Component.text("Removed " + count + " radiation source(s).", NamedTextColor.GREEN));
                    return CommandResultType.SUCCESS;
                }).build())
                .addSubCommand(SubCommand.builder(plugin, "sources").permission(PositionSubCommand.ADMIN)
                        .executes(RadiationCommands::sources).build())
                .addSubCommand(SubCommand.builder(plugin, "level").permission(SEE).executes(RadiationCommands::level).build())
                .addSubCommand(new DoseCommand(plugin))
                // The older form: /mekanized radiation <Sv/h>.
                .addRequiredArgument("sieverts", DoubleArgumentType.doubleArg(0))
                .executes(dispatch -> dispatch.getSender().hasPermission(PositionSubCommand.ADMIN) ? release(dispatch) : denied(dispatch))
                .build();
    }

    private static CommandResultType release(CommandDispatch<Plugin> dispatch) {
        Location at = dispatch.getLocation();
        double strength = dispatch.getArgument("sieverts", Double.class);
        Radiation.release(PaperWorld.position(at.getBlock()), strength);
        dispatch.getSender().sendMessage(Component.text("Released " + Radiation.format(strength, "/h") + " at "
                + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + ".", NamedTextColor.GREEN));
        return CommandResultType.SUCCESS;
    }

    private static CommandResultType sources(CommandDispatch<Plugin> dispatch) {
        List<Map.Entry<WorldPosition, Double>> sources = new ArrayList<>(Radiation.sources().entrySet());
        sources.sort(Map.Entry.<WorldPosition, Double>comparingByValue(Comparator.reverseOrder()));
        CommandSender sender = dispatch.getSender();
        sender.sendMessage(Component.text(sources.size() + " radiation source(s)" + (sources.isEmpty() ? "." : ", strongest first:"),
                NamedTextColor.GOLD));
        for (Map.Entry<WorldPosition, Double> source : sources.subList(0, Math.min(20, sources.size()))) {
            WorldPosition at = source.getKey();
            sender.sendMessage(Component.text("  " + at.world().asString() + " " + at.position().x() + " " + at.position().y() + " "
                    + at.position().z() + ": ", NamedTextColor.GRAY).append(Component.text(Radiation.format(source.getValue(), "/h"),
                    color(source.getValue()))));
        }
        if (sources.size() > 20) sender.sendMessage(Component.text("  … and " + (sources.size() - 20) + " more", NamedTextColor.DARK_GRAY));
        return CommandResultType.SUCCESS;
    }

    private static CommandResultType level(CommandDispatch<Plugin> dispatch) {
        Location at = dispatch.getLocation();
        if (at.getWorld() == null) return CommandResultType.FAILURE;
        double level = Radiation.levelAt(Key.key(at.getWorld().getKey().asString()), at.getX(), at.getY() + 1, at.getZ());
        dispatch.getSender().sendMessage(Component.text("Radiation here: ", NamedTextColor.GRAY)
                .append(Component.text(Radiation.format(level, "/h"), color(level * 3600))));
        return CommandResultType.SUCCESS;
    }

    private static CommandResultType denied(CommandDispatch<Plugin> dispatch) {
        dispatch.getSender().sendMessage(Component.text("You may not do that.", NamedTextColor.RED));
        return CommandResultType.FAILURE;
    }

    /** Green below 0.1 Sv, yellow below 1, red above (an hour's worth of a level, or a dose). */
    private static NamedTextColor color(double sieverts) {
        return switch (Radiation.severity(sieverts)) {
            case LOW -> NamedTextColor.GREEN;
            case MEDIUM -> NamedTextColor.YELLOW;
            case HIGH -> NamedTextColor.RED;
        };
    }

    /** {@code dose [<player> [set <Sv> | add <Sv> | clear]]}. */
    private static final class DoseCommand extends SubCommand<Plugin> {
        DoseCommand(Plugin plugin) {
            super(plugin, "dose");
            setPermission(SEE);
            addOptionalArgument(Commands.argument("arguments", StringArgumentType.greedyString())
                    .suggests((context, builder) -> {
                        if (!context.getSource().getSender().hasPermission(PositionSubCommand.ADMIN)) return builder.buildFuture();
                        String typed = builder.getRemaining();
                        String[] words = typed.split(" ", -1);
                        String before = typed.substring(0, typed.length() - words[words.length - 1].length());
                        String last = words[words.length - 1].toLowerCase(Locale.ROOT);
                        List<String> options = switch (words.length) {
                            case 1 -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                            case 2 -> List.of("set", "add", "clear");
                            default -> List.of();
                        };
                        for (String option : options) {
                            if (option.toLowerCase(Locale.ROOT).startsWith(last)) builder.suggest(before + option);
                        }
                        return builder.buildFuture();
                    }));
        }

        @Override
        protected CommandResultType perform(CommandDispatch<Plugin> dispatch) {
            CommandSender sender = dispatch.getSender();
            String[] words = dispatch.getOptionalArgument("arguments", String.class).orElse("").trim().split(" +");
            boolean self = words[0].isEmpty();
            if (self && !(dispatch.getExecutor() instanceof Player)) {
                sender.sendMessage(Component.text(DOSE_USAGE, NamedTextColor.RED));
                return CommandResultType.FAILURE;
            }
            Player target = self ? (Player) dispatch.getExecutor() : Bukkit.getPlayerExact(words[0]);
            if (target == null) {
                sender.sendMessage(Component.text(words[0] + " is not online.", NamedTextColor.RED));
                return CommandResultType.FAILURE;
            }
            String action = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "show";
            // Anyone with the permission sees their own dose; everything else is for admins.
            if ((!target.equals(sender) || !action.equals("show")) && !sender.hasPermission(PositionSubCommand.ADMIN)) {
                return denied(dispatch);
            }
            Double amount = null;
            if (action.equals("set") || action.equals("add")) {
                try {
                    amount = words.length > 2 ? Double.parseDouble(words[2]) : null;
                } catch (NumberFormatException e) {
                    amount = null;
                }
                if (amount == null || !Double.isFinite(amount) || (action.equals("set") && amount < 0)) {
                    sender.sendMessage(Component.text(DOSE_USAGE, NamedTextColor.RED));
                    return CommandResultType.FAILURE;
                }
            } else if (!action.equals("show") && !action.equals("clear")) {
                sender.sendMessage(Component.text(DOSE_USAGE, NamedTextColor.RED));
                return CommandResultType.FAILURE;
            }
            Double change = amount;
            // A player's data belongs to their own thread (Folia).
            PaperWorld.scheduler().run(target, () -> apply(sender, target, action, change), null);
            return CommandResultType.SUCCESS;
        }

        private static void apply(CommandSender sender, Player target, String action, @Nullable Double amount) {
            MachinePlayer data = MekanizedPlayers.of(target).orElse(null);
            if (data == null) {
                sender.sendMessage(Component.text("The data of " + target.getName() + " is not loaded yet.", NamedTextColor.RED));
                return;
            }
            double dose = switch (action) {
                case "set" -> {
                    data.set(MekanizedPlayers.RADIATION_DOSE, amount);
                    yield amount;
                }
                case "add" -> data.update(MekanizedPlayers.RADIATION_DOSE, old -> Math.max(0, old + amount));
                case "clear" -> {
                    data.reset(MekanizedPlayers.RADIATION_DOSE);
                    yield 0;
                }
                default -> data.get(MekanizedPlayers.RADIATION_DOSE);
            };
            String who = target.equals(sender) ? "Your" : target.getName() + "'s";
            sender.sendMessage(Component.text(who + " radiation dose" + (action.equals("show") ? ": " : " is now "), NamedTextColor.GRAY)
                    .append(Component.text(Radiation.format(dose, ""), color(dose))));
        }
    }
}
