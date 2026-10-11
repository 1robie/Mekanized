package fr.robie.mekanized.paper.menu;

import fr.robie.mekanized.machine.FactoryBehavior;
import fr.robie.mekanized.machine.LogisticsMachines;
import fr.robie.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.mekanized.paper.menu.MenuYaml.Menu;
import java.util.List;
import static fr.robie.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.mekanized.paper.menu.MenuYaml.*;

/** The menus of the logistical sorter and the factories. */
final class LogisticsMenus {
    private LogisticsMenus() {
    }

    static void add(List<Menu> menus) {
        MenuLayout sorter = new MenuLayout("logistical_sorter", 5)
                .button("info", display(at(0, 4), "HOPPER", "<yellow>Logistical sorter", "<gray>Pulls from the block behind it,",
                        "<gray>pushes out of its front.", "<gray>Only items like the filters pass (none: all)."))
                .slot("buffer", 3, 4, Kind.NORMAL, "buffer", "In transit");
        for (int i = 1; i <= 9; i++) sorter.slot("filter-" + i, 1, i - 1, Kind.EXTRA, "filter", i, "Filter " + i);
        menus.add(sorter.build());

        for (String tier : List.of("basic", "advanced", "elite", "ultimate")) {
            int lanes = LogisticsMachines.LANES_BY_TIER.get(tier);
            for (var kind : LogisticsMachines.KINDS) {
                menus.add(factory(tier, lanes, kind));
            }
        }
    }

    /**
     * Mekanism's factory window, 6 rows: energy (and the shared second input) on the top row, the chemical
     * below it, then one column per lane: input, its down arrow, output (and the sawmill's second output).
     */
    private static Menu factory(String tier, int lanes, LogisticsMachines.Kind kind) {
        MenuLayout layout = new MenuLayout(tier + "_" + kind.name() + "_factory", 6)
                .energyHorizontal(0, 0)
                .energySlot(0, 3)
                .upgrades();
        if (kind.extraSlot()) layout.slot("extra", 0, 4, Kind.EXTRA, "extra", "Second input (every lane)");
        if (kind.chemicalTank() > 0) {
            layout.horizontalTank("chemical", 1, 0, "chemical", kind.chemicalIsInput() ? "Infuse type" : "Chemical")
                    .slot("chemical-slot", 1, 3, Kind.EXTRA, "chemical_slot", kind.chemicalIsInput() ? "Infuse source" : "Chemical source");
        }
        int first = (9 - lanes) / 2;
        for (int lane = 0; lane < lanes; lane++) {
            int column = first + lane;
            layout.slot("input-" + (lane + 1), 2, column, Kind.INPUT, FactoryBehavior.laneStorage(lane), "Lane " + (lane + 1))
                    .lane("progress-" + (lane + 1), 3, column, "lane_" + (lane + 1), "Lane " + (lane + 1))
                    .slot("output-" + (lane + 1), 4, column, Kind.OUTPUT, "output", lane + 1, "Output");
            if (kind.outputsPerLane() > 1) {
                layout.slot("output-" + (lanes + lane + 1), 5, column, Kind.OUTPUT_2, "output", lanes + lane + 1, "Secondary output");
            }
        }
        return layout.build();
    }
}
