package fr.robie.mekanized.paper.menu;

import fr.robie.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.mekanized.paper.menu.MenuLayout.Tank;
import fr.robie.mekanized.paper.menu.MenuYaml.Menu;
import java.util.List;
import static fr.robie.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.mekanized.paper.menu.MenuYaml.*;

/** The menus of the reaction chamber, centrifuge, neutron activator, nucleosynthesizer, painting and pigment machines, and the late generators. */
final class ReactionAndPigmentMenus {
    private ReactionAndPigmentMenus() {
    }

    static void add(List<Menu> menus) {
        menus.add(powered("pressurized_reaction_chamber")
                .tank("fluid", 1, 0, Tank.FLUID, "fluid", "Fluid input")
                .buckets(4, 0, 1)
                .tank("chemical", 1, 1, Tank.CHEMICAL, "chemical", "Chemical input")
                .slot("input", 2, 2, Kind.INPUT, "input", "Item input")
                .progress(2, 3)
                .slot("output", 2, 6, Kind.OUTPUT, "output", "Item output")
                .tank("chemical-output", 1, 7, Tank.CHEMICAL, "chemical_output", "Chemical output")
                .build());

        menus.add(powered("isotopic_centrifuge")
                .tank("input", 1, 0, Tank.CHEMICAL, "input", "Input")
                .progress(2, 2)
                .tank("output", 1, 5, Tank.CHEMICAL, "output", "Output")
                .build());

        menus.add(new MenuLayout("solar_neutron_activator", 5)
                .tank("input", 1, 1, Tank.CHEMICAL, "input", "Input")
                .button("state", display(at(2, 4), "SUNFLOWER", "<yellow>%zmenu_machineengine_state%", "<gray>Works in daylight under open sky"))
                .tank("output", 1, 7, Tank.CHEMICAL, "output", "Output")
                .build());

        for (String[] machine : new String[][]{{"antiprotonic_nucleosynthesizer", "Antimatter"}, {"painting_machine", "Pigment"}}) {
            menus.add(powered(machine[0])
                    .tank("chemical", 1, 0, Tank.CHEMICAL, "chemical", machine[1])
                    .slot("input", 2, 1, Kind.INPUT, "input", "Input")
                    .progress(2, 2)
                    .slot("output", 2, 5, Kind.OUTPUT, "output", "Output")
                    .build());
        }

        menus.add(powered("pigment_extractor")
                .slot("input", 2, 1, Kind.INPUT, "input", "Input")
                .progress(2, 2)
                .tank("output", 1, 5, Tank.CHEMICAL, "output", "Pigment")
                .build());

        menus.add(powered("pigment_mixer")
                .tank("left", 1, 0, Tank.CHEMICAL, "left", "Left pigment (left side)")
                .tank("right", 1, 1, Tank.CHEMICAL, "right", "Right pigment (right side)")
                .progress(2, 2)
                .tank("output", 1, 5, Tank.CHEMICAL, "output", "Output (front)")
                .build());

        menus.add(generator("gas_burning_generator", false)
                .tank("fuel", 1, 0, Tank.CHEMICAL, "fuel", "Fuel (hydrogen, ethene)").build());
        menus.add(generator("wind_generator", false).build());
        menus.add(generator("advanced_solar_generator", false).build());
    }
}
