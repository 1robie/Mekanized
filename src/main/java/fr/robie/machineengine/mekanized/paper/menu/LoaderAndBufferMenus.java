package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.Liquifier;
import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the dimensional stabilizer, quantum entangloporter, radioactive waste barrel and nutritional liquifier. */
final class LoaderAndBufferMenus {
    private LoaderAndBufferMenus() {
    }

    /** The radii the stabilizer allows: 0 up to the configured maximum. */
    private static List<String> radii() {
        List<String> radii = new ArrayList<>();
        for (int radius = 0; radius <= ChunkLoadingSettings.maxRadius; radius++) radii.add(Integer.toString(radius));
        return radii;
    }

    /** "0: its chunk, 1: 3 x 3, 2: 5 x 5": the area each radius loads. */
    private static String radiusAreas() {
        StringBuilder text = new StringBuilder();
        for (int radius = 0; radius <= ChunkLoadingSettings.maxRadius; radius++) {
            if (radius > 0) text.append(", ");
            int side = 2 * radius + 1;
            text.append(radius).append(": ").append(radius == 0 ? "its chunk" : side + " x " + side);
        }
        return text.toString();
    }

    static void add(List<Menu> menus) {
        Map<String, String> stabilizer = new LinkedHashMap<>();
        stabilizer.put("info", display(4, "END_CRYSTAL", "<light_purple>Dimensional Stabilizer", "<gray>Keeps the chunks around it loaded",
                "<gray>while it has energy (" + ChunkLoadingSettings.energyPerChunk
                        + " J/t per chunk)."));
        stabilizer.put("radius", setting(11, "radius", radii(), "COMPASS",
                "<yellow>Chunks around: %zmenu_machineengine_data_radius%", "<gray>" + radiusAreas()));
        stabilizer.put("energy", energyDisplay(13));
        stabilizer.put("status", status(15));
        stabilizer.put("energy-slot", slot(31, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        menus.add(new Menu("dimensional_stabilizer", stabilizer));

        Map<String, String> entangloporter = new LinkedHashMap<>();
        entangloporter.put("info", display(4, "ENDER_EYE", "<light_purple>Quantum Entangloporter",
                "<gray>Every entangloporter of a frequency shares one buffer.", "<gray>Sending ones fill it from pipes, cables and hoppers;",
                "<gray>receiving ones empty it out of every side.", "<gray>Frequency: look at it, /mekanized frequency <name>"));
        entangloporter.put("frequency", display(10, "NAME_TAG", "<yellow>Frequency: %zmenu_machineengine_data_frequency%"));
        entangloporter.put("mode", setting(12, "entangloporter_mode", List.of("send", "receive"), "COMPARATOR",
                "<yellow>Mode: %zmenu_machineengine_data_entangloporter_mode%"));
        entangloporter.put("energy", energyDisplay(14));
        entangloporter.put("fluid", tank(15, "fluid", "WATER_BUCKET", "<aqua>", "Fluid"));
        entangloporter.put("chemical", chemical(16, "chemical", "Chemical"));
        entangloporter.put("items", slot(22, "items", "Items"));
        menus.add(new Menu("quantum_entangloporter", entangloporter, -1));

        Map<String, String> barrel = new LinkedHashMap<>();
        barrel.put("info", display(4, "BARREL", "<green>Radioactive Waste Barrel", "<gray>Holds radioactive chemicals safely",
                "<gray>while they slowly decay (" + Radiation.BARREL_DECAY + " mB a second)."));
        barrel.put("waste", chemical(13, "waste", "Waste"));
        menus.add(new Menu("radioactive_waste_barrel", barrel, -1));

        Map<String, String> liquifier = new LinkedHashMap<>();
        liquifier.put("info", display(4, "BREAD", "<gold>Nutritional Liquifier", "<gray>Food into nutritional paste: "
                + Liquifier.PASTE_PER_FOOD + " mB per hunger point.",
                "<gray>Right-click it with a canteen to fill it."));
        liquifier.put("input", slot(10, "input", "Food"));
        liquifier.put("paste", tank(14, "paste", "HONEY_BOTTLE", "<gold>", "Nutritional paste"));
        liquifier.put("energy", energyDisplay(16));
        liquifier.put("energy-slot", slot(34, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        liquifier.put("upgrades", upgrades());
        menus.add(new Menu("nutritional_liquifier", liquifier));
    }
}
