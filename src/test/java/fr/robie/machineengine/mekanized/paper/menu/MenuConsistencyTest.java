package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.mekanized.MekanizedTestKit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every menu names only what its machine has: the storages (and their slots) of its slot buttons and
 * gauges, the data of its setting buttons and placeholders, and button slots that fit the inventory
 * without overlapping. Buttons are zMenu YAML text, so they are read back with patterns.
 */
class MenuConsistencyTest {
    private static final Pattern BUTTON_SLOT = Pattern.compile("(?m)^    slot: (\\d+)$");
    private static final Pattern SLOT_STORAGE = Pattern.compile("(?m)^    storage: (\\w+)$");
    private static final Pattern STORAGE_SLOT = Pattern.compile("(?m)^    storage-slot: (\\d+)$");
    private static final Pattern DATA = Pattern.compile("(?m)^    data: (\\w+)$");
    private static final Pattern PLACEHOLDER_STORAGE =
            Pattern.compile("%zmenu_machineengine_storage_(\\w+?)_(?:slot_(\\d+)_)?(?:name|amount|capacity|bar)%");
    private static final Pattern PLACEHOLDER_DATA = Pattern.compile("%zmenu_machineengine_data_(\\w+)%");

    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    private MachineType type(MenuYaml.Menu menu) {
        return MekanizedTestKit.type(this.engine, menu.machine());
    }

    @Test
    void thereAreMenusAndEachNamesARegisteredMachineOnce() {
        List<MenuYaml.Menu> menus = MenuCatalog.menus();
        assertTrue(menus.size() > 50, "menus: " + menus.size());
        Set<String> seen = new HashSet<>();
        for (MenuYaml.Menu menu : menus) {
            assertTrue(seen.add(menu.machine()), "two menus for " + menu.machine());
            type(menu); // throws when the machine type does not exist
        }
    }

    @Test
    void everyStorageAMenuNamesBelongsToItsMachine() {
        List<String> problems = new ArrayList<>();
        for (MenuYaml.Menu menu : MenuCatalog.menus()) {
            MachineType type = type(menu);
            menu.buttons().forEach((name, button) -> {
                Matcher storage = SLOT_STORAGE.matcher(button);
                if (storage.find()) {
                    var definition = type.storage(storage.group(1));
                    if (definition.isEmpty()) {
                        problems.add(menu.machine() + "." + name + ": no storage " + storage.group(1));
                    } else {
                        Matcher index = STORAGE_SLOT.matcher(button);
                        if (index.find() && Integer.parseInt(index.group(1)) > definition.get().slots()) {
                            problems.add(menu.machine() + "." + name + ": " + storage.group(1) + " has " + definition.get().slots()
                                    + " slots, the button wants " + index.group(1));
                        }
                    }
                }
                Matcher placeholder = PLACEHOLDER_STORAGE.matcher(button);
                while (placeholder.find()) {
                    var definition = type.storage(placeholder.group(1));
                    if (definition.isEmpty()) {
                        problems.add(menu.machine() + "." + name + ": placeholder for storage " + placeholder.group(1));
                    } else if (placeholder.group(2) != null && Integer.parseInt(placeholder.group(2)) > definition.get().slots()) {
                        problems.add(menu.machine() + "." + name + ": placeholder for slot " + placeholder.group(2) + " of "
                                + placeholder.group(1) + " (" + definition.get().slots() + " slots)");
                    }
                }
            });
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyDataAMenuNamesBelongsToItsMachine() {
        List<String> problems = new ArrayList<>();
        for (MenuYaml.Menu menu : MenuCatalog.menus()) {
            MachineType type = type(menu);
            Set<String> data = new HashSet<>();
            type.data().forEach(key -> data.add(key.id()));
            menu.buttons().forEach((name, button) -> {
                Matcher setting = DATA.matcher(button);
                while (setting.find()) {
                    if (!data.contains(setting.group(1))) problems.add(menu.machine() + "." + name + ": no data " + setting.group(1));
                }
                Matcher placeholder = PLACEHOLDER_DATA.matcher(button);
                while (placeholder.find()) {
                    if (!data.contains(placeholder.group(1))) problems.add(menu.machine() + "." + name + ": placeholder for data " + placeholder.group(1));
                }
            });
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void buttonsFitTheInventoryAndDoNotOverlap() {
        List<String> problems = new ArrayList<>();
        for (MenuYaml.Menu menu : MenuCatalog.menus()) {
            Set<Integer> used = new HashSet<>();
            menu.buttons().forEach((name, button) -> {
                Matcher slot = BUTTON_SLOT.matcher(button);
                if (!slot.find()) {
                    problems.add(menu.machine() + "." + name + ": no slot");
                    return;
                }
                int at = Integer.parseInt(slot.group(1));
                if (at < 0 || at >= menu.size()) problems.add(menu.machine() + "." + name + ": slot " + at + " outside " + menu.size());
                else if (!used.add(at)) problems.add(menu.machine() + "." + name + ": slot " + at + " is taken");
            });
            if (menu.sideSlot() >= 0 && (menu.sideSlot() >= menu.size() || !used.add(menu.sideSlot()))) {
                problems.add(menu.machine() + ": side configuration slot " + menu.sideSlot() + " is outside or taken");
            }
        }
        assertEquals(List.of(), problems);
    }
}
