package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.api.paper.PaperItemResources;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.machine.UtilityBehaviors;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.paper.PaperItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockBreakEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.paper.MachineInteractions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Paper side of the utility blocks: bins taken from and filled by hand, personal
 * chests and barrels kept to their owner, chargepads charging the players on them, and the server's
 * crafting recipes and items for the Formulaic Assemblicator and the Oredictionificator.
 */
public final class PaperUtility implements Listener {
    private static final long CHARGE_EVERY = 10;
    /** Crafting grids already looked up: recipes do not change while the server runs (bounded). */
    private static final Map<List<ResourceVariant>, Optional<ResourceStack>> CRAFTED = new ConcurrentHashMap<>();

    private final Plugin plugin;

    private PaperUtility(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void install(Plugin plugin) {
        UtilityBehaviors.setItemFactory(id -> {
            ItemStack stack = PaperItems.itemOf(id);
            return stack == null ? null : PaperItems.toResource(stack, id).variant();
        });
        UtilityBehaviors.setCrafting(PaperUtility::craft);
        PaperUtility listener = new PaperUtility(plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        for (Key type : MachineEngine.get().registries().registry(Registries.MACHINE_TYPE).keys()) {
            if (UtilityMachines.isBin(type)) MachineInteractions.register(type, BIN);
            if (UtilityMachines.isPersonal(type)) MachineInteractions.register(type, PERSONAL);
        }
        for (Player player : Bukkit.getOnlinePlayers()) listener.startCharging(player);
    }

    private static @Nullable ResourceStack craft(Key world, List<@Nullable ResourceVariant> grid) {
        if (CRAFTED.size() > 1024) CRAFTED.clear();
        List<ResourceVariant> key = Collections.unmodifiableList(new ArrayList<>(grid));
        return CRAFTED.computeIfAbsent(key, cells -> {
            ItemStack[] matrix = new ItemStack[9];
            for (int i = 0; i < 9 && i < cells.size(); i++) {
                ResourceVariant cell = cells.get(i);
                matrix[i] = cell == null ? ItemStack.empty() : PaperItems.toItemStack(cell, 1);
                if (matrix[i] == null) matrix[i] = ItemStack.empty();
            }
            // The machine's own world: the one this region thread may touch.
            World at = PaperWorld.world(world);
            if (at == null) return Optional.empty();
            Recipe recipe = Bukkit.getCraftingRecipe(matrix, at);
            if (recipe == null || recipe.getResult().isEmpty()) return Optional.empty();
            return Optional.of(PaperItems.toResource(recipe.getResult()));
        }).orElse(null);
    }

    // -- bins and personal storage -----------------------------------------------------------

    /** A personal chest or barrel: the first player to open it owns it; only they (or the bypass permission) open it after. */
    private static final MachineInteractions.Interaction PERSONAL = new MachineInteractions.Interaction() {
        @Override
        public boolean use(MachineInteractions.Click click) {
            Player player = click.player();
            MachineContext context = click.machine().context();
            String owner = context.get(UtilityMachines.OWNER);
            String self = player.getUniqueId().toString();
            if (owner.isEmpty()) {
                context.set(UtilityMachines.OWNER, self);
                player.sendActionBar(Component.text("This is now your personal storage", NamedTextColor.GREEN));
            } else if (!owner.equals(self) && !player.hasPermission("mekanized.personal.bypass")) {
                player.sendActionBar(Component.text("This belongs to someone else", NamedTextColor.RED));
                return true;
            }
            return false; // its menu opens
        }
    };

    /** A bin: right-click with an item puts the stack in, left-click takes a stack out (one while sneaking). */
    private static final MachineInteractions.Interaction BIN = new MachineInteractions.Interaction() {
        @Override
        public boolean use(MachineInteractions.Click click) {
            ItemStack hand = click.item();
            if (hand.isEmpty() || click.sneaking()) return false;
            MachineInstance machine = click.machine();
            MachineContext context = machine.context();
            ResourceVariant variant = PaperItems.toResource(hand).variant();
            long inserted;
            try (Transaction transaction = context.transactions().openOuter()) {
                inserted = context.storage("items").insert(variant, hand.getAmount(), transaction);
                transaction.commit();
            }
            if (inserted > 0) {
                hand.setAmount(hand.getAmount() - (int) inserted);
                machine.wake(WakeReason.RESOURCE_INSERTED);
            } else {
                click.player().sendActionBar(Component.text("The bin holds another item, or is full", NamedTextColor.RED));
            }
            return true;
        }

        @Override
        public boolean attack(MachineInteractions.Click click) {
            MachineInstance machine = click.machine();
            ResourceHandler items = machine.context().storage("items");
            StorageView held = items.views().getFirst();
            if (held.isEmpty()) return false; // an empty bin: left-click breaks it as usual
            Player player = click.player();
            ItemStack one = PaperItems.toItemStack(held.variant(), 1);
            long wanted = click.sneaking() ? 1 : Math.min(held.amount(), one == null ? 64 : one.getMaxStackSize());
            ResourceVariant variant = held.variant();
            long taken;
            try (Transaction transaction = machine.context().transactions().openOuter()) {
                taken = items.extract(variant, wanted, transaction);
                transaction.commit();
            }
            for (ItemStack stack : PaperItems.toItemStacks(new ResourceStack(variant, taken))) {
                PaperWorld.give(player, stack);
            }
            machine.wake(WakeReason.RESOURCE_EXTRACTED);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "%,d left", Math.max(0, held.amount())), NamedTextColor.GRAY));
            return true;
        }
    };

    /** Only its owner breaks a personal chest or barrel (or players with the bypass permission). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(CustomBlockBreakEvent event) {
        String owner = personalOwner(event.bukkitBlock());
        if (owner == null || owner.isEmpty()) return;
        Player player = event.getPlayer();
        if (owner.equals(player.getUniqueId().toString()) || player.hasPermission("mekanized.personal.bypass")) return;
        event.setCancelled(true);
        player.sendActionBar(Component.text("This belongs to someone else", NamedTextColor.RED));
    }

    /** The owner of the personal chest or barrel at {@code block} ("" if none yet), or null if it is not one. */
    private static @Nullable String personalOwner(Block block) {
        WorldPosition position = PaperWorld.position(block);
        if (!PaperWorld.isOwnedByCurrentThread(position)) return null;
        MachineInstance machine = PaperWorld.machine(position);
        if (machine == null || !UtilityMachines.isPersonal(machine.inspect().type())) return null;
        return machine.context().get(UtilityMachines.OWNER);
    }

    // -- chargepads --------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startCharging(event.getPlayer());
    }

    /** On the player's own thread (Folia: the region they are in), which owns the block they stand on. */
    private void startCharging(Player player) {
        player.getScheduler().runAtFixedRate(this.plugin, task -> charge(player), null, CHARGE_EVERY, CHARGE_EVERY);
    }

    private static void charge(Player player) {
        if (!player.isOnline()) return;
        Block feet = player.getLocation().getBlock();
        MachineInstance pad = null;
        for (Block block : new Block[]{feet, feet.getRelative(BlockFace.DOWN)}) {
            WorldPosition position = PaperWorld.position(block);
            if (!PaperWorld.isOwnedByCurrentThread(position)) continue;
            MachineInstance machine = PaperWorld.machine(position);
            if (machine != null && machine.inspect().type().equals(MekanizedContent.key("chargepad"))) {
                pad = machine;
                break;
            }
        }
        if (pad == null) return;
        MachineContext context = pad.context();
        long budget = context.options().get(UtilityMachines.CHARGE_PER_TICK) * CHARGE_EVERY;
        PlayerInventory inventory = player.getInventory();
        boolean charged = false;
        for (int slot = 0; slot < inventory.getSize() && budget > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || stack.getAmount() != 1) continue;
            long capacity = PaperItemResources.capacity(stack, Energy.KEY);
            if (capacity <= 0) continue;
            long stored = PaperItemResources.amount(stack, Energy.KEY);
            long wanted = Math.min(budget, capacity - stored);
            if (wanted <= 0) continue;
            long taken;
            try (Transaction transaction = context.transactions().openOuter()) {
                taken = context.storage("energy").extract(MekanizedContent.ENERGY.variant(), wanted, transaction);
                transaction.commit();
            }
            if (taken <= 0) break;
            PaperItemResources.withAmount(stack, Energy.KEY, stored + taken);
            budget -= taken;
            charged = true;
        }
        if (charged) pad.wake(WakeReason.RESOURCE_EXTRACTED);
    }
}
