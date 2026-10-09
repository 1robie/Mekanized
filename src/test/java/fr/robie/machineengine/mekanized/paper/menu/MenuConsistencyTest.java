package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.mekanized.MekanizedTestKit;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

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
            Pattern.compile("%zmenu_machineengine_storage_(\\w+?)_(?:slot_(\\d+)_)?(?:name|amount|capacity|bar|color|cell_\\d)%");
    private static final Pattern PLACEHOLDER_DATA = Pattern.compile("%zmenu_machineengine_data_(\\w+?)(?:_cell)?%");

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

    /** The images and bar items Mekanized's gui.yml declares, read from its row and bar factories. */
    private static String gui() throws IOException {
        try (InputStream in = Objects.requireNonNull(MenuConsistencyTest.class.getClassLoader()
                .getResourceAsStream("mekanized/pack/configuration/gui.yml"), "gui.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void texturedTitlesNameExistingImagesAndBars() throws IOException {
        String gui = gui();
        Set<String> images = new HashSet<>();
        Matcher panel = Pattern.compile("(?m)^  (mekanized:gui/panel_\\d):$").matcher(gui);
        while (panel.find()) images.add(panel.group(1));
        Matcher row = Pattern.compile("(?m)^      (mekanized:gui/\\w+)_r\\$\\{row}:").matcher(gui);
        while (row.find()) {
            for (int r = 0; r < 6; r++) images.add(row.group(1) + "_r" + r);
        }
        Set<String> shapes = new HashSet<>();
        Matcher shape = Pattern.compile("\\{shape: (\\w+), cell: 0}").matcher(gui);
        while (shape.find()) shapes.add(shape.group(1));
        assertTrue(images.size() > 50 && shapes.contains("energy"), "read gui.yml: " + images.size() + " images, shapes " + shapes);

        Pattern image = Pattern.compile("<image:([^>]+)>");
        Pattern shift = Pattern.compile("<shift:(-?\\d+)>");
        Pattern tall = Pattern.compile("frame_(?:tall_gauge|power_vertical)_r(\\d)");
        Pattern bar = Pattern.compile("gui/bar/(\\w+)/(\\d)/");
        List<String> problems = new ArrayList<>();
        int textured = 0;
        List<MenuYaml.Menu> all = new ArrayList<>(MenuCatalog.menus());
        for (int i = 0; i < SideMenus.GROUPS.length; i++) all.add(SideMenus.sideMenu(i));
        all.add(UpgradeMenu.menu());
        for (MenuYaml.Menu menu : all) {
            textured++;
            String title = menu.title();
            Matcher images1 = image.matcher(title);
            while (images1.find()) {
                if (!images.contains(images1.group(1))) problems.add(menu.machine() + ": no image " + images1.group(1));
            }
            if (!title.contains("gui/panel_" + menu.size() / 9 + ">")) problems.add(menu.machine() + ": panel for another size");
            Matcher shifts = shift.matcher(title);
            while (shifts.find()) {
                int by = Integer.parseInt(shifts.group(1));
                if (by < -256 || by > 256) problems.add(menu.machine() + ": shift " + by + " (CraftEngine has -256 to 256)");
            }
            Matcher frames = tall.matcher(title);
            while (frames.find()) {
                if (Integer.parseInt(frames.group(1)) + 3 > menu.size() / 9) problems.add(menu.machine() + ": a tall bar below the window");
            }
            menu.buttons().forEach((name, button) -> {
                Matcher cell = bar.matcher(button);
                while (cell.find()) {
                    if (!shapes.contains(cell.group(1))) problems.add(menu.machine() + "." + name + ": no bar shape " + cell.group(1));
                    if (Integer.parseInt(cell.group(2)) > 2) problems.add(menu.machine() + "." + name + ": bar cell " + cell.group(2));
                }
                if (button.contains("GRAY_STAINED_GLASS_PANE")) problems.add(menu.machine() + "." + name + ": a pane on a textured menu");
            });
        }
        assertTrue(textured > 0, "no textured menu");
        assertEquals(List.of(), problems);
    }

    /**
     * The client moves the cursor past a font image up to its last visible column, so an image with an
     * empty right edge would pull every later image of the title left (compose_menu_textures.py gives
     * such images an invisible pixel there).
     */
    @Test
    void titleImagesMoveTheCursorByTheirWholeWidth() throws IOException {
        Matcher file = Pattern.compile("file: ([\\w/]+)[,}]").matcher(gui());
        List<String> problems = new ArrayList<>();
        int checked = 0;
        while (file.find()) {
            String path = "mekanized/pack/resourcepack/assets/mekanized/textures/gui/" + file.group(1) + ".png";
            try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path), path)) {
                BufferedImage image = ImageIO.read(in);
                int last = image.getWidth() - 1;
                boolean visible = false;
                for (int y = 0; y < image.getHeight() && !visible; y++) visible = (image.getRGB(last, y) >>> 24) != 0;
                if (!visible) problems.add(file.group(1) + ": its last column is empty");
                checked++;
            }
        }
        assertTrue(checked > 10, "images checked: " + checked);
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
