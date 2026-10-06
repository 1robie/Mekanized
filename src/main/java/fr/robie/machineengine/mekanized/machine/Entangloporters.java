package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.resource.ResourceCategory;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * The quantum entangloporter (Phase 6 wave 6): every entangloporter of a frequency shares one buffer
 * of energy, items, fluid and chemical. A sending one moves what pipes, cables and hoppers give it
 * into the buffer; a receiving one takes from the buffer and pushes it out of every side.
 *
 * <p>Entangloporters can be in different Folia regions, so the buffer belongs to no machine: it is a
 * locked map, changed only after the machine's own transaction committed (nothing is lost or made).
 * The platform saves it.
 */
public final class Entangloporters {
    /** {@code send} (default) or {@code receive}. */
    public static final MachineDataKey<String> MODE = MachineDataKey.of("entangloporter_mode",
            VersionedCodec.builder(key("entangloporter_mode"), Codecs.STRING).build(), "send");

    /** A shared buffer of one kind: its storage in the machine, what it takes, how much the frequency holds. */
    public record Channel(@NotNull String storage, @NotNull ResourcePredicate filter, long capacity, boolean oneKind,
                          @NotNull String group) {
    }

    public static final List<Channel> CHANNELS = List.of(
            new Channel("energy", MekanizedMachines.IS_ENERGY, 64_000_000, false, "energy"),
            new Channel("items", Items.anyItem(), 64, true, "items"),
            new Channel("fluid", ResourcePredicate.category(MekanizedContent.FLUID), 256_000, true, "fluid"),
            new Channel("chemical", MekanizedMachines.IS_CHEMICAL, 256_000, true, "chemical"));

    /** Frequency -> channel storage -> what the buffer holds. */
    private static final Map<String, Map<String, Map<ResourceVariant, Long>>> BUFFERS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean DIRTY = new java.util.concurrent.atomic.AtomicBoolean();

    private Entangloporters() {
    }

    /** The buffers by frequency (lock the frequency's map to read or change it). */
    public static @NotNull Map<String, Map<String, Map<ResourceVariant, Long>>> buffers() {
        return BUFFERS;
    }

    /** Whether a buffer changed since the last call (the platform saves then). */
    public static boolean takeDirty() {
        return DIRTY.getAndSet(false);
    }

    static Map<String, Map<ResourceVariant, Long>> buffer(String frequency) {
        return BUFFERS.computeIfAbsent(frequency, f -> {
            Map<String, Map<ResourceVariant, Long>> channels = new LinkedHashMap<>();
            CHANNELS.forEach(channel -> channels.put(channel.storage(), new LinkedHashMap<>()));
            return channels;
        });
    }

    static void register(RegistrationContext context) {
        MachineType.Builder builder = MachineType.builder(key("quantum_entangloporter"))
                .data(AutomationMachines.FREQUENCY).data(MODE);
        for (Channel channel : CHANNELS) {
            // Each machine holds a sixteenth of the frequency's buffer (items: one slot).
            StorageDefinition storage = channel.storage().equals("items")
                    ? StorageDefinition.slots("items", 1, 64).filter(channel.filter())
                    : StorageDefinition.tank(channel.storage(), channel.capacity() / 16).filter(channel.filter());
            builder.storage(storage).port(PortDefinition.builder(channel.storage(), channel.storage())
                    .direction(PortDirection.INPUT).filter(channel.filter()).group(channel.group()).build());
        }
        context.machineTypes().register(builder.behavior(entangloporter()).build());
    }

    static @NotNull MachineBehavior.Factory entangloporter() {
        return type -> context -> {
            String frequency = context.get(AutomationMachines.FREQUENCY);
            boolean receive = "receive".equals(context.get(MODE));
            PortDirection direction = receive ? PortDirection.OUTPUT : PortDirection.INPUT;
            for (Channel channel : CHANNELS) {
                if (context.portDirection(channel.storage()) != direction) context.setPortDirection(channel.storage(), direction);
            }
            if (frequency.isEmpty()) {
                context.setActive(false);
                context.sleep();
                return;
            }
            Map<String, Map<ResourceVariant, Long>> buffer = buffer(frequency);
            boolean moved = false;
            synchronized (buffer) {
                for (Channel channel : CHANNELS) {
                    Map<ResourceVariant, Long> shared = buffer.get(channel.storage());
                    moved |= receive ? receive(context, channel, shared) : send(context, channel, shared);
                }
            }
            if (moved) DIRTY.set(true);
            if (receive) {
                for (Channel channel : CHANNELS) {
                    for (RelativeSide side : RelativeSide.values()) context.push(side, channel.storage(), variant -> true, Long.MAX_VALUE);
                }
            }
            context.setActive(true);
            context.wakeIn(1);
        };
    }

    /** Moves what the machine holds into the shared buffer (as much as fits). */
    private static boolean send(MachineContext context, Channel channel, Map<ResourceVariant, Long> shared) {
        ResourceHandler local = context.storage(channel.storage());
        long stored = shared.values().stream().mapToLong(Long::longValue).sum();
        boolean moved = false;
        for (StorageView view : List.copyOf(local.views())) {
            if (view.isEmpty()) continue;
            ResourceVariant variant = view.variant();
            if (channel.oneKind() && !shared.isEmpty() && !shared.containsKey(variant)) continue;
            long space = channel.capacity() - stored;
            if (space <= 0) break;
            long taken;
            try (Transaction transaction = context.transactions().openOuter()) {
                taken = local.extract(variant, Math.min(space, view.amount()), transaction);
                transaction.commit();
            }
            if (taken > 0) {
                shared.merge(variant, taken, Long::sum);
                stored += taken;
                moved = true;
            }
        }
        return moved;
    }

    /** Takes from the shared buffer what the machine has room for. */
    private static boolean receive(MachineContext context, Channel channel, Map<ResourceVariant, Long> shared) {
        ResourceHandler local = context.storage(channel.storage());
        boolean moved = false;
        for (Map.Entry<ResourceVariant, Long> entry : List.copyOf(shared.entrySet())) {
            long given;
            try (Transaction transaction = context.transactions().openOuter()) {
                given = local.insert(entry.getKey(), entry.getValue(), transaction);
                transaction.commit();
            }
            if (given <= 0) continue;
            moved = true;
            if (given >= entry.getValue()) shared.remove(entry.getKey());
            else shared.put(entry.getKey(), entry.getValue() - given);
        }
        return moved;
    }

    /** Adds to a frequency's buffer (the platform loading it). */
    public static void load(@NotNull String frequency, @NotNull String storage, @NotNull ResourceVariant variant, long amount) {
        Map<String, Map<ResourceVariant, Long>> buffer = buffer(frequency);
        synchronized (buffer) {
            Map<ResourceVariant, Long> shared = buffer.get(storage);
            if (shared != null) shared.merge(variant, amount, Long::sum);
        }
    }

    /** The channel of {@code storage}, or null. */
    public static @Nullable Channel channel(@NotNull String storage) {
        return CHANNELS.stream().filter(channel -> channel.storage().equals(storage)).findFirst().orElse(null);
    }

    /** Whether {@code category} is carried by the items channel (the platform serializes those differently). */
    public static boolean isItems(@NotNull ResourceCategory category) {
        return category.equals(Items.CATEGORY);
    }
}
