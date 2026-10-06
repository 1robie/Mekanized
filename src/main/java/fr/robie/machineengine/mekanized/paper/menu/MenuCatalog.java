package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.EditableFiles;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.Plugin;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/**
 * zMenu menus of the Mekanized machines, written to {@code plugins/MachineEngine/menus/} as
 * {@code mekanized_<machine>.yml} (MachineEngine picks the menu by that name). Server owners edit them
 * like any zMenu inventory; edited menus are never overwritten. Titles are translated on each
 * player's client.
 */
public final class MenuCatalog {
    private MenuCatalog() {
    }

    public static void install(Plugin plugin, EditableFiles files) {
        Path folder = plugin.getDataFolder().toPath().getParent().resolve("MachineEngine/menus");
        int installed = 0;
        for (Menu menu : menus()) {
            Path file = folder.resolve("mekanized_" + menu.machine() + ".yml");
            try {
                if (files.install(file, yaml(menu).getBytes(StandardCharsets.UTF_8))) installed++;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not install the menu " + file.getFileName() + ": " + e.getMessage());
            }
        }
        installed += SideMenus.install(plugin, files, folder);
        if (installed > 0) plugin.getLogger().info("Installed " + installed + " Mekanized machine menus in " + folder);
    }

    static List<Menu> menus() {
        List<Menu> menus = new ArrayList<>();
        for (String machine : List.of("enrichment_chamber", "crusher", "energized_smelter")) menus.add(processing(machine, false, null));
        menus.add(processing("precision_sawmill", true, null));
        Menu combiner = processing("combiner", false, null);
        combiner.buttons().put("extra", slot(28, "extra", "Second input"));
        menus.add(combiner);
        menus.add(processing("osmium_compressor", false, "Osmium source (osmium ingots)"));
        menus.add(processing("purification_chamber", false, "Oxygen source"));
        menus.add(processing("metallurgic_infuser", false, "Infuse source (redstone, coal, diamond dust...)"));
        menus.add(generator("heat_generator", true));
        menus.add(generator("bio_generator", true));
        menus.add(generator("solar_generator", false));
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "creative")) {
            Menu cube = generator(tier + "_energy_cube", false);
            cube.buttons().put("charge", slot(31, "charge", "Charge an Energy Tablet"));
            menus.add(cube);
        }
        ChemistryMenus.add(menus);
        LogisticsMenus.add(menus);
        MultiblockMenus.add(menus);
        ReactionAndPigmentMenus.add(menus);
        UtilityMenus.add(menus);
        ReactorMenus.add(menus);
        AutomationMenus.add(menus);
        LoaderAndBufferMenus.add(menus);
        return menus;
    }
}
