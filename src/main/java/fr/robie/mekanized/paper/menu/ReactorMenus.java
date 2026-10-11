package fr.robie.mekanized.paper.menu;

import fr.robie.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.mekanized.paper.menu.MenuLayout.Tank;
import fr.robie.mekanized.paper.menu.MenuYaml.Menu;
import java.util.List;
import static fr.robie.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.mekanized.paper.menu.MenuYaml.*;

/** The menus of the heaters and of the boiler, turbine, fission reactor, fusion reactor and SPS structures. */
final class ReactorMenus {
    private ReactorMenus() {
    }

    static void add(List<Menu> menus) {
        menus.add(new MenuLayout("resistive_heater", 5)
                .button("info", display(at(0, 4), "MAGMA_BLOCK", "<gold>Resistive Heater", "<gray>Turns energy into heat, given to the blocks",
                        "<gray>around it (a boiler valve, conductors)"))
                .energy(1, 0)
                .heat("heat", 2, 2, "heat", "Heat")
                .button("rate", setting(at(2, 6), "heater_rate", List.of("100", "1000", "10000", "100000"), "BLAZE_POWDER",
                        "<yellow>Energy use: %zmenu_machineengine_data_heater_rate% J/t"))
                .build());

        menus.add(new MenuLayout("fuelwood_heater", 5)
                .button("info", display(at(0, 4), "CAMPFIRE", "<gold>Fuelwood Heater", "<gray>Burns furnace fuels for heat (400 J/t)"))
                .slot("fuel", 2, 1, Kind.INPUT, "fuel", "Fuel")
                .heat("heat", 2, 3, "heat", "Heat")
                .button("state", display(at(2, 7), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"))
                .build());

        menus.add(new MenuLayout("boiler_casing", 5)
                .button("info", display(at(0, 4), "CAULDRON", "<aqua>Thermoelectric Boiler", "<gray>Heat boils water into steam: heaters on a valve,",
                        "<gray>or superheated sodium from a fission reactor.", "<gray>Inside: a layer of pressure dispersers, superheating elements",
                        "<gray>Configurator on a valve: input, steam out, sodium out"))
                .tank("water", 1, 0, Tank.FLUID, "water", "Water")
                .heat("heat", 2, 1, "heat", "Heat")
                .tank("steam", 1, 4, Tank.CHEMICAL, "steam", "Steam")
                .tank("coolant", 1, 6, Tank.CHEMICAL, "coolant", "Superheated sodium (in)")
                .tank("cooled", 1, 7, Tank.CHEMICAL, "cooled", "Sodium (out)")
                .button("status", status(at(4, 4)))
                .build());

        menus.add(new MenuLayout("turbine_casing", 5)
                .button("info", display(at(0, 4), "IRON_BARS", "<aqua>Industrial Turbine", "<gray>Steam turns the rotors (blades: right-click a rotor),",
                        "<gray>coils make energy, vents let steam through,", "<gray>condensers give water back. Valves give energy and water"))
                .tank("steam", 1, 1, Tank.CHEMICAL, "steam", "Steam")
                .energy(1, 4)
                .tank("water", 1, 7, Tank.FLUID, "water", "Condensed water")
                .button("status", status(at(4, 4)))
                .build());

        menus.add(new MenuLayout("fission_reactor_casing", 5)
                .button("info", display(at(0, 4), "BEACON", "<green>Fission Reactor", "<gray>Burns fissile fuel; water or sodium cools it.",
                        "<gray>Too hot: damage, then a SCRAM (it stops).", "<gray>Configurator on a port: input, coolant out, waste out"))
                .tank("fuel", 1, 0, Tank.CHEMICAL, "fuel", "Fissile fuel")
                .tank("waste", 1, 1, Tank.CHEMICAL, "waste", "Nuclear waste")
                .tank("water", 1, 2, Tank.FLUID, "water", "Water (coolant)")
                .tank("steam", 1, 3, Tank.CHEMICAL, "steam", "Steam")
                .tank("sodium", 1, 4, Tank.CHEMICAL, "sodium", "Sodium (coolant)")
                .tank("hot", 1, 5, Tank.CHEMICAL, "hot", "Superheated sodium")
                .button("running", "    type: machineengine_data\n    data: running\n    slot: " + at(2, 7) + "\n    update: true\n"
                        + item("LEVER", "<red>Running: <yellow>%zmenu_machineengine_data_running%", "<dark_gray>Click to start or stop"))
                .button("rate", setting(at(3, 7), "burn_rate", List.of("0.1", "1", "5", "10", "50", "100"), "BLAZE_POWDER",
                        "<yellow>Burn rate: %zmenu_machineengine_data_burn_rate% mB/t", "<gray>At most 1 mB/t per fuel assembly"))
                .button("status", status(at(4, 4)))
                .build());

        menus.add(new MenuLayout("fusion_reactor_frame", 5)
                .button("info", display(at(0, 4), "END_CRYSTAL", "<light_purple>Fusion Reactor", "<gray>Ignite it with lasers into a laser focus matrix",
                        "<gray>(400 MJ), with deuterium and tritium inside.", "<gray>Configurator on a port: input, energy out"))
                .tank("deuterium", 1, 0, Tank.CHEMICAL, "deuterium", "Deuterium")
                .tank("tritium", 1, 1, Tank.CHEMICAL, "tritium", "Tritium")
                .energy(1, 3)
                .button("ignition", display(at(2, 5), "GLOWSTONE_DUST", "<gold>Laser energy",
                        "<gray>%zmenu_machineengine_storage_ignition_amount% / %zmenu_machineengine_storage_ignition_capacity% J"))
                .button("rate", setting(at(2, 7), "injection_rate", List.of("2", "10", "20", "50", "98"), "BLAZE_POWDER",
                        "<yellow>Injection rate: %zmenu_machineengine_data_injection_rate% mB/t"))
                .button("status", status(at(4, 4)))
                .build());

        menus.add(new MenuLayout("sps_casing", 5)
                .button("info", display(at(0, 4), "NETHER_STAR", "<light_purple>Supercritical Phase Shifter", "<gray>Energy turns polonium into antimatter:",
                        "<gray>1,000 mB and 1 GJ per mB.", "<gray>Configurator on a port: input, antimatter out"))
                .tank("polonium", 1, 0, Tank.CHEMICAL, "polonium", "Polonium")
                .energy(1, 3)
                .tank("antimatter", 1, 6, Tank.CHEMICAL, "antimatter", "Antimatter")
                .button("status", status(at(4, 4)))
                .build());
    }
}
