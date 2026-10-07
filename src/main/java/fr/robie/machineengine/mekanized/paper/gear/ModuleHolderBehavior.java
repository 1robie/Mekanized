package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.item.ItemCapacityModifier;
import fr.robie.machineengine.api.item.MachineTool;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.machine.GearRules;
import fr.robie.machineengine.mekanized.machine.GearSettings;
import net.kyori.adventure.key.Key;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The CraftEngine item behavior {@code mekanized:module_holder}: gear that takes modules (the MekaSuit,
 * the Meka-Tool). An installed energy unit multiplies the item's energy capacity (MachineEngine's
 * {@code energy_item}, D32). Right-click a modification station to take the last module back out.
 *
 * <pre>{@code
 * behaviors:
 *   - type: machineengine:energy_item
 *     max-energy: 16000000
 *   - type: mekanized:module_holder
 *     energy-unit-factor: 10     # optional, default: gear.energy-unit-factor in config.yml
 * }</pre>
 */
public final class ModuleHolderBehavior extends PaperItemBehavior implements ItemCapacityModifier, MachineTool {
    public static final ItemBehaviorFactory<ModuleHolderBehavior> FACTORY = (pack, path, id, section) ->
            new ModuleHolderBehavior(section.getLong("energy-unit-factor", 0));

    /** 0: the config's factor, read when used so a config reload applies. */
    private final long energyUnitFactor;

    private ModuleHolderBehavior(long energyUnitFactor) {
        this.energyUnitFactor = Math.max(0, energyUnitFactor);
    }

    @Override
    public long modify(@NotNull Item item, @NotNull Key resource, long capacity) {
        if (!resource.equals(Energy.KEY)) return capacity;
        return GearRules.energyCapacity(capacity, GearItems.modules((ItemStack) item.platformItem()),
                this.energyUnitFactor > 0 ? this.energyUnitFactor : GearSettings.energyUnitFactor);
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
     * {@code mekanized:module}: a module item; sneak-right-click to install it into the gear it fits.
     *
     * <pre>{@code
     * behaviors:
     *   - type: mekanized:module
     *     module: vein_mining_unit
     * }</pre>
     */
    public static final class Module extends PaperItemBehavior {
        public static final ItemBehaviorFactory<Module> FACTORY = (pack, path, id, section) -> new Module(section.getNonEmptyString("module"));

        private final String module;

        private Module(String module) {
            this.module = module;
        }

        @Override
        protected boolean use(@NotNull Use use) {
            if (!use.sneaking()) return false;
            GearModules.install(use.player(), use.item(), this.module);
            return true;
        }
    }
}
