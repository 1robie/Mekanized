package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineAction;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.mekanized.MekanizedConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The modification station (D40), as Mekanism's: put a MekaSuit piece or the Meka-Tool in its gear slot and
 * a module in its module slot; it installs one level of the module for energy over time ({@code ticks} and
 * {@code energy-per-tick} in its block config, D34). Its menu lists the gear's modules ({@value #LIST}): take
 * a level or all of them out, or switch one on or off ({@value #ACTION}). Taking out an energy unit that
 * would lose energy asks for the same click again within {@code confirm-seconds}. What the items are is the platform's ({@link StationItems}).
 */
public final class ModificationStation {
    public static final String ACTION = "module";
    public static final String LIST = "modules";

    public static final ConfigKey<Long> TICKS = ConfigKey.whole("ticks", 40, 1, 72_000).clamped();
    public static final ConfigKey<Long> ENERGY_PER_TICK = ConfigKey.whole("energy-per-tick", 400, 0, MekanizedConfig.MAX).clamped();
    /** How long the second click of a removal that loses energy may wait. */
    public static final ConfigKey<Long> CONFIRM_SECONDS = ConfigKey.whole("confirm-seconds", 5, 1, 3_600).clamped();
    static final long ENERGY = 40_000;

    public static final MachineDataKey<String> STATUS = DataKeys.STATUS;
    /** Install progress in percent, for the menu's arrow. */
    public static final MachineDataKey<Long> PROGRESS = DataKeys.number("progress", 0);
    static final MachineDataKey<Long> WORKED = DataKeys.number("worked", 0);
    /** A removal that loses energy and waits for its second click: {@code module:levels@gameTime}. */
    static final MachineDataKey<String> CONFIRM = DataKeys.string("confirm", "");

    private static volatile @Nullable StationItems items;

    private ModificationStation() {
    }

    /** Set by the platform (Paper: CraftEngine items); without it the station takes nothing. */
    public static void setItems(@Nullable StationItems value) {
        items = value;
    }

    static void register(RegistrationContext context) {
        context.machineTypes().register(BasicMachines.base("modification_station", ENERGY)
                .storage(StorageDefinition.slots("gear", 1, 1).filter((variant, tags) -> {
                    StationItems current = items;
                    return current != null && current.modules(variant) != null;
                }))
                .storage(StorageDefinition.slots("module", 1, 64).filter((variant, tags) -> {
                    StationItems current = items;
                    return current != null && current.module(variant) != null;
                }))
                .data(STATUS).data(PROGRESS).data(WORKED).data(CONFIRM)
                .action(ACTION, ModificationStation::click)
                .list(LIST, ModificationStation::rows)
                .behavior(MachineParts.powered(type -> ModificationStation::tick))
                .build());
    }

    // -- installing ------------------------------------------------------------------------------

    private static void tick(MachineContext context) {
        StationItems current = items;
        ResourceVariant gear = only(context.storage("gear"));
        ResourceVariant module = only(context.storage("module"));
        String why = current == null ? "Not available" : blocked(current, gear, module);
        if (why != null) {
            stop(context, why);
            context.waitForInput();
            return;
        }
        String name = current.module(module).name();
        long ticks = context.options().get(TICKS);
        boolean paid;
        try (Transaction transaction = context.transactions().openOuter()) {
            paid = MachineParts.pay(context.storage("energy"), context.options().get(ENERGY_PER_TICK), transaction);
            if (paid) transaction.commit();
        }
        if (!paid) {
            context.setActive(false);
            context.set(STATUS, "Needs energy");
            context.wakeIn(20);
            return;
        }
        long worked = context.get(WORKED) + 1;
        context.setActive(true);
        context.set(STATUS, "Installing " + name.replace('_', ' '));
        // Working: it stays active and ticks every tick (a wake request would cost a tick each time).
        if (worked < ticks) {
            context.set(WORKED, worked);
            context.set(PROGRESS, worked * 100 / ticks);
            return;
        }
        ResourceVariant installed = current.install(gear, name);
        if (installed != null) {
            try (Transaction transaction = context.transactions().openOuter()) {
                ResourceHandler gearSlot = context.storage("gear");
                if (gearSlot.extract(gear, 1, transaction) == 1 && gearSlot.insert(installed, 1, transaction) == 1
                        && context.storage("module").extract(module, 1, transaction) == 1) {
                    transaction.commit();
                }
            }
        }
        context.set(WORKED, 0L);
        context.set(PROGRESS, 0L);
    }

    /** Why nothing can be installed now, or {@code null} if it can. */
    static @Nullable String blocked(StationItems items, @Nullable ResourceVariant gear, @Nullable ResourceVariant module) {
        if (gear == null) return "Put a MekaSuit piece or Meka-Tool in";
        List<GearRules.Installed> modules = items.modules(gear);
        if (modules == null) return "This gear takes no modules";
        if (module == null) return "Put a module in to install it";
        StationItems.ModuleItem options = items.module(module);
        if (options == null) return "That is not a module";
        ItemData data = gear.components().get(Items.DATA);
        if (data == null || !options.fits().contains(data.item())) return "This module does not fit this gear";
        if (GearRules.installedLevel(modules, options.name()) >= options.maxLevel()) {
            return "At its max level (" + options.maxLevel() + ")";
        }
        return null;
    }

    private static void stop(MachineContext context, String status) {
        context.setActive(false);
        context.set(STATUS, status);
        context.set(WORKED, 0L);
        context.set(PROGRESS, 0L);
    }

    private static @Nullable ResourceVariant only(ResourceHandler storage) {
        for (StorageView view : storage.views()) {
            if (!view.isEmpty()) return view.variant();
        }
        return null;
    }

    // -- the module list -------------------------------------------------------------------------

    /** Rows: {@code name}, {@code level}, {@code max}, {@code state} (on, off), {@code item} (its module item's id). */
    private static List<Map<String, String>> rows(MachineContext context) {
        StationItems current = items;
        ResourceVariant gear = only(context.storage("gear"));
        List<GearRules.Installed> modules = current == null || gear == null ? null : current.modules(gear);
        if (modules == null) return List.of();
        List<Map<String, String>> rows = new ArrayList<>();
        for (GearRules.Installed module : modules) {
            StationItems.ModuleItem options = current.moduleNamed(module.name());
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", module.name());
            row.put("level", Integer.toString(module.level()));
            row.put("max", Integer.toString(options == null ? module.level() : options.maxLevel()));
            row.put("state", module.enabled() ? "on" : "off");
            row.put("toggleable", Boolean.toString(options == null || options.toggleable()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * A click on row {@code index}: left takes one level out, shift all of them, right switches the module.
     * A removal that loses energy asks for the same click again.
     */
    private static MachineAction.Outcome click(MachineContext context, MachineAction.Click click) {
        StationItems current = items;
        ResourceVariant gear = only(context.storage("gear"));
        List<GearRules.Installed> modules = current == null || gear == null ? null : current.modules(gear);
        if (modules == null || click.index() >= modules.size() || click.kind() == MachineAction.ClickKind.INSERT) {
            return MachineAction.Outcome.nothing();
        }
        GearRules.Installed module = modules.get(click.index());
        String pending = context.get(CONFIRM);
        context.set(CONFIRM, "");
        if (click.kind() == MachineAction.ClickKind.RIGHT) {
            ResourceVariant toggled = current.toggle(gear, module.name());
            if (toggled == null) return MachineAction.Outcome.refused(Component.text("This module cannot be switched off", NamedTextColor.RED));
            return replace(context, gear, toggled) ? MachineAction.Outcome.ok() : MachineAction.Outcome.nothing();
        }
        int levels = click.kind() == MachineAction.ClickKind.SHIFT ? module.level() : 1;
        ResourceVariant item = current.moduleItem(module.name());
        if (item == null) {
            return MachineAction.Outcome.refused(Component.text("No item for " + module.name() + "; it stays in", NamedTextColor.RED));
        }
        long lost = current.energyLost(gear, module.name(), levels);
        String confirm = module.name() + ":" + levels;
        long seconds = context.options().get(CONFIRM_SECONDS);
        if (lost > 0 && !confirmed(pending, confirm, context.gameTime(), seconds * 20)) {
            context.set(CONFIRM, confirm + "@" + context.gameTime());
            return MachineAction.Outcome.refused(Component.text("That loses " + String.format(Locale.ROOT, "%,d", lost)
                    + " J of stored energy: click again within " + seconds + " s to take it out", NamedTextColor.GOLD));
        }
        ResourceVariant after = current.remove(gear, module.name(), levels);
        int removed = module.level() - GearRules.installedLevel(current.modules(after) == null ? List.of() : current.modules(after), module.name());
        if (removed <= 0 || !replace(context, gear, after)) return MachineAction.Outcome.nothing();
        return MachineAction.Outcome.give(new ResourceStack(item, removed));
    }

    /** Whether {@code pending} ({@code click@gameTime}) is the same {@code click}, made at most {@code ticks} ago. */
    static boolean confirmed(String pending, String click, long now, long ticks) {
        int at = pending.lastIndexOf('@');
        if (at < 0 || !pending.substring(0, at).equals(click)) return false;
        try {
            long then = Long.parseLong(pending.substring(at + 1));
            return now >= then && now - then <= ticks;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Puts {@code after} in the gear slot in place of {@code before}. */
    private static boolean replace(MachineContext context, ResourceVariant before, ResourceVariant after) {
        try (Transaction transaction = context.transactions().openOuter()) {
            ResourceHandler slot = context.storage("gear");
            if (slot.extract(before, 1, transaction) != 1 || slot.insert(after, 1, transaction) != 1) return false;
            transaction.commit();
            return true;
        }
    }
}
