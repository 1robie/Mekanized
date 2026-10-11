package fr.robie.mekanized.paper;

import fr.robie.machineengine.api.item.ResourceItem;
import fr.robie.machineengine.api.paper.PaperItemResources;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.mekanized.MekanizedContent;
import fr.robie.mekanized.paper.automation.PaperAutomation;
import fr.robie.mekanized.machine.QioDrives;
import fr.robie.mekanized.paper.gear.GearBehaviors;
import fr.robie.mekanized.paper.gear.GearItems;
import fr.robie.mekanized.paper.gear.GearLoreProcessor;
import fr.robie.mekanized.paper.gear.GearTools;
import fr.robie.mekanized.paper.gear.ModuleHolderBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.item.processor.ItemProcessors;
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
        register("qio_drive", GearBehaviors.QioDrive.FACTORY);
        register("portable_teleporter", PaperAutomation.PortableBehavior.TELEPORTER);
        register("portable_qio", PaperAutomation.PortableBehavior.QIO);
        register("cardboard_box", PaperAutomation.BoxBehavior.FACTORY);
        register("cardboard_box_storage", PaperAutomation.FullBoxBehavior.FACTORY);
        register("seismic_reader", PaperAutomation.SeismicReaderBehavior.FACTORY);
    }

    /** In {@code onLoad} too: item processors for {@code client-bound-data}. */
    public static void registerProcessors() {
        ItemProcessors.register(Key.of(MekanizedContent.NAMESPACE, "gear_lore"), GearLoreProcessor.FACTORY);
    }

    private static void register(String name, ItemBehaviorFactory<?> factory) {
        ItemBehaviors.register(Key.of(MekanizedContent.NAMESPACE, name), factory);
    }

    /** In {@code onEnable}: old item data, and the lore that shows what an item holds. */
    public static void install() {
        // What a QIO drive holds: its item config's mekanized:qio_drive (D34).
        QioDrives.setAccess(item -> {
            GearBehaviors.QioDrive drive = GearBehaviors.options(item, GearBehaviors.QioDrive.class);
            return drive == null ? null : new QioDrives.Limits(drive.items(), drive.kinds());
        });
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
