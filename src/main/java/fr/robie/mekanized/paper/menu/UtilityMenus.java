package fr.robie.mekanized.paper.menu;

import fr.robie.mekanized.machine.UtilityMachines;
import fr.robie.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.mekanized.paper.menu.MenuLayout.Tank;
import fr.robie.mekanized.paper.menu.MenuYaml.Menu;
import java.util.List;
import static fr.robie.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.mekanized.paper.menu.MenuYaml.*;

/** The menus of the utility blocks: personal storage, bins, chargepad, plenisher, assemblicator, lasers, oredictionificator. */
final class UtilityMenus {
    private UtilityMenus() {
    }

    static void add(List<Menu> menus) {
        // Personal storage: a double chest's worth, no sides (nothing pipes in or out).
        for (String name : List.of("personal_chest", "personal_barrel")) {
            MenuLayout chest = new MenuLayout(name, 6).noSides();
            for (int i = 0; i < 54; i++) chest.slot("slot-" + (i + 1), i / 9, i % 9, Kind.NORMAL, "items", i + 1, "");
            menus.add(chest.build());
        }
        for (String tier : UtilityMachines.BIN_TIERS) {
            menus.add(new MenuLayout(tier + "_bin", 5)
                    .button("info", display(at(0, 4), "BARREL", "<yellow>Bin", "<gray>One kind of item, many of it.",
                            "<gray>Right-click the block with items to store them,", "<gray>left-click to take a stack (sneak: one)"))
                    .slot("items", 2, 4, Kind.NORMAL, "items", "Stored")
                    .button("amount", display(at(3, 4), "PAPER",
                            "<gray>%zmenu_machineengine_storage_items_amount% / %zmenu_machineengine_storage_items_capacity%"))
                    .build());
        }
        menus.add(new MenuLayout("chargepad", 5)
                .button("info", display(at(0, 4), "LIGHT_WEIGHTED_PRESSURE_PLATE", "<yellow>Chargepad", "<gray>Charges the energy items",
                        "<gray>of players standing on it"))
                .energy(1, 4)
                .build());

        menus.add(powered("fluidic_plenisher")
                .button("info", display(at(0, 4), "WATER_BUCKET", "<aqua>Fluidic Plenisher", "<gray>Places water or lava below it,",
                        "<gray>filling the space down there"))
                .tank("fluid", 1, 2, Tank.FLUID, "fluid", "Fluid")
                .buckets(4, 2, 3)
                .button("state", display(at(2, 5), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"))
                .build());

        // The recipe grid top left, its outputs to the right, energy below, the ingredients in the last two rows.
        MenuLayout assemblicator = new MenuLayout("formulaic_assemblicator", 6);
        for (int i = 0; i < 9; i++) {
            assemblicator.slot("pattern-" + (i + 1), i / 3, i % 3, Kind.EXTRA, "pattern", i + 1, "Recipe (one item each, like a crafting table)");
        }
        for (int i = 0; i < 3; i++) assemblicator.slot("output-" + (i + 1), 1, 4 + i, Kind.OUTPUT, "output", i + 1, "Output");
        for (int i = 0; i < 18; i++) assemblicator.slot("input-" + (i + 1), 4 + i / 9, i % 9, Kind.INPUT, "input", i + 1, "Ingredients");
        menus.add(assemblicator
                .energyHorizontal(3, 0)
                .energySlot(3, 3)
                .button("state", display(at(3, 5), "CRAFTING_TABLE", "<yellow>%zmenu_machineengine_state%"))
                .upgrades()
                .build());

        menus.add(new MenuLayout("laser", 5)
                .button("info", display(at(0, 4), "END_ROD", "<red>Laser", "<gray>Fires out of its front (5 kJ/t): breaks blocks,",
                        "<gray>or feeds a laser amplifier or tractor beam"))
                .energy(1, 4)
                .redstone()
                .build());
        menus.add(new MenuLayout("laser_amplifier", 5)
                .button("info", display(at(0, 4), "END_CRYSTAL", "<red>Laser Amplifier", "<gray>Gathers laser energy and fires it",
                        "<gray>out of its front while not powered by redstone"))
                .energy(1, 4)
                .redstone()
                .build());
        MenuLayout tractor = new MenuLayout("laser_tractor_beam", 5)
                .button("info", display(at(0, 4), "END_CRYSTAL", "<red>Laser Tractor Beam", "<gray>An amplifier that keeps",
                        "<gray>the drops of the blocks it breaks"))
                .energyHorizontal(4, 0)
                .redstone();
        for (int i = 0; i < 27; i++) tractor.slot("items-" + (i + 1), 1 + i / 9, i % 9, Kind.OUTPUT, "items", i + 1, "Collected");
        menus.add(tractor.build());

        menus.add(new MenuLayout("oredictionificator", 5)
                .button("info", display(at(0, 4), "NAME_TAG", "<yellow>Oredictionificator", "<gray>Turns ores, ingots, dusts... of other plugins",
                        "<gray>into Mekanized's (by their c: tags)"))
                .slot("input", 2, 2, Kind.INPUT, "input", "Input")
                .slot("output", 2, 6, Kind.OUTPUT, "output", "Output")
                .build());
    }
}
