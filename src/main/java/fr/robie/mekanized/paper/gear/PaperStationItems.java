package fr.robie.mekanized.paper.gear;

import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.paper.PaperItems;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.mekanized.MekanizedContent;
import fr.robie.mekanized.machine.GearRules;
import fr.robie.mekanized.machine.StationItems;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The modification station's view of CraftEngine gear and module items (D40): each edit turns the variant
 * into an item, changes it as a player's gear changes ({@link GearModules}), and turns it back.
 */
public final class PaperStationItems implements StationItems {
    public static final PaperStationItems INSTANCE = new PaperStationItems();

    private PaperStationItems() {
    }

    @Override
    public @Nullable List<GearRules.Installed> modules(@NotNull ResourceVariant gear) {
        ItemStack stack = PaperItems.toItemStack(gear, 1);
        if (stack == null || GearBehaviors.of(stack, ModuleHolderBehavior.class) == null) return null;
        return GearItems.installed(stack);
    }

    @Override
    public @Nullable ModuleItem module(@NotNull ResourceVariant item) {
        ItemStack stack = PaperItems.toItemStack(item, 1);
        return options(stack == null ? null : GearBehaviors.of(stack, ModuleHolderBehavior.Module.class));
    }

    @Override
    public @Nullable ModuleItem moduleNamed(@NotNull String name) {
        return options(GearBehaviors.module(name));
    }

    private static @Nullable ModuleItem options(@Nullable ModuleHolderBehavior.Module module) {
        return module == null ? null : new ModuleItem(module.module(), module.maxLevel(), module.fits(), module.toggleable());
    }

    @Override
    public @Nullable ResourceVariant moduleItem(@NotNull String name) {
        ItemStack stack = PaperItems.itemOf(MekanizedContent.key("module_" + name));
        return stack == null ? null : PaperItems.toResource(stack).variant();
    }

    @Override
    public @Nullable ResourceVariant install(@NotNull ResourceVariant gear, @NotNull String module) {
        ModuleHolderBehavior.Module options = GearBehaviors.module(module);
        ItemStack stack = PaperItems.toItemStack(gear, 1);
        if (options == null || stack == null || GearModules.install(stack, options) == null) return null;
        return back(gear, stack);
    }

    @Override
    public @NotNull ResourceVariant remove(@NotNull ResourceVariant gear, @NotNull String module, int levels) {
        ItemStack stack = PaperItems.toItemStack(gear, 1);
        if (stack == null) return gear;
        GearModules.remove(stack, module, levels);
        return back(gear, stack);
    }

    @Override
    public @Nullable ResourceVariant toggle(@NotNull ResourceVariant gear, @NotNull String module) {
        ItemStack stack = PaperItems.toItemStack(gear, 1);
        return stack == null || !GearModules.toggle(stack, module) ? null : back(gear, stack);
    }

    @Override
    public long energyLost(@NotNull ResourceVariant gear, @NotNull String module, int levels) {
        ItemStack stack = PaperItems.toItemStack(gear, 1);
        if (stack == null) return 0;
        long energy = GearItems.energy(stack);
        // Try it on a copy: the capacity left decides.
        ItemStack copy = stack.clone();
        GearModules.remove(copy, module, levels);
        return GearRules.energyLost(energy, GearItems.capacity(copy));
    }

    /** The changed item as a variant, keeping the gear's item id. */
    private static ResourceVariant back(ResourceVariant gear, ItemStack stack) {
        ItemData data = gear.components().get(Items.DATA);
        return PaperItems.toResource(stack, data == null ? null : data.item()).variant();
    }
}
