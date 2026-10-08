package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.transfer.TransactionContext;
import fr.robie.machineengine.api.upgrade.StandardModifiers;
import fr.robie.machineengine.api.world.BlockPosition;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedConfig;
import fr.robie.machineengine.mekanized.MekanizedContent;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.ENERGY_CONVERSION;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.IS_ENERGY;

/**
 * The Digital Miner, the seismic vibrator, the teleporter and
 * QIO (drive arrays, importers, exporters; the dashboard is a Paper menu). Teleporters and QIO blocks
 * share a frequency, a name set with {@code /mekanized frequency <name>}.
 */
public final class AutomationMachines {
    private static final Set<Key> AIR = Set.of(Key.key("minecraft", "air"), Key.key("minecraft", "cave_air"), Key.key("minecraft", "void_air"));

    // -- settings ---------------------------------------------------------------------------------
    public static final MachineDataKey<String> FREQUENCY = DataKeys.string("frequency", "");
    public static final MachineDataKey<Boolean> RUNNING = DataKeys.RUNNING;
    public static final MachineDataKey<String> RADIUS = DataKeys.string("radius", "8");
    public static final MachineDataKey<String> MIN_Y = DataKeys.string("min_y", "-64");
    public static final MachineDataKey<Long> CURSOR = DataKeys.number("cursor");
    public static final MachineDataKey<String> STATUS = DataKeys.STATUS;

    /** Seismic vibrator: Joules per tick while it runs (its block config's option, D34). */
    public static final ConfigKey<Long> VIBRATOR_ENERGY = ConfigKey.whole("energy-per-tick", 50, 0, MekanizedConfig.MAX).clamped();
    /** Digital miner: Joules and ticks per block mined (its block config's options, D34). */
    public static final ConfigKey<Long> MINER_ENERGY = ConfigKey.whole("energy-per-block", 2_000, 0, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> MINER_TICKS = ConfigKey.whole("ticks-per-block", 10, 1, 72_000).clamped();
    /** Items an importer or exporter moves per second: its block config's option (D34). */
    public static final ConfigKey<Long> IO_RATE = ConfigKey.whole("items-per-second", 64, 1, 1_000_000).clamped();

    private AutomationMachines() {
    }

    static void register(RegistrationContext context) {
        context.machineTypes().register(BasicMachines.base("digital_miner", 50_000)
                .storage(StorageDefinition.slots("filter", 9, 1).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("output", 27, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).group("items")
                        .sides(RelativeSide.BACK, RelativeSide.TOP).build())
                .upgradeSlots(2, upgrade -> MekanizedRegistry.standardUpgrade(upgrade) || upgrade.equals(MekanizedRegistry.STONE_GENERATOR_UPGRADE))
                .data(RUNNING).data(RADIUS).data(MIN_Y).data(CURSOR).data(STATUS)
                .behavior(MachineParts.powered(miner()))
                .build());
        context.machineTypes().register(MachineType.builder(key("seismic_vibrator"))
                .storage(StorageDefinition.tank("energy", 20_000).filter(IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY).group("energy").build())
                .behavior(vibrator())
                .build());
        context.machineTypes().register(MachineType.builder(key("teleporter"))
                .storage(StorageDefinition.tank("energy", 5_000_000).filter(IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY).group("energy").build())
                .data(FREQUENCY)
                .behavior(teleporter())
                .build());
        context.machineTypes().register(MachineType.builder(key("teleporter_frame")).behavior(type -> MachineContext::sleep).build());
        context.machineTypes().register(MachineType.builder(key("qio_drive_array"))
                .storage(StorageDefinition.slots("drives", 12, 1).filter((variant, tags) -> drive(variant) != null))
                .storage(StorageDefinition.slots("items", QioSettings.arrayKinds, Long.MAX_VALUE / 4).filter(Items.anyItem()))
                .data(FREQUENCY)
                .behavior(driveArray())
                .build());
        context.machineTypes().register(MachineType.builder(key("qio_dashboard")).data(FREQUENCY).behavior(qioMember()).build());
        for (String name : List.of("qio_importer", "qio_exporter")) {
            boolean importer = name.equals("qio_importer");
            context.machineTypes().register(MachineType.builder(key(name))
                    .storage(StorageDefinition.slots("filter", 9, 1).filter(Items.anyItem()))
                    .data(FREQUENCY)
                    .behavior(importer ? importer() : exporter())
                    .build());
        }
    }

    // -- Digital Miner ----------------------------------------------------------------------------

    /**
     * Mines the blocks around it, from just below it down to its lowest level, within its radius:
     * those its filters name (an item per filter slot), or every ore ({@code *_ore}, ancient debris)
     * without filters. One block every {@link DigitalMinerSettings#ticksPerBlock} ticks; drops into its output.
     */
    static @NotNull MachineBehavior.Factory miner() {
        return type -> context -> {
            if (!context.get(RUNNING)) {
                context.setActive(false);
                context.set(STATUS, "Stopped");
                context.sleep();
                return;
            }
            int radius = DataKeys.parseInt(context.get(RADIUS), 8, 1, DigitalMinerSettings.maxRadius);
            BlockPosition here = context.machine().position().position();
            int minY = Math.min(DataKeys.parseInt(context.get(MIN_Y), -64, -2048, 2048), here.y() - 1);
            int side = 2 * radius + 1;
            long area = (long) side * side * (here.y() - minY);
            if (context.storage("output").views().stream().noneMatch(StorageView::isEmpty)) {
                idle(context, "Output full", 20);
                return;
            }
            long cost = context.modifiedCost(MekanizedContent.ENERGY, context.options().get(MINER_ENERGY));
            if (context.storage("energy").views().getFirst().amount() < cost) {
                idle(context, "Needs energy", 20);
                return;
            }
            Set<Key> filters = filters(context);
            var machineTypes = context.registries().registry(Registries.MACHINE_TYPE);
            long cursor = context.get(CURSOR);
            if (cursor >= area) {
                context.set(CURSOR, 0L); // look again from the top later: blocks change
                idle(context, "Finished: everything mined", 200);
                return;
            }
            for (int scanned = 0; scanned < DigitalMinerSettings.scanPerTick && cursor < area; scanned++, cursor++) {
                int layer = (int) (cursor / ((long) side * side));
                int index = (int) (cursor % ((long) side * side));
                BlockPosition at = new BlockPosition(here.x() - radius + index % side, here.y() - 1 - layer, here.z() - radius + index / side);
                Key block = context.world().blockAt(at);
                if (block == null || AIR.contains(block) || machineTypes.get(block).isPresent()) continue;
                boolean wanted = filters.isEmpty() ? block.value().endsWith("_ore") || block.value().equals("ancient_debris") : filters.contains(block);
                if (!wanted || context.world().blockHardness(at) < 0) continue;
                if (!context.world().breakBlock(at)) continue;
                try (Transaction transaction = context.transactions().openOuter()) {
                    context.storage("energy").extract(MekanizedContent.ENERGY.variant(), cost, transaction);
                    for (ResourceStack drop : context.world().collectItems(at)) {
                        context.storage("output").insert(drop.variant(), drop.amount(), transaction); // what does not fit is lost
                    }
                    transaction.commit();
                }
                // Stone generator upgrade: the hole is filled with stone.
                if (MekanizedRegistry.installed(context, MekanizedRegistry.STONE_GENERATOR_UPGRADE)) {
                    context.world().placeBlock(at, Key.key("minecraft", "stone"));
                }
                context.set(CURSOR, cursor + 1);
                context.setActive(true);
                context.set(STATUS, String.format(Locale.ROOT, "Mining: %s at y %d", block.value().replace('_', ' '), at.y()));
                context.wakeIn(Math.max(1, context.modified(StandardModifiers.PROCESS_DURATION, context.options().get(MINER_TICKS))));
                return;
            }
            context.set(CURSOR, cursor);
            context.set(STATUS, String.format(Locale.ROOT, "Searching: %d %%", area == 0 ? 100 : cursor * 100 / area));
            context.wakeIn(1);
        };
    }

    private static Set<Key> filters(MachineContext context) {
        Set<Key> filters = new HashSet<>();
        for (StorageView view : context.storage("filter").views()) {
            ItemData data = view.isEmpty() ? null : view.variant().components().get(Items.DATA);
            if (data != null) filters.add(data.item());
        }
        return filters;
    }

    private static void idle(MachineContext context, String status, long ticks) {
        context.setActive(false);
        context.set(STATUS, status);
        context.wakeIn(ticks);
    }

    // -- seismic vibrator ---------------------------------------------------------------------

    /** Runs (shows it, and lets a seismic reader see the ground below) while it has energy. */
    static @NotNull MachineBehavior.Factory vibrator() {
        return type -> context -> {
            boolean running;
            try (Transaction transaction = context.transactions().openOuter()) {
                running = MachineParts.pay(context.storage("energy"), context.options().get(VIBRATOR_ENERGY) * 20, transaction);
                if (running) transaction.commit();
            }
            context.setActive(running);
            context.wakeIn(20);
        };
    }

    // -- teleporter -------------------------------------------------------------------------------

    /** Checks its frame every two seconds and keeps the frequencies up to date; the platform teleports. */
    static @NotNull MachineBehavior.Factory teleporter() {
        return type -> new MachineBehavior() {
            @Override
            public void tick(@NotNull MachineContext context) {
                String frequency = context.get(FREQUENCY);
                boolean formed = frameComplete(context);
                context.setActive(formed && !frequency.isEmpty());
                Frequencies.teleporter(context.machine().position(), frequency.isEmpty() ? null : new Frequencies.Teleporter(frequency, formed));
                context.wakeIn(40);
            }

            @Override
            public void onRemove(@NotNull MachineContext context) {
                Frequencies.teleporter(context.machine().position(), null);
            }

            @Override
            public void onWake(@NotNull MachineContext context, @NotNull WakeReason reason) {
            }
        };
    }

    /**
     * Mekanism's frame: the teleporter at the bottom middle of a 3 wide, 4 high ring of frames
     * (along x or z), the portal the two blocks above it.
     */
    public static boolean frameComplete(@NotNull MachineContext context) {
        BlockPosition here = context.machine().position().position();
        Key frame = key("teleporter_frame");
        for (Direction axis : List.of(Direction.EAST, Direction.SOUTH)) {
            boolean complete = true;
            for (int dy = 0; dy <= 3 && complete; dy++) {
                BlockPosition row = new BlockPosition(here.x(), here.y() + dy, here.z());
                complete = frame.equals(context.world().blockAt(row.relative(axis))) && frame.equals(context.world().blockAt(row.relative(axis.opposite())));
            }
            complete = complete && frame.equals(context.world().blockAt(new BlockPosition(here.x(), here.y() + 3, here.z())));
            if (complete) return true;
        }
        return false;
    }

    // -- QIO -----------------------------------------------------------------------------------

    /** The items and kinds a drive item holds (its item config, {@link QioDrives}), or null if it is no drive. */
    public static long @Nullable [] drive(@NotNull ResourceVariant variant) {
        ItemData data = variant.components().get(Items.DATA);
        QioDrives.Limits limits = data == null ? null : QioDrives.of(data.item());
        return limits == null ? null : new long[]{limits.items(), limits.kinds()};
    }

    /** A drive array: its drives set how many items (and kinds) it holds; it publishes what it holds. */
    static @NotNull MachineBehavior.Factory driveArray() {
        return type -> new MachineBehavior() {
            @Override
            public void tick(@NotNull MachineContext context) {
                String frequency = context.get(FREQUENCY);
                Map<ResourceVariant, Long> contents = new HashMap<>();
                long stored = 0;
                for (StorageView view : context.storage("items").views()) {
                    if (view.isEmpty()) continue;
                    contents.merge(view.variant(), view.amount(), Long::sum);
                    stored += view.amount();
                }
                long[] limits = limits(context.storage("drives"));
                context.setActive(!frequency.isEmpty() && limits[0] > 0);
                WorldPosition here = context.machine().position();
                if (frequency.isEmpty()) Frequencies.ARRAYS.remove(here);
                else Frequencies.ARRAYS.put(here, new Frequencies.Array(frequency, Map.copyOf(contents), limits[0], stored));
                context.wakeIn(20);
            }

            @Override
            public void onUnload(@NotNull MachineContext context) {
                Frequencies.ARRAYS.remove(context.machine().position());
            }

            @Override
            public void onRemove(@NotNull MachineContext context) {
                Frequencies.ARRAYS.remove(context.machine().position());
            }

            @Override
            public void onWake(@NotNull MachineContext context, @NotNull WakeReason reason) {
            }
        };
    }

    /** {items, kinds} the drives in {@code drives} hold together. */
    static long[] limits(ResourceHandler drives) {
        long items = 0;
        long kinds = 0;
        for (StorageView view : drives.views()) {
            long[] drive = view.isEmpty() ? null : drive(view.variant());
            if (drive == null) continue;
            items += drive[0];
            kinds += drive[1];
        }
        return new long[]{items, Math.min(kinds, QioSettings.arrayKinds)};
    }

    /**
     * Puts up to {@code amount} of {@code variant} into the drive array whose storages are given,
     * within what its drives hold (items and kinds).
     */
    public static long insertIntoArray(@NotNull ResourceHandler drives, @NotNull ResourceHandler items, @NotNull ResourceVariant variant,
                                       long amount, @NotNull TransactionContext transaction) {
        long[] limits = limits(drives);
        long stored = 0;
        long kinds = 0;
        boolean known = false;
        for (StorageView view : items.views()) {
            if (view.isEmpty()) continue;
            stored += view.amount();
            kinds++;
            known |= view.variant().equals(variant);
        }
        if (!known && kinds >= limits[1]) return 0;
        long room = limits[0] - stored;
        if (room <= 0) return 0;
        return items.insert(variant, Math.min(room, amount), transaction);
    }

    /** The QIO network reachable from {@code context}: the drive arrays of its frequency this thread owns. */
    private static List<ResourceHandler[]> network(MachineContext context) {
        String frequency = context.get(FREQUENCY);
        List<ResourceHandler[]> arrays = new ArrayList<>();
        if (frequency.isEmpty()) return arrays;
        for (WorldPosition position : Frequencies.arrays(frequency)) {
            var drives = context.machines().storage(position, "drives");
            var items = context.machines().storage(position, "items");
            if (drives.isPresent() && items.isPresent()) arrays.add(new ResourceHandler[]{drives.get(), items.get()});
        }
        return arrays;
    }

    /** Dashboards: only shown as connected while their frequency has a drive array. */
    static @NotNull MachineBehavior.Factory qioMember() {
        return type -> context -> {
            String frequency = context.get(FREQUENCY);
            context.setActive(!frequency.isEmpty() && !Frequencies.arrays(frequency).isEmpty());
            context.wakeIn(40);
        };
    }

    /** Takes what its filters allow (everything without filters) from the block in front into the network. */
    static @NotNull MachineBehavior.Factory importer() {
        return type -> context -> {
            List<ResourceHandler[]> network = network(context);
            ResourceHandler source = context.neighbor(RelativeSide.FRONT, Capabilities.RESOURCE_HANDLER).orElse(null);
            context.setActive(!network.isEmpty());
            context.wakeIn(20);
            if (network.isEmpty() || source == null) return;
            Set<Key> filters = filters(context);
            long budget = context.options().get(IO_RATE);
            for (StorageView view : List.copyOf(source.views())) {
                if (budget <= 0) break;
                if (view.isEmpty() || !passes(view.variant(), filters, true)) continue;
                ResourceVariant variant = view.variant();
                long amount = Math.min(budget, view.amount());
                try (Transaction transaction = context.transactions().openOuter()) {
                    long moved = 0;
                    for (ResourceHandler[] array : network) {
                        moved += insertIntoArray(array[0], array[1], variant, amount - moved, transaction);
                        if (moved >= amount) break;
                    }
                    if (moved <= 0 || source.extract(variant, moved, transaction) != moved) continue;
                    transaction.commit();
                    budget -= moved;
                }
            }
        };
    }

    /** Puts what its filters name from the network into the block in front (needs filters, like Mekanism's). */
    static @NotNull MachineBehavior.Factory exporter() {
        return type -> context -> {
            List<ResourceHandler[]> network = network(context);
            ResourceHandler target = context.neighbor(RelativeSide.FRONT, Capabilities.RESOURCE_HANDLER).orElse(null);
            context.setActive(!network.isEmpty());
            context.wakeIn(20);
            Set<Key> filters = filters(context);
            if (network.isEmpty() || target == null || filters.isEmpty()) return;
            long budget = context.options().get(IO_RATE);
            for (ResourceHandler[] array : network) {
                for (StorageView view : List.copyOf(array[1].views())) {
                    if (budget <= 0) return;
                    if (view.isEmpty() || !passes(view.variant(), filters, false)) continue;
                    ResourceVariant variant = view.variant();
                    try (Transaction transaction = context.transactions().openOuter()) {
                        long moved = target.insert(variant, Math.min(budget, view.amount()), transaction);
                        if (moved <= 0 || array[1].extract(variant, moved, transaction) != moved) continue;
                        transaction.commit();
                        budget -= moved;
                    }
                }
            }
        };
    }

    private static boolean passes(ResourceVariant variant, Set<Key> filters, boolean emptyPasses) {
        if (filters.isEmpty()) return emptyPasses;
        ItemData data = variant.components().get(Items.DATA);
        return data != null && filters.contains(data.item());
    }
}
