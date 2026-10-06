package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the structures (dynamic tank, induction matrix, thermal evaporation plant), opened from any member: the anchor's storage. */
final class MultiblockMenus {
    private MultiblockMenus() {
    }

    static void add(List<Menu> menus) {
        Map<String, String> tank = new LinkedHashMap<>();
        tank.put("info", display(4, "BUCKET", "<aqua>Dynamic Tank", "<gray>Holds one fluid or one chemical,",
                "<gray>350,000 mB of fluid per block inside"));
        tank.put("chemical", chemical(15, "chemical", "Chemical"));
        buckets(tank, "fluid", "Fluid", 11);
        menus.add(new Menu("dynamic_tank", tank));

        Map<String, String> matrix = new LinkedHashMap<>();
        matrix.put("info", display(4, "BEACON", "<aqua>Induction Matrix", "<gray>Cells store energy, providers set the output rate.",
                "<gray>Configurator on a port: input or output"));
        matrix.put("energy", energyDisplay(22));
        menus.add(new Menu("induction_casing", matrix));

        Map<String, String> tower = new LinkedHashMap<>();
        tower.put("info", display(4, "SUNFLOWER", "<gold>Thermal Evaporation Plant", "<gray>Water to brine, brine to liquid lithium.",
                "<gray>Faster when taller, by day, and when dry"));
        buckets(tower, "input", "Input", 10);
        tower.put("output", tank(16, "output", "WATER_BUCKET", "<aqua>", "Output"));
        tower.put("output-bucket", slotOf(25, "output_bucket", 1, "Empty bucket to fill from the output"));
        tower.put("state", display(13, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("thermal_evaporation_block", tower));
    }
}
