package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.Liquifier;
import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Tank;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.ArrayList;
import java.util.List;
import static fr.robie.machineengine.mekanized.paper.menu.MenuLayout.at;
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
        menus.add(new MenuLayout("dimensional_stabilizer", 5)
                .button("info", display(at(0, 4), "END_CRYSTAL", "<light_purple>Dimensional Stabilizer", "<gray>Keeps the chunks around it loaded",
                        "<gray>while it has energy, paid per chunk."))
                .button("radius", setting(at(2, 2), "radius", radii(), "COMPASS",
                        "<yellow>Chunks around: %zmenu_machineengine_data_radius%", "<gray>" + radiusAreas()))
                .button("status", status(at(2, 4)))
                .energy(1, 8)
                .energySlot(4, 8)
                .build());

        menus.add(new MenuLayout("quantum_entangloporter", 5)
                .button("info", display(at(0, 4), "ENDER_EYE", "<light_purple>Quantum Entangloporter",
                        "<gray>Every entangloporter of a frequency shares one buffer.", "<gray>Sending ones fill it from pipes, cables and hoppers;",
                        "<gray>receiving ones empty it out of every side.", "<gray>Frequency: look at it, /mekanized frequency <name>"))
                .button("frequency", display(at(1, 1), "NAME_TAG", "<yellow>Frequency: %zmenu_machineengine_data_frequency%"))
                .button("mode", setting(at(3, 1), "entangloporter_mode", List.of("send", "receive"), "COMPARATOR",
                        "<yellow>Mode: %zmenu_machineengine_data_entangloporter_mode%"))
                .energy(1, 3)
                .slot("items", 2, 4, Kind.NORMAL, "items", "Items")
                .tank("fluid", 1, 5, Tank.FLUID, "fluid", "Fluid")
                .tank("chemical", 1, 6, Tank.CHEMICAL, "chemical", "Chemical")
                .noSides()
                .build());

        menus.add(new MenuLayout("radioactive_waste_barrel", 5)
                .button("info", display(at(0, 4), "BARREL", "<green>Radioactive Waste Barrel", "<gray>Holds radioactive chemicals safely",
                        "<gray>while they slowly decay."))
                .tank("waste", 1, 4, Tank.CHEMICAL, "waste", "Waste")
                .noSides()
                .build());

        menus.add(powered("nutritional_liquifier")
                .button("info", display(at(0, 4), "BREAD", "<gold>Nutritional Liquifier", "<gray>Food into nutritional paste: "
                        + Liquifier.PASTE_PER_FOOD + " mB per hunger point.", "<gray>Right-click it with a canteen to fill it."))
                .slot("input", 2, 1, Kind.INPUT, "input", "Food")
                .progress(2, 2)
                .tank("paste", 1, 5, Tank.FLUID, "paste", "Nutritional paste")
                .build());
    }
}
