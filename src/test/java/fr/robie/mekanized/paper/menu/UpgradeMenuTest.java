package fr.robie.mekanized.paper.menu;

import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.upgrade.UpgradeActions;
import fr.robie.machineengine.api.upgrade.UpgradeType;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.mekanized.MekanizedTestKit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The upgrades menu (D40) and the upgrade tabs that open it. */
class UpgradeMenuTest {
    private static final Pattern SLOT = Pattern.compile("(?m)^    slot: (\\d+)$");

    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void everyUpgradeTabIsOnAMachineWithUpgradeSlotsAndOpensTheMenu() {
        List<String> problems = new ArrayList<>();
        for (MenuYaml.Menu menu : MenuCatalog.menus()) {
            MachineType type = MekanizedTestKit.type(this.engine, menu.machine());
            String tab = menu.buttons().get("upgrades");
            boolean hasAction = type.action(UpgradeActions.ACTION).isPresent();
            if (tab != null && !hasAction) problems.add(menu.machine() + ": an upgrade tab, but no upgrade slots");
            if (tab != null && !tab.contains("menu: " + UpgradeMenu.NAME)) problems.add(menu.machine() + ": the tab opens another menu");
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void theMenuHasARowForEveryUpgradeAMachineTakes() {
        List<UpgradeType> upgrades = this.engine.registries().registry(Registries.UPGRADE_TYPE).values();
        int most = 0;
        for (MenuYaml.Menu menu : MenuCatalog.menus()) {
            MachineType type = MekanizedTestKit.type(this.engine, menu.machine());
            most = Math.max(most, (int) upgrades.stream().filter(type::acceptsUpgrade).count());
        }
        assertTrue(most >= 3 && most <= UpgradeMenu.ROWS, "a machine takes " + most + " upgrades, the menu shows " + UpgradeMenu.ROWS);
        assertEquals(upgrades.size(), UpgradeMenu.ROWS, "one row per registered upgrade");
    }

    @Test
    void rowsRunTheUpgradeActionAndButtonsDoNotOverlap() {
        MenuYaml.Menu menu = UpgradeMenu.menu();
        Set<Integer> used = new HashSet<>();
        menu.buttons().forEach((name, button) -> {
            Matcher slot = SLOT.matcher(button);
            assertTrue(slot.find(), name + ": no slot");
            int at = Integer.parseInt(slot.group(1));
            assertTrue(at >= 0 && at < menu.size(), name + ": slot " + at);
            assertTrue(used.add(at), name + ": slot " + at + " is taken");
        });
        for (int i = 0; i < UpgradeMenu.ROWS; i++) {
            String row = menu.buttons().get("upgrade-" + i);
            assertTrue(row.contains("action: upgrade") && row.contains("list: upgrades") && row.contains("index: " + i), row);
            assertTrue(row.contains("%zmenu_machineengine_list_upgrades_" + i + "_item%"), "the row shows its upgrade's item");
            assertTrue(row.contains("    empty:\n      material: \"craftengine:mekanized:gui/ghost_upgrade\""), "an empty row shows a ghost upgrade");
        }
        assertTrue(menu.buttons().get("input").contains("input: true"));
    }

    @Test
    void stationRowsShowAGhostModuleWhenEmpty() {
        MenuYaml.Menu menu = StationMenu.menu();
        for (int i = 0; i < StationMenu.ROWS; i++) {
            String row = menu.buttons().get("module-" + i);
            assertTrue(row.contains("list: modules") && row.contains("index: " + i), row);
            assertTrue(row.contains("craftengine:mekanized:gui/ghost_module"), "an empty row shows a ghost module");
        }
    }
}
