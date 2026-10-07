package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.item.ItemCapacityModifier;
import fr.robie.machineengine.api.item.MachineTool;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.machine.GearRules;
import net.kyori.adventure.key.Key;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The CraftEngine item behavior {@code mekanized:module_holder}: gear that takes modules (the MekaSuit,
 * the Meka-Tool). Installed modules with a {@code capacity-factor} (the energy unit) multiply the item's
 * energy capacity (MachineEngine's {@code energy_item}, D32). Right-click a modification station to take
 * the last module back out.
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
        for (String installed : GearItems.modules((ItemStack) item.platformItem())) {
            Module module = GearBehaviors.module(installed);
            if (module != null && module.capacityFactor() > 1) result = GearRules.scaled(result, module.capacityFactor());
        }
        return result;
    }

    /** At a modification station: the last module comes back out as an item. */
    @Override
    protected boolean use(@NotNull Use use) {
        if (use.block() == null || GearItems.modules(use.item()).isEmpty() || !GearModules.isStation(use.block())) return false;
        String id = GearItems.id(use.item());
        if (id == null) return false;
        GearModules.uninstall(use.player(), use.item(), id);
        return true;
    }

    /**
     * {@code mekanized:module}: a module item; sneak-right-click to install it into the gear it fits. What
     * the module costs and does is in its options.
     *
     * <pre>{@code
     * behaviors:
     *   - type: mekanized:module
     *     module: vision_enhancement_unit
     *     energy: 200                 # Joules per second (or per use, per item, or made per second), 0 for none
     *     range: 8                    # the magnetic attraction unit: blocks
     *     capacity-factor: 4          # the energy unit: multiplies the gear's energy capacity
     *     enchantment: minecraft:fortune   # the gear gets this enchantment while installed
     *     enchantment-level: 3
     *     glider: true                # the elytra unit: the chestplate glides (Paper 1.21.2+)
     * }</pre>
     */
    public static final class Module extends PaperItemBehavior {
        public static final ItemBehaviorFactory<Module> FACTORY = (pack, path, id, section) -> new Module(
                section.getNonEmptyString("module"), section.getLong("energy", 0), section.getInt("range", 0),
                section.getLong("capacity-factor", 1), section.getString("enchantment", (String) null),
                section.getInt("enchantment-level", 1), section.getBoolean("glider", false));

        private final String module;
        private final long energy;
        private final int range;
        private final long capacityFactor;
        private final @Nullable String enchantment;
        private final int enchantmentLevel;
        private final boolean glider;

        private Module(String module, long energy, int range, long capacityFactor, @Nullable String enchantment,
                       int enchantmentLevel, boolean glider) {
            this.module = module;
            this.energy = Math.max(0, energy);
            this.range = Math.max(0, range);
            this.capacityFactor = Math.max(1, capacityFactor);
            this.enchantment = enchantment;
            this.enchantmentLevel = Math.max(1, enchantmentLevel);
            this.glider = glider;
        }

        public String module() {
            return this.module;
        }

        public long energy() {
            return this.energy;
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
            GearModules.install(use.player(), use.item(), this.module);
            return true;
        }
    }
}
