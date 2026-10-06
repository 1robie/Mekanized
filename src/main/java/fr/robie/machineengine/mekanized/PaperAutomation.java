package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.mekanized.machine.AutomationMachines.Frequencies;
import fr.robie.machineengine.mekanized.machine.AutomationMachines;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.paper.PaperItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Paper side of Phase 6 wave 4: frequencies ({@code /mekanized frequency}), teleporting players
 * who walk into a teleporter's portal, the cardboard box, the seismic reader and the QIO dashboard
 * (a chest menu of everything the frequency's drive arrays hold).
 */
final class PaperAutomation implements Listener {
    private static final long TELEPORT_COOLDOWN_MS = 3_000;
    private static final NamespacedKey BOX_BLOCK = new NamespacedKey(MekanizedContent.NAMESPACE, "box_block");
    private static final NamespacedKey BOX_ITEMS = new NamespacedKey(MekanizedContent.NAMESPACE, "box_items");
    private static final NamespacedKey BOX_SPAWNER = new NamespacedKey(MekanizedContent.NAMESPACE, "box_spawner");
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
    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();

    private PaperAutomation(Plugin plugin) {
        this.plugin = plugin;
        this.teleporterFile = plugin.getDataFolder().toPath().resolve("teleporters.txt");
    }

    static void install(Plugin plugin) {
        PaperAutomation automation = new PaperAutomation(plugin);
        automation.loadTeleporters();
        Frequencies.onTeleportersChanged(automation::saveTeleporters);
        Bukkit.getPluginManager().registerEvents(automation, plugin);
    }

    // -- frequencies --------------------------------------------------------------------------

    /** Sets the frequency of the teleporter or QIO block {@code player} looks at (on the player's thread). */
    static void setFrequency(Plugin plugin, Player player, String frequency) {
        player.getScheduler().run(plugin, task -> {
            Block block = player.getTargetBlockExact(6);
            LoadedMachine machine = block == null ? null : PaperWorld.machine(block);
            ItemStack hand = player.getInventory().getItemInMainHand();
            String held = PaperGear.id(hand);
            if (("portable_teleporter".equals(held) || "portable_qio_dashboard".equals(held)) && (machine == null || machine.instance() == null)) {
                hand.editMeta(meta -> {
                    meta.getPersistentDataContainer().set(ITEM_FREQUENCY, PersistentDataType.STRING, frequency);
                    meta.lore(List.of(Component.text("Frequency: " + frequency, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
                });
                player.sendMessage(Component.text("Frequency of the item set to ", NamedTextColor.GRAY).append(Component.text(frequency, NamedTextColor.YELLOW)));
                return;
            }
            if (machine == null || machine.instance() == null
                    || machine.instance().type().data().stream().noneMatch(key -> key.id().equals(AutomationMachines.FREQUENCY.id()))) {
                player.sendMessage(Component.text("Look at a teleporter, a QIO block or a quantum entangloporter (or hold a portable teleporter or dashboard)", NamedTextColor.RED));
                return;
            }
            machine.instance().context().set(AutomationMachines.FREQUENCY, frequency);
            machine.instance().wake(WakeReason.CONFIG_CHANGED);
            player.sendMessage(Component.text("Frequency set to ", NamedTextColor.GRAY).append(Component.text(frequency, NamedTextColor.YELLOW)));
        }, null);
    }

    // -- teleporters --------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        Long last = this.lastTeleport.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < TELEPORT_COOLDOWN_MS) return;
        Block feet = event.getTo().getBlock();
        for (Block below : new Block[]{feet.getRelative(BlockFace.DOWN), feet.getRelative(BlockFace.DOWN, 2)}) {
            WorldPosition from = PaperWorld.position(below);
            Frequencies.Teleporter teleporter = Frequencies.teleporters().get(from);
            if (teleporter == null || !teleporter.formed()) continue;
            WorldPosition to = Frequencies.destination(from, teleporter.frequency());
            if (to == null) {
                player.sendActionBar(Component.text("No other teleporter on " + teleporter.frequency(), NamedTextColor.RED));
                this.lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
                return;
            }
            World world = PaperWorld.world(to);
            LoadedMachine source = PaperWorld.machine(below);
            if (world == null || source == null || source.instance() == null) return;
            long cost = cost(from, to);
            MachineContext context = source.instance().context();
            try (Transaction transaction = context.transactions().openOuter()) {
                if (context.storage("energy").extract(MekanizedContent.ENERGY.variant(), cost, transaction) != cost) {
                    player.sendActionBar(Component.text(String.format(Locale.ROOT, "The teleporter needs %,d J", cost), NamedTextColor.RED));
                    this.lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
                    return;
                }
                transaction.commit();
            }
            this.lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
            Location target = new Location(world, to.position().x() + 0.5, to.position().y() + 1, to.position().z() + 0.5,
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            player.teleportAsync(target);
            return;
        }
    }

    /** Mekanism's cost: 1 kJ, plus 10 J per block; 100 kJ to another world. */
    static long cost(WorldPosition from, WorldPosition to) {
        if (!from.world().equals(to.world())) return 100_000;
        double dx = from.position().x() - to.position().x(), dy = from.position().y() - to.position().y(), dz = from.position().z() - to.position().z();
        return 1_000 + (long) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 10);
    }

    private void loadTeleporters() {
        if (!Files.isRegularFile(this.teleporterFile)) return;
        try {
            for (String line : Files.readAllLines(this.teleporterFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|");
                if (parts.length != 6) continue;
                WorldPosition position = WorldPosition.of(Key.key(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
                Frequencies.teleporters().put(position, new Frequencies.Teleporter(parts[0], Boolean.parseBoolean(parts[5])));
            }
        } catch (IOException | RuntimeException e) {
            this.plugin.getLogger().warning("Could not read " + this.teleporterFile + ": " + e.getMessage());
        }
    }

    private synchronized void saveTeleporters() {
        StringBuilder text = new StringBuilder();
        Frequencies.teleporters().forEach((position, teleporter) -> text.append(teleporter.frequency().replace("|", "")).append('|')
                .append(position.world().asString()).append('|').append(position.position().x()).append('|').append(position.position().y())
                .append('|').append(position.position().z()).append('|').append(teleporter.formed()).append('\n'));
        PaperFiles.write(this.plugin, this.teleporterFile, text);
    }

    // -- portable teleporter, portable QIO dashboard ------------------------------------------

    /** The frequency of a portable teleporter or dashboard. */
    static final NamespacedKey ITEM_FREQUENCY = new NamespacedKey(MekanizedContent.NAMESPACE, "frequency");

    @EventHandler(priority = EventPriority.LOW)
    public void onPortable(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = PaperGear.id(hand);
        if (!"portable_teleporter".equals(id) && !"portable_qio_dashboard".equals(id)) return;
        event.setCancelled(true);
        String frequency = hand.getItemMeta().getPersistentDataContainer().get(ITEM_FREQUENCY, PersistentDataType.STRING);
        if (frequency == null || frequency.isEmpty()) {
            player.sendActionBar(Component.text("No frequency: hold it and use /mekanized frequency <name>", NamedTextColor.RED));
            return;
        }
        if ("portable_qio_dashboard".equals(id)) {
            new Dashboard(frequency, 0).open(player);
            return;
        }
        Long last = this.lastTeleport.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < TELEPORT_COOLDOWN_MS) return;
        this.lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
        WorldPosition from = PaperWorld.position(player.getLocation().getBlock());
        WorldPosition to = Frequencies.teleporters().entrySet().stream()
                .filter(entry -> entry.getValue().formed() && entry.getValue().frequency().equals(frequency))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        World world = to == null ? null : PaperWorld.world(to);
        if (world == null) {
            player.sendActionBar(Component.text("No teleporter with a complete frame on " + frequency, NamedTextColor.RED));
            return;
        }
        long cost = cost(from, to);
        if (!PaperGear.useEnergy(hand, cost)) {
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "The portable teleporter needs %,d J", cost), NamedTextColor.RED));
            return;
        }
        player.teleportAsync(new Location(world, to.position().x() + 0.5, to.position().y() + 1, to.position().z() + 0.5,
                player.getLocation().getYaw(), player.getLocation().getPitch()));
    }

    // -- cardboard box, seismic reader, QIO dashboard ----------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        Key tool = hand == null || hand.isEmpty() ? null : PaperItems.idOf(hand);
        if (MekanizedContent.key("cardboard_box").equals(tool) && player.isSneaking()) {
            event.setCancelled(true);
            pack(player, block, hand);
        } else if (MekanizedContent.key("cardboard_box_storage").equals(tool)) {
            event.setCancelled(true);
            unpack(player, block.getRelative(event.getBlockFace()), hand);
        } else if (MekanizedContent.key("seismic_reader").equals(tool)) {
            LoadedMachine machine = PaperWorld.machine(block);
            if (machine == null || machine.instance() == null || !machine.inspect().type().equals(MekanizedContent.key("seismic_vibrator"))) return;
            event.setCancelled(true);
            if (!machine.instance().isActive()) {
                player.sendActionBar(Component.text("The seismic vibrator needs energy", NamedTextColor.RED));
                return;
            }
            read(player, block);
        } else if (!player.isSneaking()) {
            LoadedMachine machine = PaperWorld.machine(block);
            if (machine == null || machine.instance() == null || !machine.inspect().type().equals(MekanizedContent.key("qio_dashboard"))) return;
            event.setCancelled(true);
            String frequency = machine.instance().context().get(AutomationMachines.FREQUENCY);
            if (frequency.isEmpty()) {
                player.sendActionBar(Component.text("No frequency: look at it and use /mekanized frequency <name>", NamedTextColor.RED));
                return;
            }
            new Dashboard(frequency, 0).open(player);
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
        full.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(BOX_BLOCK, PersistentDataType.STRING, data);
            if (finalItems != null) pdc.set(BOX_ITEMS, PersistentDataType.BYTE_ARRAY, finalItems);
            if (finalSpawned != null) pdc.set(BOX_SPAWNER, PersistentDataType.STRING, finalSpawned);
            meta.lore(List.of(Component.text("Contains: " + block.getType().getKey().getKey().replace('_', ' '), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        });
        block.setType(Material.AIR, false);
        box.setAmount(box.getAmount() - 1);
        PaperWorld.give(player, full);
    }

    /** Places the boxed block back where there is room, with what it held. */
    private void unpack(Player player, Block target, ItemStack box) {
        if (!target.getType().isAir() && !target.isReplaceable()) return;
        PersistentDataContainer pdc = box.getItemMeta().getPersistentDataContainer();
        String data = pdc.get(BOX_BLOCK, PersistentDataType.STRING);
        if (data == null) return;
        try {
            target.setBlockData(Bukkit.createBlockData(data), false);
        } catch (IllegalArgumentException e) {
            return;
        }
        byte[] items = pdc.get(BOX_ITEMS, PersistentDataType.BYTE_ARRAY);
        if (items != null && target.getState(false) instanceof Container container) {
            ItemStack[] contents = deserialize(items);
            container.getInventory().setContents(Arrays.copyOf(contents, container.getInventory().getSize()));
        }
        String spawned = pdc.get(BOX_SPAWNER, PersistentDataType.STRING);
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
            Map<ResourceVariant, Long> all = new HashMap<>();
            long stored = 0;
            long capacity = 0;
            for (WorldPosition position : Frequencies.arrays(this.frequency)) {
                Frequencies.Array array = Frequencies.arrays().get(position);
                if (array == null) continue;
                array.contents().forEach((variant, amount) -> all.merge(variant, amount, Long::sum));
                stored += array.stored();
                capacity += array.capacity();
            }
            List<Map.Entry<ResourceVariant, Long>> sorted = new ArrayList<>(all.entrySet());
            sorted.sort(Map.Entry.<ResourceVariant, Long>comparingByValue(Comparator.reverseOrder()));
            this.inventory.clear();
            this.shown.clear();
            for (int i = 0; i < PER_PAGE && this.page * PER_PAGE + i < sorted.size(); i++) {
                var entry = sorted.get(this.page * PER_PAGE + i);
                ItemStack icon = PaperItems.toItemStack(entry.getKey(), 1);
                if (icon == null) continue;
                long amount = entry.getValue();
                icon.setAmount((int) Math.max(1, Math.min(64, amount)));
                icon.editMeta(meta -> {
                    List<Component> lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.lore()) : List.of());
                    lore.add(Component.text(String.format(Locale.ROOT, "Stored: %,d", amount), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Left: a stack, right: one", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
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
            stack.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
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
            player.getScheduler().run(this.plugin, task -> {
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
        Bukkit.getRegionScheduler().run(this.plugin, location, task -> {
            long inserted = 0;
            LoadedMachine array = PaperWorld.machine(position);
            if (array != null && array.instance() != null) {
                MachineContext context = array.instance().context();
                try (Transaction transaction = context.transactions().openOuter()) {
                    inserted = AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"), variant, amount, transaction);
                    transaction.commit();
                }
                if (inserted > 0) array.instance().wake(WakeReason.RESOURCE_INSERTED);
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
            Bukkit.getRegionScheduler().run(this.plugin, location, task -> {
                long taken = 0;
                LoadedMachine machine = PaperWorld.machine(position);
                if (machine != null && machine.instance() != null) {
                    MachineContext context = machine.instance().context();
                    try (Transaction transaction = context.transactions().openOuter()) {
                        taken = context.storage("items").extract(variant, wanted, transaction);
                        transaction.commit();
                    }
                    if (taken > 0) machine.instance().wake(WakeReason.RESOURCE_EXTRACTED);
                }
                long finalTaken = taken;
                player.getScheduler().run(this.plugin, give -> {
                    for (ItemStack stack : PaperItems.toItemStacks(new ResourceStack(variant, finalTaken))) PaperWorld.give(player, stack);
                    refresh(player);
                }, null);
            });
            return;
        }
    }

    /** Redraws the open dashboard once the arrays published their new contents. */
    private void refresh(Player player) {
        player.getScheduler().runDelayed(this.plugin, task -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Dashboard dashboard) dashboard.render();
        }, null, 21);
    }

}
