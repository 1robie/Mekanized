package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Tank;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.List;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/**
 * The menus of the chemistry machines, pumps and tanks: tanks are tall gauges (rows 1 to 3) with their
 * bucket slots below, the progress arrow runs from the inputs to the outputs across row 2.
 */
final class ChemistryMenus {
    private ChemistryMenus() {
    }

    static void add(List<Menu> menus) {
        menus.add(powered("chemical_oxidizer")
                .slot("input", 2, 1, Kind.INPUT, "input", "Input")
                .progress(2, 2)
                .tank("output", 1, 5, Tank.CHEMICAL, "output", "Output")
                .build());

        menus.add(powered("chemical_infuser")
                .tank("left", 1, 0, Tank.CHEMICAL, "left", "Left input (left side)")
                .tank("right", 1, 1, Tank.CHEMICAL, "right", "Right input (right side)")
                .progress(2, 2)
                .tank("output", 1, 5, Tank.CHEMICAL, "output", "Output (front)")
                .build());

        menus.add(processing("chemical_injection_chamber", false, "Chemical source").build());

        menus.add(powered("chemical_dissolution_chamber")
                .tank("chemical", 1, 0, Tank.CHEMICAL, "chemical", "Sulfuric acid")
                .slot("input", 2, 1, Kind.INPUT, "input", "Input")
                .progress(2, 2)
                .tank("output", 1, 5, Tank.CHEMICAL, "output", "Output")
                .build());

        menus.add(powered("chemical_washer")
                .tank("fluid", 1, 0, Tank.FLUID, "fluid", "Water")
                .buckets(4, 0, 1)
                .tank("input", 1, 1, Tank.CHEMICAL, "input", "Dirty slurry")
                .progress(2, 2)
                .tank("output", 1, 5, Tank.CHEMICAL, "output", "Clean slurry")
                .build());

        menus.add(powered("chemical_crystallizer")
                .tank("input", 1, 0, Tank.CHEMICAL, "input", "Input")
                .progress(2, 1)
                .slot("output", 2, 4, Kind.OUTPUT, "output", "Output")
                .build());

        menus.add(powered("electrolytic_separator")
                .tank("input", 1, 0, Tank.FLUID, "input", "Input fluid")
                .buckets(4, 0, 1)
                .progress(2, 1)
                .tank("output-1", 1, 4, Tank.CHEMICAL, "output", 1, "Output")
                .tank("output-2", 1, 6, Tank.CHEMICAL, "output", 2, "Output")
                .build());

        menus.add(powered("rotary_condensentrator")
                .tank("chemical", 1, 0, Tank.CHEMICAL, "chemical", "Chemical (left side)")
                .progress(2, 2)
                .tank("fluid", 1, 6, Tank.FLUID, "fluid", "Fluid (right side)")
                .buckets(4, 6, 7)
                .button("mode", "    type: machineengine_data\n    data: mode\n    values:\n      - condensing\n      - decondensing\n"
                        + "    slot: " + MenuLayout.at(4, 3) + "\n    update: true\n" + item("COMPARATOR", "<yellow>Mode: %zmenu_machineengine_data_mode%",
                        "<gray>condensing: chemical to fluid", "<gray>decondensing: fluid to chemical", "<dark_gray>Click to switch"))
                .build());

        menus.add(powered("electric_pump")
                .tank("fluid", 1, 0, Tank.FLUID, "fluid", "Pumped fluid")
                .buckets(4, 0, 1)
                .button("state", display(MenuLayout.at(2, 4), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%",
                        "<gray>Pumps water or lava source blocks below it"))
                .build());

        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "creative")) {
            menus.add(new MenuLayout(tier + "_chemical_tank", 5)
                    .slot("chemical-slot", 2, 2, Kind.EXTRA, "chemical_slot", "Chemical source")
                    .tank("chemical", 1, 4, Tank.CHEMICAL, "chemical", "Stored")
                    .button("state", display(MenuLayout.at(2, 6), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%", "<gray>Output: front"))
                    .build());
            menus.add(new MenuLayout(tier + "_fluid_tank", 5)
                    .slot("bucket-in", 2, 2, Kind.INPUT, "bucket_in", "Bucket (fills or empties)")
                    .tank("fluid", 1, 4, Tank.FLUID, "fluid", "Stored")
                    .slot("bucket-out", 2, 6, Kind.OUTPUT, "bucket_out", "Buckets out")
                    .button("state", display(MenuLayout.at(4, 4), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%", "<gray>Output: bottom"))
                    .build());
        }
    }
}
