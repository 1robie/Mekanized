package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the heaters and of the boiler, turbine, fission reactor, fusion reactor and SPS structures. */
final class ReactorMenus {
    private ReactorMenus() {
    }

    static void add(List<Menu> menus) {
        Map<String, String> resistive = new LinkedHashMap<>();
        resistive.put("info", display(4, "MAGMA_BLOCK", "<gold>Resistive Heater", "<gray>Turns energy into heat, given to the blocks",
                "<gray>around it (a boiler valve, conductors)"));
        resistive.put("energy", energyDisplay(11));
        resistive.put("heat", heat(13, "heat", "Heat"));
        resistive.put("rate", setting(15, "heater_rate", List.of("100", "1000", "10000", "100000"), "BLAZE_POWDER",
                "<yellow>Energy use: %zmenu_machineengine_data_heater_rate% J/t"));
        menus.add(new Menu("resistive_heater", resistive));
        Map<String, String> fuelwood = new LinkedHashMap<>();
        fuelwood.put("info", display(4, "CAMPFIRE", "<gold>Fuelwood Heater", "<gray>Burns furnace fuels for heat (400 J/t)"));
        fuelwood.put("fuel", slot(11, "fuel", "Fuel"));
        fuelwood.put("heat", heat(13, "heat", "Heat"));
        fuelwood.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("fuelwood_heater", fuelwood));

        Map<String, String> boiler = new LinkedHashMap<>();
        boiler.put("info", display(4, "CAULDRON", "<aqua>Thermoelectric Boiler", "<gray>Heat boils water into steam: heaters on a valve,",
                "<gray>or superheated sodium from a fission reactor.", "<gray>Inside: a layer of pressure dispersers, superheating elements",
                "<gray>Configurator on a valve: input, steam out, sodium out"));
        boiler.put("water", tank(10, "water", "WATER_BUCKET", "<aqua>", "Water"));
        boiler.put("heat", heat(12, "heat", "Heat"));
        boiler.put("steam", chemical(14, "steam", "Steam"));
        boiler.put("coolant", chemical(28, "coolant", "Superheated sodium (in)"));
        boiler.put("cooled", chemical(30, "cooled", "Sodium (out)"));
        boiler.put("status", status(22));
        menus.add(new Menu("boiler_casing", boiler));

        Map<String, String> turbine = new LinkedHashMap<>();
        turbine.put("info", display(4, "IRON_BARS", "<aqua>Industrial Turbine", "<gray>Steam turns the rotors (blades: right-click a rotor),",
                "<gray>coils make energy, vents let steam through,", "<gray>condensers give water back. Valves give energy and water"));
        turbine.put("steam", chemical(11, "steam", "Steam"));
        turbine.put("energy", energyDisplay(13));
        turbine.put("water", tank(15, "water", "WATER_BUCKET", "<aqua>", "Condensed water"));
        turbine.put("status", status(22));
        menus.add(new Menu("turbine_casing", turbine));

        Map<String, String> fission = new LinkedHashMap<>();
        fission.put("info", display(4, "BEACON", "<green>Fission Reactor", "<gray>Burns fissile fuel; water or sodium cools it.",
                "<gray>Too hot: damage, then a SCRAM (it stops).", "<gray>Configurator on a port: input, coolant out, waste out"));
        fission.put("fuel", chemical(10, "fuel", "Fissile fuel"));
        fission.put("waste", chemical(11, "waste", "Nuclear waste"));
        fission.put("water", tank(19, "water", "WATER_BUCKET", "<aqua>", "Water (coolant)"));
        fission.put("steam", chemical(20, "steam", "Steam"));
        fission.put("sodium", chemical(28, "sodium", "Sodium (coolant)"));
        fission.put("hot", chemical(29, "hot", "Superheated sodium"));
        fission.put("running", "    type: machineengine_data\n    data: running\n    slot: 15\n    update: true\n"
                + item("LEVER", "<red>Running: <yellow>%zmenu_machineengine_data_running%", "<dark_gray>Click to start or stop"));
        fission.put("rate", setting(16, "burn_rate", List.of("0.1", "1", "5", "10", "50", "100"), "BLAZE_POWDER",
                "<yellow>Burn rate: %zmenu_machineengine_data_burn_rate% mB/t", "<gray>At most 1 mB/t per fuel assembly"));
        fission.put("status", status(24));
        menus.add(new Menu("fission_reactor_casing", fission));

        Map<String, String> fusion = new LinkedHashMap<>();
        fusion.put("info", display(4, "END_CRYSTAL", "<light_purple>Fusion Reactor", "<gray>Ignite it with lasers into a laser focus matrix",
                "<gray>(400 MJ), with deuterium and tritium inside.", "<gray>Configurator on a port: input, energy out"));
        fusion.put("deuterium", chemical(10, "deuterium", "Deuterium"));
        fusion.put("tritium", chemical(11, "tritium", "Tritium"));
        fusion.put("energy", energyDisplay(13));
        fusion.put("ignition", display(14, "GLOWSTONE_DUST", "<gold>Laser energy",
                "<gray>%zmenu_machineengine_storage_ignition_amount% / %zmenu_machineengine_storage_ignition_capacity% J"));
        fusion.put("rate", setting(16, "injection_rate", List.of("2", "10", "20", "50", "98"), "BLAZE_POWDER",
                "<yellow>Injection rate: %zmenu_machineengine_data_injection_rate% mB/t"));
        fusion.put("status", status(22));
        menus.add(new Menu("fusion_reactor_frame", fusion));

        Map<String, String> sps = new LinkedHashMap<>();
        sps.put("info", display(4, "NETHER_STAR", "<light_purple>Supercritical Phase Shifter", "<gray>Energy turns polonium into antimatter:",
                "<gray>1,000 mB and 1 GJ per mB.", "<gray>Configurator on a port: input, antimatter out"));
        sps.put("polonium", chemical(10, "polonium", "Polonium"));
        sps.put("energy", energyDisplay(12));
        sps.put("antimatter", chemical(14, "antimatter", "Antimatter"));
        sps.put("status", status(22));
        menus.add(new Menu("sps_casing", sps));
    }
}
