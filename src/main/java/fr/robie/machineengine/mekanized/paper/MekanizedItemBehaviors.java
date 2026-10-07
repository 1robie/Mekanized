package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.api.item.ResourceItem;
import fr.robie.machineengine.api.paper.PaperItemResources;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.paper.gear.GearItems;
import fr.robie.machineengine.mekanized.paper.gear.ModuleHolderBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.util.Key;

/**
 * Mekanized's CraftEngine item behaviors (D32). Energy and chemicals use MachineEngine's
 * {@code energy_item} and {@code resource_item}; the types here are Mekanism gameplay only.
 */
public final class MekanizedItemBehaviors {
    private MekanizedItemBehaviors() {
    }

    /** In {@code onLoad}: CraftEngine reads the item configs in its {@code onEnable}. */
    public static void register() {
        ItemBehaviors.register(Key.of(MekanizedContent.NAMESPACE, "module_holder"), ModuleHolderBehavior.FACTORY);
    }

    /** In {@code onEnable}: old item data, and the lore that shows what an item holds. */
    public static void install() {
        // Before D32 the amounts were Bukkit persistent data.
        PaperItemResources.migratePdcLong(MekanizedContent.NAMESPACE + ":energy", Energy.KEY);
        PaperItemResources.migratePdcLong(MekanizedContent.NAMESPACE + ":gear_chemical", item -> {
            net.kyori.adventure.key.Key[] chemical = {null};
            item.getBehavior().ifPresent(behavior -> behavior.let(ResourceItem.class, holder -> {
                if (chemical[0] == null && !holder.resource().equals(Energy.KEY)) chemical[0] = holder.resource();
            }));
            return chemical[0];
        });
        PaperItemResources.onChange(GearItems::refresh);
    }
}
