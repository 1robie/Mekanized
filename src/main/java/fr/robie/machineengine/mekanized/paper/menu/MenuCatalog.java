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
        try {
            if (files.install(folder.resolve(UpgradeMenu.NAME + ".yml"), yaml(UpgradeMenu.menu()).getBytes(StandardCharsets.UTF_8))) installed++;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not install the upgrades menu: " + e.getMessage());
        }
        if (installed > 0) plugin.getLogger().info("Installed " + installed + " Mekanized machine menus in " + folder);
    }

    static List<Menu> menus() {
        List<Menu> menus = new ArrayList<>();
        for (String machine : List.of("enrichment_chamber", "crusher", "energized_smelter")) menus.add(processing(machine, false, null).build());
        menus.add(processing("precision_sawmill", true, null).build());
        menus.add(processing("combiner", false, null).slot("extra", 3, 1, MenuLayout.Kind.EXTRA, "extra", "Second input").build());
        menus.add(processing("osmium_compressor", false, "Osmium source (osmium ingots)").build());
        menus.add(processing("purification_chamber", false, "Oxygen source").build());
        menus.add(processing("metallurgic_infuser", false, "Infuse source (redstone, coal, diamond dust...)").build());
        menus.add(generator("heat_generator", true).build());
        menus.add(generator("bio_generator", true).build());
        menus.add(generator("solar_generator", false).build());
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "creative")) menus.add(energyCube(tier + "_energy_cube").build());
        ChemistryMenus.add(menus);
        LogisticsMenus.add(menus);
        MultiblockMenus.add(menus);
        ReactionAndPigmentMenus.add(menus);
        UtilityMenus.add(menus);
        ReactorMenus.add(menus);
        AutomationMenus.add(menus);
        LoaderAndBufferMenus.add(menus);
        StationMenu.add(menus);
        return menus;
    }
}
