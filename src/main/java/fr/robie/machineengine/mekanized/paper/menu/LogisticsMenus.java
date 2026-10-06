package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.machine.FactoryBehavior;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the logistical sorter and the factories. */
final class LogisticsMenus {
    private LogisticsMenus() {
    }

    static void add(List<Menu> menus) {
        Map<String, String> sorter = new LinkedHashMap<>();
        sorter.put("info", display(4, "HOPPER", "<yellow>Logistical sorter", "<gray>Pulls from the block behind it,",
                "<gray>pushes out of its front.", "<gray>Only items like the filters pass (none: all)."));
        for (int i = 1; i <= 9; i++) sorter.put("filter-" + i, slotOf(8 + i, "filter", i, "Filter " + i));
        sorter.put("buffer", slot(22, "buffer", "In transit"));
        menus.add(new Menu("logistical_sorter", sorter));

        for (String tier : List.of("basic", "advanced", "elite", "ultimate")) {
            int lanes = LogisticsMachines.LANES_BY_TIER.get(tier);
            for (var kind : LogisticsMachines.KINDS) {
                menus.add(factory(tier, lanes, kind));
            }
        }
    }

    /** Lanes in columns: input, progress, output (and the sawmill's second outputs); energy and extras on top. */
    private static Menu factory(String tier, int lanes, LogisticsMachines.Kind kind) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("energy-slot", slot(0, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        buttons.put("energy", energyDisplay(1));
        if (kind.chemicalTank() > 0) {
            buttons.put("chemical-slot", slot(3, "chemical_slot", kind.chemicalIsInput() ? "Infuse source" : "Chemical source"));
            buttons.put("chemical", chemical(4, "chemical", kind.chemicalIsInput() ? "Infuse type" : "Chemical"));
        }
        if (kind.extraSlot()) buttons.put("extra", slot(6, "extra", "Second input (every lane)"));
        buttons.put("upgrades", upgrades());
        int first = (9 - lanes) / 2;
        for (int lane = 0; lane < lanes; lane++) {
            int column = first + lane;
            buttons.put("input-" + (lane + 1), slot(9 + column, FactoryBehavior.laneStorage(lane),
                    "Lane " + (lane + 1)));
            buttons.put("progress-" + (lane + 1), display(18 + column, "BLAZE_POWDER", "<yellow>Lane " + (lane + 1),
                    "<gray>%zmenu_machineengine_data_lane_" + (lane + 1) + "% %"));
            buttons.put("output-" + (lane + 1), slotOf(27 + column, "output", lane + 1, "Output"));
            if (kind.outputsPerLane() > 1) {
                buttons.put("output-" + (lanes + lane + 1), slotOf(36 + column, "output", lanes + lane + 1, "Secondary output"));
            }
        }
        return new Menu(tier + "_" + kind.name() + "_factory", buttons, kind.outputsPerLane() > 1 ? 7 : 44);
    }
}
