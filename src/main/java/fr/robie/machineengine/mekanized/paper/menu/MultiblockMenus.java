package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Tank;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.List;
import static fr.robie.machineengine.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the structures (dynamic tank, induction matrix, thermal evaporation plant), opened from any member: the anchor's storage. */
final class MultiblockMenus {
    private MultiblockMenus() {
    }

    static void add(List<Menu> menus) {
        menus.add(new MenuLayout("dynamic_tank", 5)
                .button("info", display(at(0, 4), "BUCKET", "<aqua>Dynamic Tank", "<gray>Holds one fluid or one chemical,",
                        "<gray>350,000 mB of fluid per block inside"))
                .tank("fluid", 1, 2, Tank.FLUID, "fluid", "Fluid")
                .buckets(4, 2, 3)
                .tank("chemical", 1, 6, Tank.CHEMICAL, "chemical", "Chemical")
                .build());

        menus.add(new MenuLayout("induction_casing", 5)
                .button("info", display(at(0, 4), "BEACON", "<aqua>Induction Matrix", "<gray>Cells store energy, providers set the output rate.",
                        "<gray>Configurator on a port: input or output"))
                .energy(1, 4)
                .build());

        menus.add(new MenuLayout("thermal_evaporation_block", 5)
                .button("info", display(at(0, 4), "SUNFLOWER", "<gold>Thermal Evaporation Plant", "<gray>Water to brine, brine to liquid lithium.",
                        "<gray>Faster when taller, by day, and when dry"))
                .tank("input", 1, 0, Tank.FLUID, "input", "Input")
                .buckets(4, 0, 1)
                .button("state", display(at(2, 4), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"))
                .tank("output", 1, 7, Tank.FLUID, "output", "Output")
                .slot("output-bucket", 4, 7, Kind.OUTPUT, "output_bucket", "Empty bucket to fill from the output")
                .build());
    }
}
