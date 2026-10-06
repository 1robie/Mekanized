package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.DataKeys;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * The reactor family: registers the boiler, turbine, fission reactor, fusion reactor and supercritical phase
 * shifter ({@link Boilers}, {@link Turbines}, {@link Fission}, {@link Fusion}, {@link Sps}) and holds what they share:
 * the status and running data, the port modes and the helpers of their work.
 */
public final class ReactorMachines {
    static final ResourcePredicate IS_ENERGY = ResourcePredicate.type(MekanizedContent.ENERGY);
    static final ResourcePredicate IS_HEAT = ResourcePredicate.type(MekanizedContent.HEAT);
    static final ResourcePredicate IS_CHEMICAL = ResourcePredicate.category(MekanizedContent.CHEMICAL);
    static final ResourcePredicate IS_FLUID = ResourcePredicate.category(MekanizedContent.FLUID);
    static final Set<Key> AIR = Set.of(Key.key("minecraft", "air"), Key.key("minecraft", "cave_air"), Key.key("minecraft", "void_air"));
    static final long EVERY_TICKS = 20;

    /** What the structure is doing, shown in its menu. */
    public static final MachineDataKey<String> STATUS = DataKeys.string("status", "Not formed");
    public static final MachineDataKey<Boolean> RUNNING = DataKeys.RUNNING;

    private ReactorMachines() {
    }

    public static void register(RegistrationContext context) {
        Boilers.register(context);
        Turbines.register(context);
        Fission.register(context);
        Fusion.register(context);
        Sps.register(context);
    }

    /** The modes a configurator cycles through on a valve or port, by block; Mekanism's order. */
    public static final Map<String, List<String>> MODES = Map.of(
            "boiler_valve", List.of("input", "output_steam", "output_coolant"),
            "fission_reactor_port", List.of("input", "output_coolant", "output_waste"),
            "fusion_reactor_port", List.of("input", "output"),
            "sps_port", List.of("input", "output"));

    static MachineType.Builder valve(String name, List<Multiblocks.Type> types, List<String> insertInto, Map<String, List<String>> pushes) {
        return MachineType.builder(key(name)).data(MemberBehavior.PORT_MODE).behavior(MemberBehavior.modal(types, insertInto, pushes));
    }

    /** Blocks a structure counts inside it, doing nothing themselves. */
    static void inert(RegistrationContext context, String... names) {
        for (String name : names) context.machineTypes().register(MachineType.builder(key(name)).behavior(sleeping()).build());
    }

    static MachineBehavior.Factory sleeping() {
        return type -> MachineContext::sleep;
    }

    static ResourcePredicate named(String name) {
        return (variant, tags) -> variant.type().key().value().equals(name) || variant.type().key().value().equals("fluid/" + name);
    }

    // -- helpers ---------------------------------------------------------------------------------

    static void idle(MachineContext anchor, String status) {
        anchor.setActive(false);
        anchor.set(STATUS, status);
    }

    static void working(MachineContext anchor, String status) {
        anchor.setActive(true);
        anchor.set(STATUS, status);
    }

    static long amount(MachineContext anchor, String storage) {
        return anchor.storage(storage).views().stream().mapToLong(StorageView::amount).sum();
    }

    static long room(MachineContext anchor, String storage) {
        return anchor.storage(storage).views().stream().mapToLong(view -> view.capacity() - view.amount()).sum();
    }

    static ResourceVariant variant(MachineContext anchor, String storage) {
        ResourceHandler handler = anchor.storage(storage);
        return handler.views().stream().filter(view -> !view.isEmpty()).findFirst().orElseThrow().variant();
    }

    static ResourceVariant chemical(String name) {
        ResourceType type = MekanizedContent.chemical(name);
        if (type == null) throw new IllegalStateException("no chemical " + name);
        return type.variant();
    }

    static ResourceVariant fluid(String name) {
        ResourceType type = MekanizedContent.fluid(name);
        if (type == null) throw new IllegalStateException("no fluid " + name);
        return type.variant();
    }
}
