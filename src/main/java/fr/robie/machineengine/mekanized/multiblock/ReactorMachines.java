package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.BlockPosition;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.DataKeys;
import fr.robie.machineengine.mekanized.machine.Radiation;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * Phase 6 wave 3 (MEKANIZED_PLAN.md): the thermoelectric boiler, the industrial turbine, the fission
 * and fusion reactors and the supercritical phase shifter, as boxes of frame blocks (Multiblocks).
 * Their anchor (lowest corner) holds the structure's tanks and does the work every second.
 *
 * <p>Simplified from Mekanism, with its scale: boiling 1 mB of water (or heating 1 mB of sodium) takes
 * {@link #HEAT_PER_MB} J of heat, 1 mB of fissile fuel gives 1 MJ, so 1 mB/t of fuel boils 20,000 mB/t;
 * a turbine makes up to 10 J per mB of steam, with 28 blades and enough coils.</p>
 */
public final class ReactorMachines {
    private static final ResourcePredicate IS_ENERGY = ResourcePredicate.type(MekanizedContent.ENERGY);
    private static final ResourcePredicate IS_HEAT = ResourcePredicate.type(MekanizedContent.HEAT);
    private static final ResourcePredicate IS_CHEMICAL = ResourcePredicate.category(MekanizedContent.CHEMICAL);
    private static final ResourcePredicate IS_FLUID = ResourcePredicate.category(MekanizedContent.FLUID);
    private static final Set<Key> AIR = Set.of(Key.key("minecraft", "air"), Key.key("minecraft", "cave_air"), Key.key("minecraft", "void_air"));
    static final long EVERY_TICKS = 20;

    /** Heat to boil 1 mB of water into steam, or to heat 1 mB of sodium; what 1 mB of superheated sodium gives back. */
    public static final long HEAT_PER_MB = 50;
    /** mB of water each superheating element boils per tick at most. */
    public static final long BOIL_PER_ELEMENT = 320;
    /** Joules of heat per mB of fissile fuel burned. */
    public static final long FISSION_HEAT_PER_MB = 1_000_000;
    /** A turbine's Joules per mB of steam with all its blades driven. */
    public static final double STEAM_ENERGY = 10;
    /** Blades for a turbine's full yield, blades a coil drives, steam a vent lets through per tick. */
    public static final int FULL_BLADES = 28;
    public static final int BLADES_PER_COIL = 4;
    public static final long VENT_FLOW = 32_000;
    public static final long CONDENSER_RATE = 64_000;
    /** Fusion: laser energy to ignite, Joules per mB of fuel burned. */
    public static final long FUSION_IGNITION = 400_000_000;
    public static final long FUSION_ENERGY_PER_MB = 200_000;
    /** SPS: mB of polonium per mB of antimatter, Joules per mB of polonium. */
    public static final long SPS_POLONIUM_PER_ANTIMATTER = 1_000;
    public static final long SPS_ENERGY_PER_POLONIUM = 1_000_000;

    // -- settings and state shown in the menus --------------------------------------------------
    /** What the structure is doing, shown in its menu. */
    public static final MachineDataKey<String> STATUS = DataKeys.string("status", "Not formed");
    public static final MachineDataKey<Boolean> RUNNING = DataKeys.RUNNING;
    /** Fission burn rate in mB/t; fusion injection rate in mB/t. */
    public static final MachineDataKey<String> BURN_RATE = DataKeys.string("burn_rate", "0.1");
    public static final MachineDataKey<String> INJECTION_RATE = DataKeys.string("injection_rate", "2");
    /** Heat the fission reactor holds (J) and its damage (0 to 100, a SCRAM at 100). */
    public static final MachineDataKey<Long> REACTOR_HEAT = DataKeys.number("reactor_heat");
    public static final MachineDataKey<Long> DAMAGE = DataKeys.number("damage");
    public static final MachineDataKey<Boolean> IGNITED = MachineDataKey.of("ignited",
            VersionedCodec.builder(key("ignited"), Codecs.BOOL).build(), false);
    /** Polonium processed towards the next mB of antimatter. */
    public static final MachineDataKey<Long> SPS_PROGRESS = DataKeys.number("sps_progress");

    // -- structures ------------------------------------------------------------------------------

    public static final Multiblocks.Type BOILER = new Multiblocks.Type(key("boiler"), key("boiler_casing"),
            Set.of(key("boiler_casing"), key("boiler_valve"), key("structural_glass")),
            block -> AIR.contains(block) || block.equals(key("pressure_disperser")) || block.equals(key("superheating_element")),
            structure -> Map.of("water", structure.volume() * 16_000L, "steam", structure.volume() * 160_000L,
                    "heat", structure.volume() * 1_000_000L, "coolant", structure.volume() * 16_000L, "cooled", structure.volume() * 16_000L));

    public static final Multiblocks.Type TURBINE = new Multiblocks.Type(key("turbine"), key("turbine_casing"),
            Set.of(key("turbine_casing"), key("turbine_valve"), key("turbine_vent"), key("structural_glass")),
            block -> AIR.contains(block) || Set.of("turbine_rotor", "rotational_complex", "electromagnetic_coil", "pressure_disperser",
                    "saturating_condenser").contains(block.value()) && block.namespace().equals(MekanizedContent.NAMESPACE),
            structure -> Map.of("steam", structure.volume() * 64_000L, "energy", structure.volume() * 16_000_000L,
                    "water", Math.max(1, structure.count(key("saturating_condenser")) * CONDENSER_RATE * EVERY_TICKS)));

    public static final Multiblocks.Type FISSION = new Multiblocks.Type(key("fission_reactor"), key("fission_reactor_casing"),
            Set.of(key("fission_reactor_casing"), key("fission_reactor_port"), key("fission_reactor_logic_adapter"), key("reactor_glass")),
            block -> AIR.contains(block) || block.equals(key("fission_fuel_assembly")) || block.equals(key("control_rod_assembly")),
            structure -> {
                long assemblies = Math.max(1, structure.count(key("fission_fuel_assembly")));
                return Map.of("fuel", assemblies * 8_000, "waste", assemblies * 8_000, "water", assemblies * 100_000,
                        "sodium", assemblies * 100_000, "steam", assemblies * 1_000_000, "hot", assemblies * 1_000_000);
            });

    public static final Multiblocks.Type FUSION = new Multiblocks.Type(key("fusion_reactor"), key("fusion_reactor_frame"),
            Set.of(key("fusion_reactor_frame"), key("fusion_reactor_port"), key("fusion_reactor_logic_adapter"),
                    key("laser_focus_matrix"), key("reactor_glass"), key("fusion_reactor_controller")),
            AIR::contains,
            structure -> Map.of("deuterium", 10_000L, "tritium", 10_000L, "energy", 1_000_000_000L, "ignition", FUSION_IGNITION),
            0, false, key("fusion_reactor_controller"));

    public static final Multiblocks.Type SPS = new Multiblocks.Type(key("sps"), key("sps_casing"),
            Set.of(key("sps_casing"), key("sps_port"), key("reactor_glass"), key("structural_glass")),
            AIR::contains,
            structure -> Map.of("polonium", 10_000L, "antimatter", 1_000L, "energy", 2_000_000_000L));

    private ReactorMachines() {
    }

    public static void register(RegistrationContext context) {
        List<Multiblocks.Type> boiler = List.of(BOILER);
        context.machineTypes().register(MachineType.builder(key("boiler_casing"))
                .storage(StorageDefinition.tank("water", 1).filter(IS_FLUID.and(named("water"))))
                .storage(StorageDefinition.tank("steam", 1).filter(IS_CHEMICAL.and(named("steam"))))
                .storage(StorageDefinition.tank("heat", 1).filter(IS_HEAT))
                .storage(StorageDefinition.tank("coolant", 1).filter(IS_CHEMICAL.and(named("superheated_sodium"))))
                .storage(StorageDefinition.tank("cooled", 1).filter(IS_CHEMICAL.and(named("sodium"))))
                .data(STATUS)
                .behavior(MemberBehavior.frame(boiler, ReactorMachines::boil, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("boiler_valve", boiler, List.of("water", "coolant", "heat"),
                Map.of("input", List.of(), "output_steam", List.of("steam"), "output_coolant", List.of("cooled")))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("heat", 1).filter(IS_HEAT))
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("heat", "heat").filter(IS_HEAT).group("heat").build())
                .build());
        inert(context, "pressure_disperser", "superheating_element");

        List<Multiblocks.Type> turbine = List.of(TURBINE);
        context.machineTypes().register(MachineType.builder(key("turbine_casing"))
                .storage(StorageDefinition.tank("steam", 1).filter(IS_CHEMICAL.and(named("steam"))))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .storage(StorageDefinition.tank("water", 1).filter(IS_FLUID.and(named("water"))))
                .data(STATUS)
                .behavior(MemberBehavior.frame(turbine, ReactorMachines::turn, EVERY_TICKS))
                .build());
        // Valves take steam and give energy and condensed water, like Mekanism's (no modes).
        context.machineTypes().register(valve("turbine_valve", turbine, List.of("steam"), Map.of("input", List.of("energy", "water")))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("energy", "energy").filter(IS_ENERGY).group("energy").build())
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .build());
        context.machineTypes().register(MachineType.builder(key("turbine_vent")).behavior(MemberBehavior.frame(turbine)).build());
        // A rotor holds up to two blades (right-click it with turbine blades).
        context.machineTypes().register(MachineType.builder(key("turbine_rotor"))
                .storage(StorageDefinition.slots("blades", 1, 2).filter(Items.ofItem(key("turbine_blade"))))
                .behavior(sleeping())
                .build());
        inert(context, "rotational_complex", "electromagnetic_coil", "saturating_condenser");

        List<Multiblocks.Type> fission = List.of(FISSION);
        context.machineTypes().register(MachineType.builder(key("fission_reactor_casing"))
                .storage(StorageDefinition.tank("fuel", 1).filter(IS_CHEMICAL.and(named("fissile_fuel"))))
                .storage(StorageDefinition.tank("waste", 1).filter(IS_CHEMICAL.and(named("nuclear_waste"))))
                .storage(StorageDefinition.tank("water", 1).filter(IS_FLUID.and(named("water"))))
                .storage(StorageDefinition.tank("sodium", 1).filter(IS_CHEMICAL.and(named("sodium"))))
                .storage(StorageDefinition.tank("steam", 1).filter(IS_CHEMICAL.and(named("steam"))))
                .storage(StorageDefinition.tank("hot", 1).filter(IS_CHEMICAL.and(named("superheated_sodium"))))
                .data(STATUS).data(RUNNING).data(BURN_RATE).data(REACTOR_HEAT).data(DAMAGE)
                .behavior(MemberBehavior.frame(fission, ReactorMachines::fission, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("fission_reactor_port", fission, List.of("fuel", "water", "sodium"),
                Map.of("input", List.of(), "output_coolant", List.of("steam", "hot"), "output_waste", List.of("waste")))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .build());
        context.machineTypes().register(MachineType.builder(key("fission_reactor_logic_adapter")).behavior(MemberBehavior.frame(fission)).build());
        context.machineTypes().register(MachineType.builder(key("reactor_glass")).behavior(MemberBehavior.frame(List.of(FISSION, FUSION, SPS))).build());
        inert(context, "fission_fuel_assembly", "control_rod_assembly");

        List<Multiblocks.Type> fusion = List.of(FUSION);
        context.machineTypes().register(MachineType.builder(key("fusion_reactor_frame"))
                .storage(StorageDefinition.tank("deuterium", 1).filter(IS_CHEMICAL.and(named("deuterium"))))
                .storage(StorageDefinition.tank("tritium", 1).filter(IS_CHEMICAL.and(named("tritium"))))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .storage(StorageDefinition.tank("ignition", 1).filter(IS_ENERGY))
                .data(STATUS).data(INJECTION_RATE).data(IGNITED)
                .behavior(MemberBehavior.frame(fusion, ReactorMachines::fuse, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("fusion_reactor_port", fusion, List.of("deuterium", "tritium"),
                Map.of("input", List.of(), "output", List.of("energy")))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("energy", "energy").filter(IS_ENERGY).group("energy").build())
                .build());
        for (String name : List.of("fusion_reactor_controller", "fusion_reactor_logic_adapter", "laser_focus_matrix")) {
            context.machineTypes().register(MachineType.builder(key(name)).behavior(MemberBehavior.frame(fusion)).build());
        }

        List<Multiblocks.Type> sps = List.of(SPS);
        context.machineTypes().register(MachineType.builder(key("sps_casing"))
                .storage(StorageDefinition.tank("polonium", 1).filter(IS_CHEMICAL.and(named("polonium"))))
                .storage(StorageDefinition.tank("antimatter", 1).filter(IS_CHEMICAL.and(named("antimatter"))))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .data(STATUS).data(SPS_PROGRESS)
                .behavior(MemberBehavior.frame(sps, ReactorMachines::shift, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("sps_port", sps, List.of("polonium", "energy"), Map.of("input", List.of(), "output", List.of("antimatter")))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("energy", "energy").filter(IS_ENERGY).group("energy").build())
                .build());
        inert(context, "supercharged_coil");
    }

    /** The modes a configurator cycles through on a valve or port, by block; Mekanism's order. */
    public static final Map<String, List<String>> MODES = Map.of(
            "boiler_valve", List.of("input", "output_steam", "output_coolant"),
            "fission_reactor_port", List.of("input", "output_coolant", "output_waste"),
            "fusion_reactor_port", List.of("input", "output"),
            "sps_port", List.of("input", "output"));

    private static MachineType.Builder valve(String name, List<Multiblocks.Type> types, List<String> insertInto, Map<String, List<String>> pushes) {
        return MachineType.builder(key(name)).data(MemberBehavior.PORT_MODE).behavior(MemberBehavior.modal(types, insertInto, pushes));
    }

    /** Blocks a structure counts inside it, doing nothing themselves. */
    private static void inert(RegistrationContext context, String... names) {
        for (String name : names) context.machineTypes().register(MachineType.builder(key(name)).behavior(sleeping()).build());
    }

    private static MachineBehavior.Factory sleeping() {
        return type -> MachineContext::sleep;
    }

    private static ResourcePredicate named(String name) {
        return (variant, tags) -> variant.type().key().value().equals(name) || variant.type().key().value().equals("fluid/" + name);
    }

    // -- the work, every EVERY_TICKS ------------------------------------------------------------

    /** Boiler: heat (from heaters, and from superheated sodium) boils water into steam, up to the elements' rate. */
    static void boil(MachineContext anchor, Multiblocks.Structure structure) {
        long elements = structure.count(key("superheating_element"));
        if (structure.count(key("pressure_disperser")) == 0) {
            idle(anchor, "Needs a pressure disperser inside");
            return;
        }
        if (elements == 0) {
            idle(anchor, "Needs superheating elements inside");
            return;
        }
        // Superheated sodium gives its heat back and leaves as sodium.
        long cooled = Math.min(amount(anchor, "coolant"), room(anchor, "cooled"));
        if (cooled > 0) {
            try (Transaction transaction = anchor.transactions().openOuter()) {
                anchor.storage("coolant").extract(variant(anchor, "coolant"), cooled, transaction);
                anchor.storage("cooled").insert(chemical("sodium"), cooled, transaction);
                anchor.storage("heat").insert(MekanizedContent.HEAT.variant(), cooled * HEAT_PER_MB, transaction);
                transaction.commit();
            }
        }
        long boiled = Math.min(Math.min(amount(anchor, "water"), amount(anchor, "heat") / HEAT_PER_MB),
                Math.min(elements * BOIL_PER_ELEMENT * EVERY_TICKS, room(anchor, "steam")));
        if (boiled <= 0) {
            idle(anchor, amount(anchor, "water") == 0 ? "Needs water" : amount(anchor, "heat") < HEAT_PER_MB ? "Needs heat" : "Steam tank full");
            return;
        }
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("water").extract(variant(anchor, "water"), boiled, transaction);
            anchor.storage("heat").extract(MekanizedContent.HEAT.variant(), boiled * HEAT_PER_MB, transaction);
            anchor.storage("steam").insert(chemical("steam"), boiled, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "Boiling %,d mB/t", boiled / EVERY_TICKS));
    }

    /** Turbine: steam through the vents turns the blades; coils make energy; condensers give water back. */
    static void turn(MachineContext anchor, Multiblocks.Structure structure) {
        long vents = 0;
        long blades = 0;
        BlockPosition min = structure.min();
        BlockPosition max = structure.max();
        for (int x = min.x(); x <= max.x(); x++) for (int y = min.y(); y <= max.y(); y++) for (int z = min.z(); z <= max.z(); z++) {
            BlockPosition at = new BlockPosition(x, y, z);
            Key block = anchor.world().blockAt(at);
            if (block == null) continue;
            if (block.equals(key("turbine_vent"))) vents++;
            else if (block.equals(key("turbine_rotor"))) {
                blades += anchor.machines().storage(new WorldPosition(structure.world(), at), "blades")
                        .map(handler -> handler.views().stream().mapToLong(StorageView::amount).sum()).orElse(0L);
            }
        }
        long coils = structure.count(key("electromagnetic_coil"));
        if (structure.count(key("rotational_complex")) == 0 || coils == 0 || vents == 0) {
            idle(anchor, "Needs a rotational complex, coils and vents");
            return;
        }
        if (blades == 0) {
            idle(anchor, "Needs blades on its rotors");
            return;
        }
        double perMb = STEAM_ENERGY * Math.min(1.0, Math.min(blades, coils * BLADES_PER_COIL) / (double) FULL_BLADES);
        long steam = Math.min(amount(anchor, "steam"), vents * VENT_FLOW * EVERY_TICKS);
        steam = Math.min(steam, (long) (room(anchor, "energy") / perMb));
        if (steam <= 0) {
            idle(anchor, amount(anchor, "steam") == 0 ? "Needs steam" : "Energy buffer full");
            return;
        }
        long energy = (long) (steam * perMb);
        long water = Math.min(Math.min(steam, structure.count(key("saturating_condenser")) * CONDENSER_RATE * EVERY_TICKS), room(anchor, "water"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("steam").extract(chemical("steam"), steam, transaction);
            anchor.storage("energy").insert(MekanizedContent.ENERGY.variant(), energy, transaction);
            if (water > 0) anchor.storage("water").insert(fluid("water"), water, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "%,d J/t from %,d mB/t of steam (%d blades)", energy / EVERY_TICKS, steam / EVERY_TICKS, blades));
    }

    /** Fission: burns fuel into waste and heat; coolant carries the heat away; too hot, it SCRAMs. */
    static void fission(MachineContext anchor, Multiblocks.Structure structure) {
        long assemblies = structure.count(key("fission_fuel_assembly"));
        long heat = anchor.get(REACTOR_HEAT);
        long damage = anchor.get(DAMAGE);
        long burned = 0;
        if (anchor.get(RUNNING) && assemblies > 0) {
            double rate = DataKeys.parseDouble(anchor.get(BURN_RATE), 0.1, 0, Double.POSITIVE_INFINITY);
            // At most 1 mB/t per fuel assembly.
            burned = Math.min(Math.round(Math.min(rate, assemblies) * EVERY_TICKS), Math.min(amount(anchor, "fuel"), room(anchor, "waste")));
            if (burned > 0) {
                try (Transaction transaction = anchor.transactions().openOuter()) {
                    anchor.storage("fuel").extract(chemical("fissile_fuel"), burned, transaction);
                    anchor.storage("waste").insert(chemical("nuclear_waste"), burned, transaction);
                    transaction.commit();
                }
                heat += burned * FISSION_HEAT_PER_MB;
            }
        }
        // Coolant: water into steam, sodium into superheated sodium.
        heat -= cool(anchor, "water", "steam", chemical("steam"), heat);
        heat -= cool(anchor, "sodium", "hot", chemical("superheated_sodium"), heat);
        heat -= heat / 100; // the casing loses a little
        long safe = Math.max(1, assemblies) * 5L * FISSION_HEAT_PER_MB;
        if (heat > safe) damage = Math.min(100, damage + Math.max(1, (heat - safe) * 10 / safe));
        else if (damage > 0) damage--;
        if (damage >= 100 && anchor.get(RUNNING)) {
            // SCRAM; the breach lets the waste out as radiation (Mekanism's meltdown, without the explosion).
            anchor.set(RUNNING, false);
            long waste = amount(anchor, "waste");
            if (waste > 0) {
                try (Transaction transaction = anchor.transactions().openOuter()) {
                    anchor.storage("waste").extract(chemical("nuclear_waste"), waste, transaction);
                    transaction.commit();
                }
                Radiation.release(anchor.machine().position(),
                        waste * Radiation.RADIOACTIVITY.get("nuclear_waste"));
            }
        }
        anchor.set(REACTOR_HEAT, heat);
        anchor.set(DAMAGE, damage);
        String state = !anchor.get(RUNNING) ? (damage >= 100 ? "SCRAM: too hot" : "Stopped")
                : assemblies == 0 ? "Needs fuel assemblies inside" : burned == 0 ? "Needs fissile fuel" : "Burning";
        anchor.setActive(burned > 0);
        anchor.set(STATUS, String.format(Locale.ROOT, "%s - %,d mB/s burned, heat %,d kJ, damage %d %%", state, burned, heat / 1000, damage));
    }

    /** Moves up to {@code heat} of the reactor's heat into coolant; returns the heat taken. */
    private static long cool(MachineContext anchor, String from, String to, ResourceVariant made, long heat) {
        long amount = Math.min(Math.min(amount(anchor, from), room(anchor, to)), heat / HEAT_PER_MB);
        if (amount <= 0) return 0;
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage(from).extract(variant(anchor, from), amount, transaction);
            anchor.storage(to).insert(made, amount, transaction);
            transaction.commit();
        }
        return amount * HEAT_PER_MB;
    }

    /** Fusion: lasers ignite it; then it burns deuterium and tritium (half each of the injection rate) into energy. */
    static void fuse(MachineContext anchor, Multiblocks.Structure structure) {
        if (!anchor.get(IGNITED)) {
            boolean fueled = amount(anchor, "deuterium") > 0 && amount(anchor, "tritium") > 0;
            if (amount(anchor, "ignition") >= FUSION_IGNITION && fueled) {
                try (Transaction transaction = anchor.transactions().openOuter()) {
                    anchor.storage("ignition").extract(MekanizedContent.ENERGY.variant(), FUSION_IGNITION, transaction);
                    transaction.commit();
                }
                anchor.set(IGNITED, true);
            } else {
                idle(anchor, String.format(Locale.ROOT, "Not ignited: %,d / %,d MJ of laser energy%s",
                        amount(anchor, "ignition") / 1_000_000, FUSION_IGNITION / 1_000_000, fueled ? "" : ", needs deuterium and tritium"));
                return;
            }
        }
        long rate = DataKeys.parseLong(anchor.get(INJECTION_RATE), 2, 2, Long.MAX_VALUE);
        long each = rate / 2 * EVERY_TICKS;
        if (amount(anchor, "deuterium") < each || amount(anchor, "tritium") < each) {
            anchor.set(IGNITED, false); // the plasma goes out
            idle(anchor, "Out of fuel: needs igniting again");
            return;
        }
        long energy = Math.min(2 * each * FUSION_ENERGY_PER_MB, room(anchor, "energy"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("deuterium").extract(chemical("deuterium"), each, transaction);
            anchor.storage("tritium").extract(chemical("tritium"), each, transaction);
            anchor.storage("energy").insert(MekanizedContent.ENERGY.variant(), energy, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "Ignited: %,d J/t", energy / EVERY_TICKS));
    }

    /** SPS: energy turns polonium into antimatter, 1,000 mB and 1 GJ per mB. */
    static void shift(MachineContext anchor, Multiblocks.Structure structure) {
        long polonium = Math.min(amount(anchor, "polonium"), amount(anchor, "energy") / SPS_ENERGY_PER_POLONIUM);
        long progress = anchor.get(SPS_PROGRESS);
        if (room(anchor, "antimatter") <= 0) polonium = 0;
        if (polonium <= 0) {
            idle(anchor, amount(anchor, "polonium") == 0 ? "Needs polonium" : "Needs energy");
            return;
        }
        progress += polonium;
        long made = Math.min(progress / SPS_POLONIUM_PER_ANTIMATTER, room(anchor, "antimatter"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("polonium").extract(chemical("polonium"), polonium, transaction);
            anchor.storage("energy").extract(MekanizedContent.ENERGY.variant(), polonium * SPS_ENERGY_PER_POLONIUM, transaction);
            if (made > 0) anchor.storage("antimatter").insert(chemical("antimatter"), made, transaction);
            transaction.commit();
        }
        anchor.set(SPS_PROGRESS, progress - made * SPS_POLONIUM_PER_ANTIMATTER);
        working(anchor, String.format(Locale.ROOT, "Shifting %,d mB/s of polonium (%d %% to the next mB)", polonium,
                anchor.get(SPS_PROGRESS) * 100 / SPS_POLONIUM_PER_ANTIMATTER));
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static void idle(MachineContext anchor, String status) {
        anchor.setActive(false);
        anchor.set(STATUS, status);
    }

    private static void working(MachineContext anchor, String status) {
        anchor.setActive(true);
        anchor.set(STATUS, status);
    }

    private static long amount(MachineContext anchor, String storage) {
        return anchor.storage(storage).views().stream().mapToLong(StorageView::amount).sum();
    }

    private static long room(MachineContext anchor, String storage) {
        return anchor.storage(storage).views().stream().mapToLong(view -> view.capacity() - view.amount()).sum();
    }

    private static ResourceVariant variant(MachineContext anchor, String storage) {
        ResourceHandler handler = anchor.storage(storage);
        return handler.views().stream().filter(view -> !view.isEmpty()).findFirst().orElseThrow().variant();
    }

    private static ResourceVariant chemical(String name) {
        ResourceType type = MekanizedContent.chemical(name);
        if (type == null) throw new IllegalStateException("no chemical " + name);
        return type.variant();
    }

    private static ResourceVariant fluid(String name) {
        ResourceType type = MekanizedContent.fluid(name);
        if (type == null) throw new IllegalStateException("no fluid " + name);
        return type.variant();
    }
}
