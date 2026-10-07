package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.paper.gear.GearItems;
import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.paper.persist.LineStore;
import fr.robie.machineengine.mekanized.machine.SaveLines;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import fr.robie.machineengine.api.item.MachineTool;

import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The player side of {@link Radiation}: each second a player takes in the radiation where they stand
 * (less what hazmat pieces and MekaSuit radiation shielding units stop, a quarter each by default), their dose
 * fades slowly, and a high dose hurts. The Geiger counter reads the radiation, the dosimeter the dose;
 * a MekaSuit helmet with the matching unit shows them all the time. Sources are saved in
 * {@code plugins/Mekanized/radiation.txt}.
 */
public final class PaperRadiation implements Listener {
    /** The player's dose in Sv. */
    static final NamespacedKey DOSE = new NamespacedKey(MekanizedContent.NAMESPACE, "radiation_dose");
    private static final long SAVE_SECONDS = 30;

    private final Plugin plugin;
    private final Path file;

    private PaperRadiation(Plugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("radiation.txt");
    }

    public static void install(Plugin plugin) {
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
        double dose = Radiation.nextDose(dose(player), level, shielding(player));
        player.getPersistentDataContainer().set(DOSE, PersistentDataType.DOUBLE, dose);
        ItemStack helmet = player.getInventory().getHelmet();
        if ("mekasuit_helmet".equals(GearItems.id(helmet))) {
            var modules = GearItems.modules(helmet);
            String shown = (modules.contains("geiger_unit") ? "Radiation " + Radiation.format(level, "/h") : "")
                    + (modules.contains("geiger_unit") && modules.contains("dosimeter_unit") ? " - " : "")
                    + (modules.contains("dosimeter_unit") ? "Dose " + Radiation.format(dose, "") : "");
            if (!shown.isEmpty() && level > RadiationSettings.background * 10) player.sendActionBar(Component.text(shown, NamedTextColor.GREEN));
        }
        // Mekanism's thresholds: a high dose makes you sick, a very high one hurts, a fatal one kills.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (Radiation.causesSickness(dose) && random.nextInt(10) == 0) player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 200, 0));
        if (Radiation.causesDamage(dose)) {
            if (random.nextInt(10) == 0) player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, false, false));
            if (random.nextInt(5) == 0) player.damage(1);
        }
        if (Radiation.isLethal(dose)) player.damage(4);
    }

    static double level(Location at) {
        return Radiation.levelAt(Key.key(at.getWorld().getKey().asString()), at.getX(), at.getY() + 1, at.getZ());
    }

    static double dose(Player player) {
        Double dose = player.getPersistentDataContainer().get(DOSE, PersistentDataType.DOUBLE);
        return dose == null ? 0 : dose;
    }

    /** Hazmat pieces and MekaSuit pieces with a radiation shielding unit stop a quarter each by default ({@link RadiationSettings#shieldingPerPiece}). */
    private static double shielding(Player player) {
        ItemStack[] armour = {player.getInventory().getHelmet(), player.getInventory().getChestplate(),
                player.getInventory().getLeggings(), player.getInventory().getBoots()};
        int pieces = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack piece = armour[i];
            if (Radiation.shields(i, GearItems.id(piece), () -> GearItems.modules(piece))) pieces++;
        }
        return Radiation.shielding(pieces);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getEntity().getPersistentDataContainer().remove(DOSE);
    }

    /**
     * {@code mekanized:radiation_reader}: right-click to read the radiation here ({@code mode: environment},
     * the Geiger counter) or your own dose ({@code mode: dose}, the dosimeter).
     */
    public static final class ReaderBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<ReaderBehavior> FACTORY = (pack, path, id, section) ->
                new ReaderBehavior(section.getString("mode", "environment").equalsIgnoreCase("dose"));

        private final boolean dose;

        private ReaderBehavior(boolean dose) {
            this.dose = dose;
        }

        @Override
        protected boolean use(Use use) {
            Player player = use.player();
            if (this.dose) {
                double dose = dose(player);
                player.sendMessage(Component.text("Your radiation dose: ", NamedTextColor.GRAY)
                        .append(Component.text(Radiation.format(dose, ""), color(dose))));
            } else {
                double level = level(player.getLocation());
                player.sendMessage(Component.text("Radiation here: ", NamedTextColor.GRAY)
                        .append(Component.text(Radiation.format(level, "/h"), color(level * 3600))));
            }
            return true;
        }
    }

    /** Green below 0.1 Sv, yellow below 1, red above (for an hour's worth of a level, or a dose). */
    private static NamedTextColor color(double sieverts) {
        return switch (Radiation.severity(sieverts)) {
            case LOW -> NamedTextColor.GREEN;
            case MEDIUM -> NamedTextColor.YELLOW;
            case HIGH -> NamedTextColor.RED;
        };
    }

    // -- saving ------------------------------------------------------------------------------------

    private void load() {
        LineStore.load(this.plugin, this.file, SaveLines.RADIATION_FIELDS, SaveLines::readRadiationSource);
    }

    synchronized void save() {
        StringBuilder text = new StringBuilder();
        Radiation.sources().forEach((at, strength) -> text.append(SaveLines.radiationSource(at, strength)));
        LineStore.write(this.plugin, this.file, text);
    }

    private static @Nullable PaperRadiation instance;

    /** Saves the sources (when the plugin stops). */
    public static void saveNow() {
        if (instance != null) instance.save();
    }
}
