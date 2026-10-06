package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the reaction chamber, centrifuge, neutron activator, nucleosynthesizer, painting and pigment machines, and the late generators. */
final class ReactionAndPigmentMenus {
    private ReactionAndPigmentMenus() {
    }

    static void add(List<Menu> menus) {
        Map<String, String> reaction = machine();
        reaction.put("input", slot(10, "input", "Item input"));
        reaction.put("chemical", chemical(19, "chemical", "Chemical input"));
        buckets(reaction, "fluid", "Fluid input", 28);
        reaction.put("output", slot(14, "output", "Item output"));
        reaction.put("chemical-output", chemical(23, "chemical_output", "Chemical output"));
        menus.add(new Menu("pressurized_reaction_chamber", reaction));

        Map<String, String> centrifuge = machine();
        centrifuge.put("input", chemical(10, "input", "Input"));
        centrifuge.put("output", chemical(14, "output", "Output"));
        menus.add(new Menu("isotopic_centrifuge", centrifuge));

        Map<String, String> activator = new LinkedHashMap<>();
        activator.put("input", chemical(11, "input", "Input"));
        activator.put("output", chemical(15, "output", "Output"));
        activator.put("state", display(13, "SUNFLOWER", "<yellow>%zmenu_machineengine_state%", "<gray>Works in daylight under open sky"));
        menus.add(new Menu("solar_neutron_activator", activator));

        for (String[] machine : new String[][]{{"antiprotonic_nucleosynthesizer", "Antimatter"}, {"painting_machine", "Pigment"}}) {
            Map<String, String> buttons = machine();
            buttons.put("input", slot(10, "input", "Input"));
            buttons.put("chemical", chemical(19, "chemical", machine[1]));
            buttons.put("output", slot(14, "output", "Output"));
            menus.add(new Menu(machine[0], buttons));
        }

        Map<String, String> extractor = machine();
        extractor.put("input", slot(10, "input", "Input"));
        extractor.put("output", chemical(14, "output", "Pigment"));
        menus.add(new Menu("pigment_extractor", extractor));

        Map<String, String> mixer = machine();
        mixer.put("left", chemical(10, "left", "Left pigment (left side)"));
        mixer.put("right", chemical(11, "right", "Right pigment (right side)"));
        mixer.put("output", chemical(14, "output", "Output (front)"));
        menus.add(new Menu("pigment_mixer", mixer));

        Menu burner = generator("gas_burning_generator", false);
        burner.buttons().put("fuel", chemical(11, "fuel", "Fuel (hydrogen, ethene)"));
        menus.add(burner);
        menus.add(generator("wind_generator", false));
        menus.add(generator("advanced_solar_generator", false));
    }
}
