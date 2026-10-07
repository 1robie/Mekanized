package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.api.item.ResourceItem;
import fr.robie.machineengine.api.paper.PaperItemResources;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.paper.automation.PaperAutomation;
import fr.robie.machineengine.mekanized.paper.gear.GearBehaviors;
import fr.robie.machineengine.mekanized.paper.gear.GearItems;
import fr.robie.machineengine.mekanized.paper.gear.GearTools;
import fr.robie.machineengine.mekanized.paper.gear.ModuleHolderBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
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
        register("module_holder", ModuleHolderBehavior.FACTORY);
        register("configuration_card", PaperTools.CardBehavior.FACTORY);
        register("dictionary", PaperTools.DictionaryBehavior.FACTORY);
        register("gauge_dropper", PaperTools.DropperBehavior.FACTORY);
        register("crafting_formula", PaperTools.FormulaBehavior.FACTORY);
        register("configurator", PaperConfigurator.ConfiguratorBehavior.FACTORY);
        register("tier_installer", PaperConfigurator.InstallerBehavior.FACTORY);
        register("turbine_blade", PaperConfigurator.BladeBehavior.FACTORY);
        register("module", ModuleHolderBehavior.Module.FACTORY);
        register("flamethrower", GearTools.FlamethrowerBehavior.FACTORY);
        register("canteen", GearTools.CanteenBehavior.FACTORY);
        register("meka_tool", GearTools.MekaToolBehavior.FACTORY);
        register("radiation_reader", PaperRadiation.ReaderBehavior.FACTORY);
        // Gear numbers (Stages 5 and 6): read by the listeners and the suit ticker.
        register("mining_tool", GearBehaviors.MiningTool.FACTORY);
        register("electric_bow", GearBehaviors.ElectricBow.FACTORY);
        register("jetpack", GearBehaviors.Jetpack.FACTORY);
        register("scuba_tank", GearBehaviors.ScubaTank.FACTORY);
        register("fall_absorber", GearBehaviors.FallAbsorber.FACTORY);
        register("radiation_shielding", GearBehaviors.RadiationShielding.FACTORY);
        register("portable_teleporter", PaperAutomation.PortableBehavior.TELEPORTER);
        register("portable_qio", PaperAutomation.PortableBehavior.QIO);
        register("cardboard_box", PaperAutomation.BoxBehavior.FACTORY);
        register("cardboard_box_storage", PaperAutomation.FullBoxBehavior.FACTORY);
        register("seismic_reader", PaperAutomation.SeismicReaderBehavior.FACTORY);
    }

    private static void register(String name, ItemBehaviorFactory<?> factory) {
        ItemBehaviors.register(Key.of(MekanizedContent.NAMESPACE, name), factory);
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
