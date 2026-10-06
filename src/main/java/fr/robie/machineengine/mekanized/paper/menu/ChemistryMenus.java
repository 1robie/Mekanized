package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the chemistry machines, pumps and tanks. */
final class ChemistryMenus {
    private ChemistryMenus() {
    }

    static void add(List<Menu> menus) {
        Map<String, String> oxidizer = machine();
        oxidizer.put("input", slot(10, "input", "Input"));
        oxidizer.put("output", chemical(14, "output", "Output"));
        menus.add(new Menu("chemical_oxidizer", oxidizer));

        Map<String, String> infuser = machine();
        infuser.put("left", chemical(10, "left", "Left input (left side)"));
        infuser.put("right", chemical(11, "right", "Right input (right side)"));
        infuser.put("output", chemical(14, "output", "Output (front)"));
        menus.add(new Menu("chemical_infuser", infuser));

        menus.add(processing("chemical_injection_chamber", false, "Chemical source"));

        Map<String, String> dissolution = machine();
        dissolution.put("input", slot(10, "input", "Input"));
        dissolution.put("chemical", chemical(28, "chemical", "Sulfuric acid"));
        dissolution.put("output", chemical(14, "output", "Output"));
        menus.add(new Menu("chemical_dissolution_chamber", dissolution));

        Map<String, String> washer = machine();
        washer.put("input", chemical(10, "input", "Dirty slurry"));
        washer.put("output", chemical(14, "output", "Clean slurry"));
        buckets(washer, "fluid", "Water", 28);
        menus.add(new Menu("chemical_washer", washer));

        Map<String, String> crystallizer = machine();
        crystallizer.put("input", chemical(10, "input", "Input"));
        crystallizer.put("output", slot(14, "output", "Output"));
        menus.add(new Menu("chemical_crystallizer", crystallizer));

        Map<String, String> separator = machine();
        buckets(separator, "input", "Input fluid", 10);
        separator.put("output-1", chemicalSlot(14, "output", 1, "Output"));
        separator.put("output-2", chemicalSlot(15, "output", 2, "Output"));
        menus.add(new Menu("electrolytic_separator", separator));

        Map<String, String> rotary = machine();
        rotary.put("chemical", chemical(10, "chemical", "Chemical (left side)"));
        buckets(rotary, "fluid", "Fluid (right side)", 14);
        rotary.put("mode", "    type: machineengine_data\n    data: mode\n    values:\n      - condensing\n      - decondensing\n"
                + "    slot: 22\n    update: true\n" + item("COMPARATOR", "<yellow>Mode: %zmenu_machineengine_data_mode%",
                "<gray>condensing: chemical to fluid", "<gray>decondensing: fluid to chemical", "<dark_gray>Click to switch"));
        menus.add(new Menu("rotary_condensentrator", rotary));

        Map<String, String> pump = new LinkedHashMap<>();
        buckets(pump, "fluid", "Pumped fluid", 10);
        pump.put("state", display(12, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%",
                "<gray>Pumps water or lava source blocks below it"));
        pump.put("energy", energyDisplay(16));
        pump.put("energy-slot", slot(34, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        pump.put("upgrades", upgrades());
        menus.add(new Menu("electric_pump", pump));

        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "creative")) {
            Map<String, String> chemicalTank = new LinkedHashMap<>();
            chemicalTank.put("chemical", chemical(13, "chemical", "Stored"));
            chemicalTank.put("chemical-slot", slot(11, "chemical_slot", "Chemical source"));
            chemicalTank.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%", "<gray>Output: front"));
            menus.add(new Menu(tier + "_chemical_tank", chemicalTank));
            Map<String, String> fluidTank = new LinkedHashMap<>();
            buckets(fluidTank, "fluid", "Stored", 13);
            fluidTank.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%", "<gray>Output: bottom"));
            menus.add(new Menu(tier + "_fluid_tank", fluidTank));
        }
    }
}
