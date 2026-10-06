package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.event.EventPhase;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineEvents;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * Chunk loading: machines with an anchor upgrade keep their own chunk loaded; the
 * dimensional stabilizer keeps the chunks around it loaded while it has energy. This class only
 * knows which machine wants which chunks; the platform adds the chunk tickets and saves the list.
 */
public final class ChunkLoaders {
    /** Chunks around its own the stabilizer can load: up to 2, a 5 x 5 area like Mekanism's. */
    public static final int MAX_RADIUS = 2;
    /** Joules per tick for each chunk a stabilizer keeps loaded. */
    public static final long STABILIZER_ENERGY_PER_CHUNK = 500;
    public static final MachineDataKey<String> RADIUS = DataKeys.string("radius", "0");
    public static final MachineDataKey<String> STATUS = DataKeys.STATUS;

    /** Machine position -> chunks around its own it keeps loaded (0: only its chunk). */
    private static final Map<WorldPosition, Integer> LOADERS = new ConcurrentHashMap<>();
    private static volatile @Nullable Runnable onChange;

    private ChunkLoaders() {
    }

    /** What each loader keeps loaded (the platform reads it after each change, and saves it). */
    public static @NotNull Map<WorldPosition, Integer> loaders() {
        return LOADERS;
    }

    /** Called after a loader is added, changed or removed (from any thread). */
    public static void onChange(@Nullable Runnable listener) {
        onChange = listener;
    }

    static void set(@NotNull WorldPosition machine, @Nullable Integer radius) {
        Integer old = radius == null ? LOADERS.remove(machine) : LOADERS.put(machine, radius);
        if (!Objects.equals(old, radius)) {
            Runnable listener = onChange;
            if (listener != null) listener.run();
        }
    }

    static void register(RegistrationContext context) {
        context.machineTypes().register(BasicMachines.base("dimensional_stabilizer", 1_000_000)
                .data(RADIUS).data(STATUS)
                .behavior(stabilizer())
                .build());
        // Anchor upgrades: follow the machines as they load, change upgrades and are removed (the
        // list survives unloading: that is the point).
        context.events().subscribe(context.addon(), MachineEvents.MACHINE_LOAD, EventPhase.POST, event -> anchor(event.machine()));
        context.events().subscribe(context.addon(), MachineEvents.UPGRADE_CHANGE, EventPhase.POST, event -> anchor(event.machine()));
        context.events().subscribe(context.addon(), MachineEvents.MACHINE_REMOVE, EventPhase.POST, event -> set(event.machine().position(), null));
    }

    private static boolean isStabilizer(MachineInstance machine) {
        return machine.type().key().equals(key("dimensional_stabilizer"));
    }

    private static void anchor(MachineInstance machine) {
        if (isStabilizer(machine)) return;
        boolean anchored = machine.upgrades().stream()
                .anyMatch(upgrade -> upgrade.type().equals(MekanizedRegistry.ANCHOR_UPGRADE) && upgrade.count() > 0);
        if (anchored) set(machine.position(), 0);
        else if (LOADERS.containsKey(machine.position())) set(machine.position(), null);
    }

    /** Once a second: pays for its chunks ({@link #STABILIZER_ENERGY_PER_CHUNK} each, per tick) and keeps them loaded. */
    static @NotNull MachineBehavior.Factory stabilizer() {
        return type -> context -> {
            int radius = DataKeys.parseInt(context.get(RADIUS), 0, 0, MAX_RADIUS);
            long chunks = (2L * radius + 1) * (2L * radius + 1);
            long cost = context.modifiedCost(MekanizedContent.ENERGY, STABILIZER_ENERGY_PER_CHUNK * chunks) * 20;
            boolean paid;
            try (Transaction transaction = context.transactions().openOuter()) {
                paid = MachineParts.pay(context.storage("energy"), cost, transaction);
                if (paid) transaction.commit();
            }
            WorldPosition here = context.machine().position();
            if (paid) {
                set(here, radius);
                context.set(STATUS, String.format(Locale.ROOT, "Loading %d chunk%s", chunks, chunks == 1 ? "" : "s"));
            } else {
                set(here, null);
                context.set(STATUS, "Needs energy");
            }
            context.setActive(paid);
            context.wakeIn(20);
        };
    }
}
