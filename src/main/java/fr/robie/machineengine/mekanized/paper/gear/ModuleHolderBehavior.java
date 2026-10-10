package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.item.ItemCapacityModifier;
import fr.robie.machineengine.api.item.MachineTool;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.machine.GearRules;
import net.kyori.adventure.key.Key;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The CraftEngine item behavior {@code mekanized:module_holder}: gear that takes modules (the MekaSuit,
 * the Meka-Tool). Installed modules with a {@code capacity-factor} (the energy unit) multiply the item's
 * energy capacity once per level (MachineEngine's {@code energy_item}, D32). Right-click a modification
 * station to take one level of the last module back out.
 *
 * <pre>{@code
 * behaviors:
 *   - type: machineengine:energy_item
 *     max-energy: 16000000
 *   - type: mekanized:module_holder
 * }</pre>
 */
public final class ModuleHolderBehavior extends PaperItemBehavior implements ItemCapacityModifier, MachineTool {
    public static final ItemBehaviorFactory<ModuleHolderBehavior> FACTORY = (pack, path, id, section) -> new ModuleHolderBehavior();

    @Override
    public long modify(@NotNull Item item, @NotNull Key resource, long capacity) {
        if (!resource.equals(Energy.KEY)) return capacity;
        long result = capacity;
        // Capacity counts installed levels whether on or off: an energy unit cannot be switched off.
        for (GearRules.Installed installed : GearItems.installed((ItemStack) item.platformItem())) {
            Module module = GearBehaviors.module(installed.name());
            if (module != null && module.capacityFactor() > 1) result = GearRules.scaled(result, module.capacityFactor(), installed.level());
        }
        return result;
    }

    /** At a modification station: one level of the last module comes back out as an item. */
    @Override
    protected boolean use(@NotNull Use use) {
        if (use.block() == null || GearItems.installed(use.item()).isEmpty() || !GearModules.isStation(use.block())) return false;
        GearModules.uninstall(use.player(), use.item());
        return true;
    }

    /**
     * {@code mekanized:module}: a module item; sneak-right-click to install one level into the gear it fits.
     * What the module costs and does is in its options; levels (D40) scale what Mekanism scales, see each
     * option.
     *
     * <pre>{@code
     * behaviors:
     *   - type: mekanized:module
     *     module: locomotive_boosting_unit
     *     max-level: 4                # how many of it one piece takes (default 1)
     *     energy: 2000                # Joules per second (or per use, per item, or made per second), 0 for none
     *     energy-per-level: true      # energy times the level (default false: the same at every level)
     *     effect-amplifier: 1         # potion effect modules: amplifier per level (0 = level I; default 0)
     *     effect-duration: 40         # potion effect modules: ticks, renewed every second (default 40)
     *     range: 4                    # the magnetic attraction unit: blocks per level
     *     pull-speed: 0.6             # the magnetic attraction unit: how fast items fly (default 0.6)
     *     step-height: 1.1            # the motorized servo unit: blocks the player steps up
     *     removes-effects: [minecraft:poison]   # the inhalation purification unit: effects it clears
     *     capacity-factor: 2          # the energy unit: multiplies the gear's energy capacity, once per level;
     *                                 # such a module cannot be switched off
     *     enchantment: minecraft:fortune   # the gear gets this enchantment while installed and on
     *     enchantment-level: 1        # per level
     *     glider: true                # the elytra unit: the chestplate glides (Paper 1.21.2+)
     *     fits: [mekanized:mekasuit_pants]   # the items it goes into (worn, or the off hand)
     * }</pre>
     *
     * <p>Mining tool modules (vein mining, excavation escalation, attack amplification) take the tool's
     * {@code mekanized:mining_tool} numbers from base to their {@code -with-unit} / amplified value in even
     * steps up to {@code max-level}.</p>
     */
    public static final class Module extends PaperItemBehavior {
        public static final ItemBehaviorFactory<Module> FACTORY = (pack, path, id, section) -> new Module(section);

        private final String module;
        private final int maxLevel;
        private final long energy;
        private final boolean energyPerLevel;
        private final int effectAmplifier;
        private final int effectDuration;
        private final int range;
        private final double pullSpeed;
        private final double stepHeight;
        private final List<String> removesEffects;
        private final long capacityFactor;
        private final @Nullable String enchantment;
        private final int enchantmentLevel;
        private final boolean glider;
        private final Set<net.kyori.adventure.key.Key> fits;

        private Module(ConfigSection section) {
            this.module = section.getNonEmptyString("module");
            this.maxLevel = Math.max(1, section.getInt("max-level", 1));
            this.energy = Math.max(0, section.getLong("energy", 0));
            this.energyPerLevel = section.getBoolean("energy-per-level", false);
            this.effectAmplifier = Math.max(0, section.getInt("effect-amplifier", 0));
            this.effectDuration = Math.max(1, section.getInt("effect-duration", 40));
            this.range = Math.max(0, section.getInt("range", 0));
            this.pullSpeed = Math.max(0, section.getDouble("pull-speed", 0.6));
            this.stepHeight = Math.max(0, section.getDouble("step-height", 0));
            this.removesEffects = section.getValue("removes-effects", value -> List.copyOf(value.getAsStringList()), List.of());
            this.capacityFactor = Math.max(1, section.getLong("capacity-factor", 1));
            this.enchantment = section.getString("enchantment", (String) null);
            this.enchantmentLevel = Math.max(1, section.getInt("enchantment-level", 1));
            this.glider = section.getBoolean("glider", false);
            this.fits = section.getValue("fits", value -> value.getAsStringList().stream().map(net.kyori.adventure.key.Key::key)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()), Set.of());
        }

        public String module() {
            return this.module;
        }

        /** The items it goes into. */
        public Set<net.kyori.adventure.key.Key> fits() {
            return this.fits;
        }

        /** How many of it one piece takes. */
        public int maxLevel() {
            return this.maxLevel;
        }

        /** Whether it can be switched off: not a module raising the gear's capacity. */
        public boolean toggleable() {
            return this.capacityFactor <= 1;
        }

        public long energy() {
            return this.energy;
        }

        /** Its energy at {@code level}: times the level with {@code energy-per-level}. */
        public long energy(int level) {
            return this.energyPerLevel ? this.energy * Math.max(1, level) : this.energy;
        }

        /** The potion amplifier it gives at {@code level} (0 = level I). */
        public int amplifier(int level) {
            return this.effectAmplifier * Math.max(1, level);
        }

        public int effectDuration() {
            return this.effectDuration;
        }

        public double pullSpeed() {
            return this.pullSpeed;
        }

        public double stepHeight() {
            return this.stepHeight;
        }

        /** The effects it clears (keys such as {@code minecraft:poison}). */
        public List<String> removesEffects() {
            return this.removesEffects;
        }

        public int range() {
            return this.range;
        }

        public long capacityFactor() {
            return this.capacityFactor;
        }

        /** The enchantment it gives, or {@code null}. */
        public @Nullable Enchantment enchantment() {
            if (this.enchantment == null) return null;
            NamespacedKey key = NamespacedKey.fromString(this.enchantment);
            return key == null ? null : Registry.ENCHANTMENT.get(key);
        }

        public int enchantmentLevel() {
            return this.enchantmentLevel;
        }

        public boolean glider() {
            return this.glider;
        }

        @Override
        protected boolean use(@NotNull Use use) {
            if (!use.sneaking()) return false;
            GearModules.install(use.player(), use.item(), this, this.fits);
            return true;
        }
    }
}
