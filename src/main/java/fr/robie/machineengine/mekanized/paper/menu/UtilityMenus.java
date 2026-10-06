package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the utility blocks: personal storage, bins, chargepad, plenisher, assemblicator, lasers, oredictionificator. */
final class UtilityMenus {
    private UtilityMenus() {
    }

    static void add(List<Menu> menus) {
        // Personal storage: a double chest's worth, no sides (nothing pipes in or out).
        for (String name : List.of("personal_chest", "personal_barrel")) {
            Map<String, String> buttons = new LinkedHashMap<>();
            for (int i = 0; i < 54; i++) buttons.put("slot-" + (i + 1), slotOf(i, "items", i + 1, ""));
            menus.add(new Menu(name, buttons, -1, 54));
        }
        for (String tier : UtilityMachines.BIN_TIERS) {
            Map<String, String> bin = new LinkedHashMap<>();
            bin.put("info", display(4, "BARREL", "<yellow>Bin", "<gray>One kind of item, many of it.",
                    "<gray>Right-click the block with items to store them,", "<gray>left-click to take a stack (sneak: one)"));
            bin.put("items", slot(22, "items", "Stored"));
            bin.put("amount", display(31, "PAPER", "<gray>%zmenu_machineengine_storage_items_amount% / %zmenu_machineengine_storage_items_capacity%"));
            menus.add(new Menu(tier + "_bin", bin));
        }
        Map<String, String> chargepad = new LinkedHashMap<>();
        chargepad.put("info", display(4, "LIGHT_WEIGHTED_PRESSURE_PLATE", "<yellow>Chargepad", "<gray>Charges the energy items",
                "<gray>of players standing on it"));
        chargepad.put("energy", energyDisplay(22));
        menus.add(new Menu("chargepad", chargepad));

        Map<String, String> plenisher = new LinkedHashMap<>();
        plenisher.put("info", display(4, "WATER_BUCKET", "<aqua>Fluidic Plenisher", "<gray>Places water or lava below it,",
                "<gray>filling the space down there"));
        plenisher.putAll(machine());
        plenisher.remove("progress");
        buckets(plenisher, "fluid", "Fluid", 11);
        plenisher.put("state", display(13, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("fluidic_plenisher", plenisher));

        Map<String, String> assemblicator = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            assemblicator.put("pattern-" + (i + 1), slotOf(i / 3 * 9 + i % 3, "pattern", i + 1, "Recipe (one item each, like a crafting table)"));
        }
        for (int i = 0; i < 18; i++) assemblicator.put("input-" + (i + 1), slotOf(27 + i % 9 + (i / 9) * 9, "input", i + 1, "Ingredients"));
        for (int i = 0; i < 3; i++) assemblicator.put("output-" + (i + 1), slotOf(14 + i, "output", i + 1, "Output"));
        assemblicator.put("energy", energyDisplay(5));
        assemblicator.put("energy-slot", slot(6, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        assemblicator.put("upgrades", upgrades());
        assemblicator.put("state", display(23, "CRAFTING_TABLE", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("formulaic_assemblicator", assemblicator, 26, 54));

        Map<String, String> laser = new LinkedHashMap<>();
        laser.put("info", display(4, "END_ROD", "<red>Laser", "<gray>Fires out of its front (5 kJ/t): breaks blocks,",
                "<gray>or feeds a laser amplifier or tractor beam"));
        laser.put("energy", energyDisplay(22));
        laser.put("redstone", redstoneControl(40));
        menus.add(new Menu("laser", laser));
        Map<String, String> amplifier = new LinkedHashMap<>();
        amplifier.put("info", display(4, "END_CRYSTAL", "<red>Laser Amplifier", "<gray>Gathers laser energy and fires it",
                "<gray>out of its front while not powered by redstone"));
        amplifier.put("energy", energyDisplay(22));
        amplifier.put("redstone", redstoneControl(40));
        menus.add(new Menu("laser_amplifier", amplifier));
        Map<String, String> tractor = new LinkedHashMap<>();
        tractor.put("info", display(4, "END_CRYSTAL", "<red>Laser Tractor Beam", "<gray>An amplifier that keeps",
                "<gray>the drops of the blocks it breaks"));
        tractor.put("energy", energyDisplay(0));
        tractor.put("redstone", redstoneControl(40));
        for (int i = 0; i < 27; i++) tractor.put("items-" + (i + 1), slotOf(9 + i, "items", i + 1, "Collected"));
        menus.add(new Menu("laser_tractor_beam", tractor));

        Map<String, String> oredict = new LinkedHashMap<>();
        oredict.put("info", display(4, "NAME_TAG", "<yellow>Oredictionificator", "<gray>Turns ores, ingots, dusts... of other plugins",
                "<gray>into Mekanized's (by their c: tags)"));
        oredict.put("input", slot(20, "input", "Input"));
        oredict.put("output", slot(24, "output", "Output"));
        menus.add(new Menu("oredictionificator", oredict));
    }
}
