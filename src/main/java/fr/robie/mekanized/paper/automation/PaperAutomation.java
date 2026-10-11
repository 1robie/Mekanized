package fr.robie.mekanized.paper.automation;

import fr.robie.machineengine.api.player.MachinePlayer;
import fr.robie.mekanized.MekanizedPlayers;
import fr.robie.mekanized.paper.gear.GearItems;
import fr.robie.mekanized.MekanizedContent;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.mekanized.machine.Frequencies;
import fr.robie.mekanized.machine.TeleporterSettings;
import fr.robie.mekanized.machine.AutomationMachines;
import fr.robie.mekanized.machine.MachineParts;
import fr.robie.mekanized.paper.Lore;
import fr.robie.mekanized.paper.PaperWorld;
import fr.robie.mekanized.paper.persist.LineStore;
import fr.robie.mekanized.machine.SaveLines;
import fr.robie.machineengine.api.platform.Nms;
import fr.robie.machineengine.api.paper.PaperItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import fr.robie.machineengine.api.item.MachineTool;
import org.bukkit.entity.Entity;
import fr.robie.machineengine.api.paper.MachineInteractions;
import fr.robie.mekanized.paper.gear.GearBehaviors;
import fr.robie.mekanized.paper.ItemData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The Paper side of the automation machines: frequencies ({@code /mekanized frequency}), teleporting players
 * who walk into a teleporter's portal, the cardboard box, the seismic reader and the QIO dashboard
 * (a chest menu of everything the frequency's drive arrays hold).
 */
public final class PaperAutomation implements Listener {
    /** Blocks the cardboard box leaves alone. */
    private static final Set<Material> UNBOXABLE = EnumSet.of(Material.BEDROCK, Material.END_PORTAL_FRAME,
            Material.END_PORTAL, Material.NETHER_PORTAL, Material.END_GATEWAY, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW);
    /** Common blocks the seismic reader does not list. */
    private static final Set<String> COMMON = Set.of("stone", "deepslate", "dirt", "grass_block", "gravel", "tuff",
            "andesite", "diorite", "granite", "bedrock", "air", "cave_air", "water", "lava", "netherrack", "sand", "sandstone", "calcite",
            "cobbled_deepslate", "clay", "mud", "red_sand", "terracotta", "end_stone", "basalt", "blackstone", "soul_sand", "soul_soil");

    private final Plugin plugin;
    private final Path teleporterFile;

    private PaperAutomation(Plugin plugin) {
        this.plugin = plugin;
        this.teleporterFile = plugin.getDataFolder().toPath().resolve("teleporters.txt");
    }

    /** The running instance, for the item behaviors (CraftEngine builds those). */
    private static volatile @Nullable PaperAutomation instance;

    public static void install(Plugin plugin) {
        PaperAutomation automation = new PaperAutomation(plugin);
        instance = automation;
        MachineInteractions.register(MekanizedContent.key("qio_dashboard"), new MachineInteractions.Interaction() {
            @Override
            public boolean use(MachineInteractions.Click click) {
                return automation.openDashboard(click);
            }
        });
        MachineInteractions.register(MekanizedContent.key("teleporter"), new MachineInteractions.Interaction() {
            @Override
            public void steppedOn(MachineInstance machine, Entity entity) {
                automation.steppedOn(machine, entity);
            }
        });
        automation.loadTeleporters();
        Frequencies.onTeleportersChanged(automation::saveTeleporters);
        Bukkit.getPluginManager().registerEvents(automation, plugin);
    }

    // -- frequencies --------------------------------------------------------------------------

    /** Sets the frequency of the teleporter or QIO block {@code player} looks at (on the player's thread). */
    public static void setFrequency(Player player, String frequency) {
        PaperWorld.scheduler().run(player, () -> {
            Block block = player.getTargetBlockExact(6);
            MachineInstance machine = block == null ? null : PaperWorld.machine(block);
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (GearBehaviors.of(hand, PortableBehavior.class) != null && machine == null) {
                ItemData.setString(hand, ItemData.Field.FREQUENCY, frequency);
                player.sendMessage(Component.text("Frequency of the item set to ", NamedTextColor.GRAY).append(Component.text(frequency, NamedTextColor.YELLOW)));
                return;
            }
            if (machine == null
                    || machine.type().data().stream().noneMatch(key -> key.id().equals(AutomationMachines.FREQUENCY.id()))) {
                player.sendMessage(Component.text("Look at a teleporter, a QIO block or a quantum entangloporter (or hold a portable teleporter or dashboard)", NamedTextColor.RED));
                return;
            }
            machine.context().set(AutomationMachines.FREQUENCY, frequency);
            machine.wake(WakeReason.CONFIG_CHANGED);
            player.sendMessage(Component.text("Frequency set to ", NamedTextColor.GRAY).append(Component.text(frequency, NamedTextColor.YELLOW)));
        }, null);
    }

    // -- teleporters --------------------------------------------------------------------------

    /** A player standing on a formed teleporter goes to the other one on its frequency. */
    private void steppedOn(MachineInstance source, Entity entity) {
        if (!(entity instanceof Player player)) return;
        MachinePlayer data = MekanizedPlayers.of(player).orElse(null);
        if (data == null || System.currentTimeMillis() - data.get(MekanizedPlayers.LAST_TELEPORT) < TeleporterSettings.cooldownMs) return;
        WorldPosition from = source.position();
        Frequencies.Teleporter teleporter = Frequencies.teleporters().get(from);
        if (teleporter == null || !teleporter.formed()) return;
        WorldPosition to = Frequencies.destination(from, teleporter.frequency());
        if (to == null) {
            player.sendActionBar(Component.text("No other teleporter on " + teleporter.frequency(), NamedTextColor.RED));
            data.set(MekanizedPlayers.LAST_TELEPORT, System.currentTimeMillis());
            return;
        }
        World world = PaperWorld.world(to);
        if (world == null) return;
        long cost = Frequencies.cost(from, to);
        MachineContext context = source.context();
        try (Transaction transaction = context.transactions().openOuter()) {
            if (!MachineParts.pay(context.storage("energy"), cost, transaction)) {
                player.sendActionBar(Component.text(String.format(Locale.ROOT, "The teleporter needs %,d J", cost), NamedTextColor.RED));
                data.set(MekanizedPlayers.LAST_TELEPORT, System.currentTimeMillis());
                return;
            }
            transaction.commit();
        }
        data.set(MekanizedPlayers.LAST_TELEPORT, System.currentTimeMillis());
        Location target = new Location(world, to.position().x() + 0.5, to.position().y() + 1, to.position().z() + 0.5,
                player.getLocation().getYaw(), player.getLocation().getPitch());
        player.teleportAsync(target);
    }

    private void loadTeleporters() {
        LineStore.load(this.plugin, this.teleporterFile, SaveLines.TELEPORTER_FIELDS, SaveLines::readTeleporter);
    }

    private synchronized void saveTeleporters() {
        StringBuilder text = new StringBuilder();
        Frequencies.teleporters().forEach((position, teleporter) -> text.append(SaveLines.teleporter(position, teleporter)));
        LineStore.write(this.plugin, this.teleporterFile, text);
    }

    // -- portable teleporter, portable QIO dashboard ------------------------------------------

    /**
     * {@code mekanized:portable_teleporter} and {@code mekanized:portable_qio}: right-click to go to the
     * teleporter on the item's frequency, or to open the QIO network on it.
     */
    public static final class PortableBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<PortableBehavior> TELEPORTER = (pack, path, id, section) -> new PortableBehavior(false);
        public static final ItemBehaviorFactory<PortableBehavior> QIO = (pack, path, id, section) -> new PortableBehavior(true);

        private final boolean dashboard;

        private PortableBehavior(boolean dashboard) {
            this.dashboard = dashboard;
        }

        @Override
        protected boolean use(Use use) {
            PaperAutomation automation = instance;
            if (automation != null) automation.usePortable(use.player(), use.item(), this.dashboard);
            return true;
        }
    }

    private void usePortable(Player player, ItemStack hand, boolean dashboard) {
        String frequency = ItemData.getString(hand, ItemData.Field.FREQUENCY);
        if (frequency == null || frequency.isEmpty()) {
            player.sendActionBar(Component.text("No frequency: hold it and use /mekanized frequency <name>", NamedTextColor.RED));
            return;
        }
        if (dashboard) {
            new Dashboard(frequency, 0).open(player);
            return;
        }
        MachinePlayer data = MekanizedPlayers.of(player).orElse(null);
        if (data == null || System.currentTimeMillis() - data.get(MekanizedPlayers.LAST_TELEPORT) < TeleporterSettings.cooldownMs) return;
        data.set(MekanizedPlayers.LAST_TELEPORT, System.currentTimeMillis());
        WorldPosition from = PaperWorld.position(player.getLocation().getBlock());
        WorldPosition to = Frequencies.teleporters().entrySet().stream()
                .filter(entry -> entry.getValue().formed() && entry.getValue().frequency().equals(frequency))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        World world = to == null ? null : PaperWorld.world(to);
        if (world == null) {
            player.sendActionBar(Component.text("No teleporter with a complete frame on " + frequency, NamedTextColor.RED));
            return;
        }
        long cost = Frequencies.cost(from, to);
        if (!GearItems.useEnergy(hand, cost)) {
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "The portable teleporter needs %,d J", cost), NamedTextColor.RED));
            return;
        }
        player.teleportAsync(new Location(world, to.position().x() + 0.5, to.position().y() + 1, to.position().z() + 0.5,
                player.getLocation().getYaw(), player.getLocation().getPitch()));
    }

    // -- cardboard box, seismic reader, QIO dashboard ----------------------------------------

    /** The QIO dashboard block opens its network (tools on it are asked first: their item behaviors). */
    private boolean openDashboard(MachineInteractions.Click click) {
        if (click.sneaking()) return false;
        Player player = click.player();
        String frequency = click.machine().context().get(AutomationMachines.FREQUENCY);
        if (frequency.isEmpty()) {
            player.sendActionBar(Component.text("No frequency: look at it and use /mekanized frequency <name>", NamedTextColor.RED));
            return true;
        }
        new Dashboard(frequency, 0).open(player);
        return true;
    }

    /** {@code mekanized:cardboard_box}: sneak-right-click a block to pack it, with its contents. */
    public static final class BoxBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<BoxBehavior> FACTORY = (pack, path, id, section) -> new BoxBehavior();

        @Override
        protected boolean use(Use use) {
            PaperAutomation automation = instance;
            if (use.block() == null || !use.sneaking() || automation == null) return false;
            automation.pack(use.player(), use.block(), use.item());
            return true;
        }
    }

    /** {@code mekanized:cardboard_box_storage}: right-click to place the packed block back. */
    public static final class FullBoxBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<FullBoxBehavior> FACTORY = (pack, path, id, section) -> new FullBoxBehavior();

        @Override
        protected boolean use(Use use) {
            PaperAutomation automation = instance;
            if (use.block() == null || use.face() == null || automation == null) return false;
            automation.unpack(use.player(), use.block().getRelative(use.face()), use.item());
            return true;
        }
    }

    /** {@code mekanized:seismic_reader}: right-click a running seismic vibrator to read the ground below. */
    public static final class SeismicReaderBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<SeismicReaderBehavior> FACTORY = (pack, path, id, section) -> new SeismicReaderBehavior();

        @Override
        protected boolean use(Use use) {
            MachineInstance machine = use.block() == null ? null : PaperWorld.machine(use.block());
            if (machine == null || !machine.inspect().type().equals(MekanizedContent.key("seismic_vibrator"))) return false;
            if (!machine.isActive()) {
                use.player().sendActionBar(Component.text("The seismic vibrator needs energy", NamedTextColor.RED));
                return true;
            }
            read(use.player(), use.block());
            return true;
        }
    }

    /** Puts the block (with its contents, or a spawner's mob) into the box. */
    private void pack(Player player, Block block, ItemStack box) {
        if (block.getType().isAir() || UNBOXABLE.contains(block.getType()) || block.getType().getHardness() < 0) return;
        if (CraftEngineBlocks.isCustomBlock(block)) {
            player.sendActionBar(Component.text("Machines keep their contents when broken: no need for a box", NamedTextColor.RED));
            return;
        }
        // Protection plugins decide as for breaking it.
        BlockBreakEvent check = new BlockBreakEvent(block, player);
        check.setDropItems(false);
        if (!check.callEvent()) return;
        ItemStack full = PaperItems.itemOf(MekanizedContent.key("cardboard_box_storage"));
        if (full == null) return;
        String data = block.getBlockData().getAsString();
        byte[] items = null;
        String spawned = null;
        if (block.getState(false) instanceof Container container) {
            ItemStack[] contents = container.getInventory().getContents();
            items = serialize(contents);
            container.getInventory().clear();
        } else if (block.getState(false) instanceof CreatureSpawner spawner && spawner.getSpawnedType() != null) {
            spawned = spawner.getSpawnedType().name();
        }
        byte[] finalItems = items;
        String finalSpawned = spawned;
        ItemData.setString(full, ItemData.Field.BOX_BLOCK, data);
        ItemData.setBytes(full, ItemData.Field.BOX_ITEMS, finalItems);
        ItemData.setString(full, ItemData.Field.BOX_SPAWNER, finalSpawned);
        block.setType(Material.AIR, false);
        box.setAmount(box.getAmount() - 1);
        PaperWorld.give(player, full);
    }

    /** Places the boxed block back where there is room, with what it held. */
    private void unpack(Player player, Block target, ItemStack box) {
        if (!target.getType().isAir() && !target.isReplaceable()) return;
        String data = ItemData.getString(box, ItemData.Field.BOX_BLOCK);
        if (data == null) return;
        try {
            target.setBlockData(Bukkit.createBlockData(data), false);
        } catch (IllegalArgumentException e) {
            return;
        }
        byte[] items = ItemData.getBytes(box, ItemData.Field.BOX_ITEMS);
        if (items != null && target.getState(false) instanceof Container container) {
            ItemStack[] contents = deserialize(items);
            container.getInventory().setContents(Arrays.copyOf(contents, container.getInventory().getSize()));
        }
        String spawned = ItemData.getString(box, ItemData.Field.BOX_SPAWNER);
        if (spawned != null && target.getState(false) instanceof CreatureSpawner spawner) {
            try {
                spawner.setSpawnedType(EntityType.valueOf(spawned));
                spawner.update(true, false);
            } catch (IllegalArgumentException ignored) {
                // a mob that no longer exists: an empty spawner
            }
        }
        box.setAmount(box.getAmount() - 1);
        ItemStack empty = PaperItems.itemOf(MekanizedContent.key("cardboard_box"));
        if (empty != null) PaperWorld.give(player, empty);
    }

    /** Slots as length-prefixed serialized items (an empty slot: length 0). */
    private static byte[] serialize(ItemStack[] contents) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                byte[] one = item == null || item.isEmpty() ? new byte[0] : item.serializeAsBytes();
                out.writeInt(one.length);
                out.write(one);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static ItemStack[] deserialize(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            ItemStack[] contents = new ItemStack[in.readInt()];
            for (int i = 0; i < contents.length; i++) {
                byte[] one = new byte[in.readInt()];
                in.readFully(one);
                contents[i] = one.length == 0 ? null : ItemStack.deserializeBytes(one);
            }
            return contents;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lists the uncommon blocks in the column under the vibrator, top to bottom. */
    private static void read(Player player, Block vibrator) {
        Map<String, List<Integer>> found = new LinkedHashMap<>();
        World world = vibrator.getWorld();
        for (int y = vibrator.getY() - 1; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(vibrator.getX(), y, vibrator.getZ());
            String name;
            if (CraftEngineBlocks.isCustomBlock(block)) {
                var state = CraftEngineBlocks.getCustomBlockState(block);
                name = state == null ? block.getType().getKey().getKey() : state.owner().value().id().value();
            } else {
                name = block.getType().getKey().getKey();
            }
            if (COMMON.contains(name)) continue;
            found.computeIfAbsent(name, key -> new ArrayList<>()).add(y);
        }
        player.sendMessage(Component.text("Seismic reading below " + vibrator.getX() + ", " + vibrator.getZ() + ":", NamedTextColor.GOLD));
        if (found.isEmpty()) player.sendMessage(Component.text("  only common rock", NamedTextColor.GRAY));
        found.forEach((name, levels) -> player.sendMessage(Component.text("  " + name.replace('_', ' '), NamedTextColor.YELLOW)
                .append(Component.text(" x" + levels.size() + " (y " + levels.getFirst() + (levels.size() > 1 ? " to " + levels.getLast() : "") + ")",
                        NamedTextColor.GRAY))));
    }

    // -- QIO dashboard ------------------------------------------------------------------------

    /** The dashboard menu of a frequency: 45 items a page, newest snapshot of its drive arrays. */
    private final class Dashboard implements InventoryHolder {
        private static final int PER_PAGE = 45;
        private final String frequency;
        private final int page;
        private final Inventory inventory;
        private final List<ResourceVariant> shown = new ArrayList<>();

        private Dashboard(String frequency, int page) {
            this.frequency = frequency;
            this.page = page;
            this.inventory = Bukkit.createInventory(this, 54, Component.text("QIO: " + frequency));
        }

        @Override
        public @NotNull Inventory getInventory() {
            return this.inventory;
        }

        void open(Player player) {
            render();
            player.openInventory(this.inventory);
        }

        void render() {
            Frequencies.Summary summary = Frequencies.summary(this.frequency);
            long stored = summary.stored();
            long capacity = summary.capacity();
            List<Map.Entry<ResourceVariant, Long>> pageEntries = summary.page(this.page, PER_PAGE);
            this.inventory.clear();
            this.shown.clear();
            for (int i = 0; i < pageEntries.size(); i++) {
                var entry = pageEntries.get(i);
                ItemStack icon = PaperItems.toItemStack(entry.getKey(), 1);
                if (icon == null) continue;
                long amount = entry.getValue();
                icon.setAmount((int) Math.max(1, Math.min(64, amount)));
                icon.editMeta(meta -> {
                    List<Component> lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.lore()) : List.of());
                    lore.add(Lore.line(String.format(Locale.ROOT, "Stored: %,d", amount), NamedTextColor.YELLOW));
                    lore.add(Lore.line("Left: a stack, right: one", NamedTextColor.DARK_GRAY));
                    meta.lore(lore);
                });
                this.inventory.setItem(i, icon);
                this.shown.add(entry.getKey());
            }
            this.inventory.setItem(45, button(Material.ARROW, "Previous page"));
            this.inventory.setItem(53, button(Material.ARROW, "Next page"));
            this.inventory.setItem(49, button(Material.ENDER_EYE, String.format(Locale.ROOT, "%,d / %,d items, page %d", stored, capacity, this.page + 1)));
        }

        private ItemStack button(Material material, String name) {
            ItemStack stack = new ItemStack(material);
            stack.editMeta(meta -> meta.displayName(Lore.line(name, NamedTextColor.WHITE)));
            return stack;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Dashboard) event.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Dashboard dashboard) || !(event.getWhoClicked() instanceof Player player)) return;
        boolean top = event.getClickedInventory() == event.getInventory();
        if (!top && !event.isShiftClick()) return; // moving things in one's own inventory
        event.setCancelled(true);
        if (!top) {
            ItemStack stack = event.getCurrentItem();
            if (stack == null || stack.isEmpty()) return;
            event.setCurrentItem(null);
            deposit(player, dashboard.frequency, stack);
            return;
        }
        int slot = event.getRawSlot();
        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.isEmpty()) {
            player.setItemOnCursor(null);
            deposit(player, dashboard.frequency, cursor);
        } else if (slot == 45 && dashboard.page > 0) {
            new Dashboard(dashboard.frequency, dashboard.page - 1).open(player);
        } else if (slot == 53) {
            new Dashboard(dashboard.frequency, dashboard.page + 1).open(player);
        } else if (slot < dashboard.shown.size()) {
            ResourceVariant variant = dashboard.shown.get(slot);
            ItemStack one = PaperItems.toItemStack(variant, 1);
            long wanted = event.isRightClick() ? 1 : one == null ? 64 : one.getMaxStackSize();
            withdraw(player, dashboard, variant, wanted);
        }
    }

    /** Puts {@code stack} into the frequency's drive arrays, on their threads; what does not fit comes back. */
    private void deposit(Player player, String frequency, ItemStack stack) {
        ResourceStack resource = PaperItems.toResource(stack);
        List<WorldPosition> arrays = Frequencies.arrays(frequency);
        depositInto(player, arrays, 0, resource.variant(), resource.amount());
    }

    private void depositInto(Player player, List<WorldPosition> arrays, int index, ResourceVariant variant, long amount) {
        if (amount <= 0) {
            refresh(player);
            return;
        }
        if (index >= arrays.size()) {
            PaperWorld.scheduler().run(player, () -> {
                for (ItemStack left : PaperItems.toItemStacks(new ResourceStack(variant, amount))) PaperWorld.give(player, left);
                player.sendActionBar(Component.text("The drive arrays are full", NamedTextColor.RED));
                refresh(player);
            }, null);
            return;
        }
        WorldPosition position = arrays.get(index);
        Location location = PaperWorld.location(position);
        if (location == null) {
            depositInto(player, arrays, index + 1, variant, amount);
            return;
        }
        PaperWorld.scheduler().run(location, () -> {
            long inserted = 0;
            MachineInstance array = PaperWorld.machine(position);
            if (array != null) {
                MachineContext context = array.context();
                try (Transaction transaction = context.transactions().openOuter()) {
                    inserted = AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"), variant, amount, transaction);
                    transaction.commit();
                }
                if (inserted > 0) array.wake(WakeReason.RESOURCE_INSERTED);
            }
            depositInto(player, arrays, index + 1, variant, amount - inserted);
        });
    }

    /** Takes up to {@code wanted} of {@code variant} from the arrays, on their threads, for the player. */
    private void withdraw(Player player, Dashboard dashboard, ResourceVariant variant, long wanted) {
        for (WorldPosition position : Frequencies.arrays(dashboard.frequency)) {
            Frequencies.Array array = Frequencies.arrays().get(position);
            Location location = PaperWorld.location(position);
            if (array == null || location == null || !array.contents().containsKey(variant)) continue;
            PaperWorld.scheduler().run(location, () -> {
                long taken = 0;
                MachineInstance machine = PaperWorld.machine(position);
                if (machine != null) {
                    MachineContext context = machine.context();
                    try (Transaction transaction = context.transactions().openOuter()) {
                        taken = context.storage("items").extract(variant, wanted, transaction);
                        transaction.commit();
                    }
                    if (taken > 0) machine.wake(WakeReason.RESOURCE_EXTRACTED);
                }
                long finalTaken = taken;
                PaperWorld.scheduler().run(player, () -> {
                    for (ItemStack stack : PaperItems.toItemStacks(new ResourceStack(variant, finalTaken))) PaperWorld.give(player, stack);
                    refresh(player);
                }, null);
            });
            return;
        }
    }

    /** Redraws the open dashboard once the arrays published their new contents. */
    private void refresh(Player player) {
        PaperWorld.scheduler().runLater(player, () -> {
            if (Nms.get().topInventory(player).getHolder(false) instanceof Dashboard dashboard) dashboard.render();
        }, null, 21);
    }

}
