package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.machine.Radiation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The player side of {@link Radiation}: each second a player takes in the radiation where they stand
 * (less what hazmat pieces and MekaSuit radiation shielding units stop, a quarter each), their dose
 * fades slowly, and a high dose hurts. The Geiger counter reads the radiation, the dosimeter the dose;
 * a MekaSuit helmet with the matching unit shows them all the time. Sources are saved in
 * {@code plugins/Mekanized/radiation.txt}.
 */
final class PaperRadiation implements Listener {
    /** The player's dose in Sv. */
    static final NamespacedKey DOSE = new NamespacedKey("mekanized", "radiation_dose");
    /** The dose keeps this much every second (half-life about 11 minutes). */
    private static final double DOSE_DECAY = 0.999;
    private static final List<String> HAZMAT = List.of("hazmat_mask", "hazmat_gown", "hazmat_pants", "hazmat_boots");
    private static final List<String> MEKASUIT = List.of("mekasuit_helmet", "mekasuit_bodyarmor", "mekasuit_pants", "mekasuit_boots");
    private static final long SAVE_SECONDS = 30;

    private final Plugin plugin;
    private final Path file;

    private PaperRadiation(Plugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("radiation.txt");
    }

    static void install(Plugin plugin) {
        PaperRadiation radiation = new PaperRadiation(plugin);
        instance = radiation;
        radiation.load();
        Bukkit.getPluginManager().registerEvents(radiation, plugin);
        long[] seconds = {0};
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            Radiation.decay();
            if (++seconds[0] % SAVE_SECONDS == 0) radiation.save();
        }, 20, 20);
        for (Player player : Bukkit.getOnlinePlayers()) radiation.start(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    private void start(Player player) {
        player.getScheduler().runAtFixedRate(this.plugin, task -> {
            if (player.isOnline() && !player.isDead()) expose(player);
        }, null, 20, 20);
    }

    /** One second of radiation for {@code player}. */
    private void expose(Player player) {
        Location at = player.getLocation();
        double level = level(at);
        double dose = dose(player) * DOSE_DECAY + level * (1 - shielding(player)) / 3600;
        player.getPersistentDataContainer().set(DOSE, PersistentDataType.DOUBLE, dose);
        ItemStack helmet = player.getInventory().getHelmet();
        if ("mekasuit_helmet".equals(PaperGear.id(helmet))) {
            var modules = PaperGear.modules(helmet);
            String shown = (modules.contains("geiger_unit") ? "Radiation " + Radiation.format(level, "/h") : "")
                    + (modules.contains("geiger_unit") && modules.contains("dosimeter_unit") ? " - " : "")
                    + (modules.contains("dosimeter_unit") ? "Dose " + Radiation.format(dose, "") : "");
            if (!shown.isEmpty() && level > Radiation.BACKGROUND * 10) player.sendActionBar(Component.text(shown, NamedTextColor.GREEN));
        }
        // Mekanism's thresholds: a high dose makes you sick, a very high one hurts, a fatal one kills.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (dose >= 1 && random.nextInt(10) == 0) player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
        if (dose >= 10) {
            if (random.nextInt(10) == 0) player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, false, false));
            if (random.nextInt(5) == 0) player.damage(1);
        }
        if (dose >= 100) player.damage(4);
    }

    static double level(Location at) {
        return Radiation.levelAt(Key.key(at.getWorld().getKey().asString()), at.getX(), at.getY() + 1, at.getZ());
    }

    static double dose(Player player) {
        Double dose = player.getPersistentDataContainer().get(DOSE, PersistentDataType.DOUBLE);
        return dose == null ? 0 : dose;
    }

    /** Hazmat pieces and MekaSuit pieces with a radiation shielding unit stop a quarter each. */
    private static double shielding(Player player) {
        ItemStack[] armour = {player.getInventory().getHelmet(), player.getInventory().getChestplate(),
                player.getInventory().getLeggings(), player.getInventory().getBoots()};
        double shielded = 0;
        for (int i = 0; i < 4; i++) {
            String id = PaperGear.id(armour[i]);
            if (HAZMAT.get(i).equals(id) || MEKASUIT.get(i).equals(id) && PaperGear.modules(armour[i]).contains("radiation_shielding_unit")) {
                shielded += 0.25;
            }
        }
        return shielded;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().getPersistentDataContainer().remove(DOSE);
    }

    /** Right-click with a Geiger counter (the radiation here) or a dosimeter (your dose). */
    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        String id = PaperGear.id(event.getItem());
        if ("geiger_counter".equals(id)) {
            event.setCancelled(true);
            double level = level(player.getLocation());
            player.sendMessage(Component.text("Radiation here: ", NamedTextColor.GRAY)
                    .append(Component.text(Radiation.format(level, "/h"), color(level * 3600))));
        } else if ("dosimeter".equals(id)) {
            event.setCancelled(true);
            double dose = dose(player);
            player.sendMessage(Component.text("Your radiation dose: ", NamedTextColor.GRAY)
                    .append(Component.text(Radiation.format(dose, ""), color(dose))));
        }
    }

    /** Green below 0.1 Sv, yellow below 1, red above (for an hour's worth of a level, or a dose). */
    private static NamedTextColor color(double sieverts) {
        return sieverts < 0.1 ? NamedTextColor.GREEN : sieverts < 1 ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    // -- saving ------------------------------------------------------------------------------------

    private void load() {
        if (!Files.isRegularFile(this.file)) return;
        try {
            for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|");
                if (parts.length != 5) continue;
                Radiation.sources().put(WorldPosition.of(Key.key(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])), Double.parseDouble(parts[4]));
            }
        } catch (IOException | RuntimeException e) {
            this.plugin.getLogger().warning("Could not read " + this.file + ": " + e.getMessage());
        }
    }

    synchronized void save() {
        StringBuilder text = new StringBuilder();
        Radiation.sources().forEach((at, strength) -> text.append(at.world().asString()).append('|').append(at.position().x())
                .append('|').append(at.position().y()).append('|').append(at.position().z()).append('|')
                .append(String.format(Locale.ROOT, "%.9g", strength)).append('\n'));
        PaperFiles.write(this.plugin, this.file, text);
    }

    private static @Nullable PaperRadiation instance;

    /** Saves the sources (when the plugin stops). */
    static void saveNow() {
        if (instance != null) instance.save();
    }
}
