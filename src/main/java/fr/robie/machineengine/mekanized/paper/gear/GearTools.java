package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.Liquifier;
import fr.robie.machineengine.mekanized.paper.ItemLongs;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import io.papermc.paper.entity.Shearable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.CHEMICAL;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.CHEMICALS;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.capacity;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.chemical;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.energy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.id;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.modules;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.refresh;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useChemical;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useEnergy;

/**
 * The gear used by hand: the atomic disassembler and Meka-Tool (vein mining, blasting, farming, shearing,
 * teleporting), the electric bow, the flamethrower and the canteen, filling gear from machines, and the
 * network reader. Their energy, chemical and modules are in {@link GearItems}.
 */
public final class GearTools implements Listener {
    private final Plugin plugin;
    /** Blocks being broken by vein mining (their own break events must not start another vein). */
    private final Set<Block> veining = ConcurrentHashMap.newKeySet();

    private GearTools(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void install(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new GearTools(plugin), plugin);
    }

    // -- tools ----------------------------------------------------------------------------------

    /** Every block costs the tool energy; sneaking, whole veins of ore and logs (64 blocks, 256 with the vein mining unit). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (this.veining.contains(event.getBlock())) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String id = id(tool);
        if (!"atomic_disassembler".equals(id) && !"meka_tool".equals(id)) return;
        if (!useEnergy(tool, 100)) return;
        boolean vein = player.isSneaking();
        int limit = "meka_tool".equals(id) && modules(tool).contains("vein_mining_unit") ? 256 : 64;
        Block start = event.getBlock();
        if (!vein && "meka_tool".equals(id) && modules(tool).contains("blasting_unit")) {
            blast(player, tool, start);
            return;
        }
        boolean veinable = start.getType().getKey().getKey().endsWith("_ore") || Tag.LOGS.isTagged(start.getType());
        if (!vein || !veinable) return;
        Set<Block> seen = new HashSet<>(Set.of(start));
        ArrayDeque<Block> queue = new ArrayDeque<>(List.of(start));
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            Block at = queue.poll();
            for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Block next = at.getRelative(face);
                // Folia: blocks of another region are not this thread's to read or break.
                if (!Bukkit.isOwnedByCurrentRegion(next)) continue;
                if (next.getType() != start.getType() || !seen.add(next)) continue;
                if (!useEnergy(tool, 100)) return;
                // Protection plugins decide for each block, as for breaking it by hand.
                this.veining.add(next);
                try {
                    if (new BlockBreakEvent(next, player).callEvent()) {
                        next.breakNaturally(tool);
                        broken++;
                        queue.add(next);
                    }
                } finally {
                    this.veining.remove(next);
                }
            }
        }
    }

    /** Blasting unit: the 3 x 3 square around the block, across the way the player looks (100 J a block). */
    private void blast(Player player, ItemStack tool, Block start) {
        Vector look = player.getEyeLocation().getDirection();
        double ax = Math.abs(look.getX()), ay = Math.abs(look.getY()), az = Math.abs(look.getZ());
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                Block next = ay >= ax && ay >= az ? start.getRelative(a, 0, b) : ax >= az ? start.getRelative(0, a, b) : start.getRelative(a, b, 0);
                if (!Bukkit.isOwnedByCurrentRegion(next)) continue;
                if (next.getType().isAir() || next.getType().getHardness() < 0 || next.isLiquid()) continue;
                if (CraftEngineBlocks.isCustomBlock(next)) continue;
                if (!useEnergy(tool, 100)) return;
                this.veining.add(next);
                try {
                    if (new BlockBreakEvent(next, player).callEvent()) next.breakNaturally(tool);
                } finally {
                    this.veining.remove(next);
                }
            }
        }
    }

    /** Farming unit: dirt and grass around the clicked block become farmland (100 J a block). */
    private static void till(Player player, ItemStack tool, Block center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                boolean soil = block.getType() == Material.DIRT || block.getType() == Material.GRASS_BLOCK || block.getType() == Material.DIRT_PATH;
                if (!soil || !block.getRelative(BlockFace.UP).getType().isAir() || !useEnergy(tool, 100)) continue;
                block.setType(Material.FARMLAND);
            }
        }
        player.getWorld().playSound(center.getLocation(), org.bukkit.Sound.ITEM_HOE_TILL, 1f, 1f);
    }

    /** Shearing unit: right-click a sheep, mooshroom or snow golem to shear it (500 J). */
    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!"meka_tool".equals(id(tool)) || !modules(tool).contains("shearing_unit")) return;
        if (!(event.getRightClicked() instanceof Shearable shearable) || !shearable.readyToBeSheared()) return;
        if (!useEnergy(tool, 500)) return;
        event.setCancelled(true);
        shearable.shear(net.kyori.adventure.sound.Sound.Source.PLAYER);
    }

    /** Charged: the disassembler hits for 20 (2 kJ), the Meka-Tool for 10, 30 with attack amplification (3 kJ). */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        String id = id(tool);
        if ("atomic_disassembler".equals(id) && useEnergy(tool, 2_000)) {
            event.setDamage(Math.max(event.getDamage(), 20));
        } else if ("meka_tool".equals(id) && useEnergy(tool, 3_000)) {
            event.setDamage(Math.max(event.getDamage(), modules(tool).contains("attack_amplification_unit") ? 30 : 10));
        }
    }

    /** A charged electric bow does not use arrows (120 J a shot): an arrow is still needed to shoot. */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (!"electric_bow".equals(id(bow)) || !useEnergy(bow, 120)) return;
        event.setConsumeItem(false);
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        }
        if (event.getEntity() instanceof Player player) player.getScheduler().run(this.plugin, task -> player.updateInventory(), null);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = id(hand);
        if (id == null) return;
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!right) return;
        if (id.startsWith("module_") && player.isSneaking()) {
            event.setCancelled(true);
            GearModules.install(player, hand, id.substring("module_".length()));
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null && !modules(hand).isEmpty() && GearModules.isStation(block)) {
            event.setCancelled(true);
            GearModules.uninstall(player, hand, id);
            return;
        }
        if (block != null && CHEMICALS.containsKey(id) && fill(player, hand, id, block)) {
            event.setCancelled(true);
            return;
        }
        switch (id) {
            case "flamethrower" -> {
                event.setCancelled(true);
                flame(player, hand);
            }
            case "canteen" -> {
                event.setCancelled(true);
                drink(player, hand);
            }
            case "network_reader" -> {
                if (block == null) return;
                event.setCancelled(true);
                read(player, block);
            }
            case "meka_tool" -> {
                if (event.getAction() == Action.RIGHT_CLICK_AIR && modules(hand).contains("teleportation_unit")) teleport(player, hand);
                if (block != null && event.getBlockFace() == BlockFace.UP && modules(hand).contains("farming_unit")) {
                    event.setCancelled(true);
                    till(player, hand, block);
                }
            }
            default -> {
            }
        }
    }


    /** Fills the gear's chemical from a machine holding it. */
    private boolean fill(Player player, ItemStack gear, String id, Block block) {
        LoadedMachine machine = PaperWorld.machine(block);
        if (machine == null || machine.instance() == null) return false;
        String chemical = (String) CHEMICALS.get(id)[0];
        long room = (long) CHEMICALS.get(id)[1] - chemical(gear);
        ResourceType type = MekanizedContent.resourceType(chemical);
        if (type == null || room <= 0) return false;
        var context = machine.instance().context();
        for (String storage : machine.inspect().storages().keySet()) {
            boolean holds = machine.inspect().storages().get(storage).stream()
                    .anyMatch(view -> !view.isEmpty() && view.variant().type() == type);
            if (!holds) continue;
            long taken;
            try (Transaction transaction = context.transactions().openOuter()) {
                taken = context.storage(storage).extract(type.variant(), room, transaction);
                transaction.commit();
            }
            if (taken <= 0) continue;
            long total = chemical(gear) + taken;
            gear.editMeta(meta -> {
                ItemLongs.set(meta, CHEMICAL, total);
                refresh(meta, MekanizedContent.key(id));
            });
            machine.instance().wake(WakeReason.RESOURCE_EXTRACTED);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Filled %,d mB of %s", taken,
                    MekanizedContent.displayName(chemical)), NamedTextColor.GREEN));
            return true;
        }
        return false;
    }

    /** Drinks nutritional paste: 50 mB per hunger point, up to a full bar. */
    private static void drink(Player player, ItemStack canteen) {
        int points = (int) Math.min(20 - player.getFoodLevel(), chemical(canteen) / Liquifier.PASTE_PER_FOOD);
        if (player.getFoodLevel() >= 20) {
            player.sendActionBar(Component.text("You are not hungry", NamedTextColor.GRAY));
            return;
        }
        if (points <= 0 || !useChemical(canteen, points * Liquifier.PASTE_PER_FOOD)) {
            player.sendActionBar(Component.text("Empty: fill it at a nutritional liquifier", NamedTextColor.RED));
            return;
        }
        player.setFoodLevel(player.getFoodLevel() + points);
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + points));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
    }

    /** Sets the mobs in front on fire (5 mB of hydrogen a burst). */
    private static void flame(Player player, ItemStack thrower) {
        if (!useChemical(thrower, 5)) {
            player.sendActionBar(Component.text("Out of hydrogen", NamedTextColor.RED));
            return;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        for (int i = 1; i <= 8; i++) {
            player.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(direction.clone().multiply(i)), 4, 0.15, 0.15, 0.15, 0.02);
        }
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!(entity instanceof LivingEntity living) || entity == player) continue;
            Vector to = living.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
            if (to.length() > 8 || to.normalize().dot(direction) < 0.9) continue;
            living.setFireTicks(Math.max(living.getFireTicks(), 100));
            living.damage(2, player);
        }
    }

    /** What a machine holds, in chat. */
    private static void read(Player player, Block block) {
        LoadedMachine machine = PaperWorld.machine(block);
        if (machine == null) {
            player.sendMessage(Component.text("Not a machine", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text(machine.inspect().type().value().replace('_', ' ') + ":", NamedTextColor.GOLD));
        machine.inspect().storages().forEach((storage, views) -> {
            long amount = views.stream().mapToLong(StorageView::amount).sum();
            long capacity = views.stream().mapToLong(StorageView::capacity).sum();
            String held = views.stream().filter(view -> !view.isEmpty()).map(view -> view.variant().type().key().value()).distinct()
                    .reduce((a, b) -> a + ", " + b).orElse("empty");
            player.sendMessage(Component.text(String.format(Locale.ROOT, "  %s: %,d / %,d (%s)", storage, amount, capacity, held),
                    NamedTextColor.GRAY));
        });
    }

    /** The teleportation unit: to the block looked at, up to 100 blocks (1 kJ plus 10 J a block). */
    private static void teleport(Player player, ItemStack tool) {
        Block target = targetBlock(player, 100);
        if (target == null) return;
        Location destination = target.getLocation().add(0.5, 1, 0.5);
        if (!Bukkit.isOwnedByCurrentRegion(destination.clone().add(0, 1, 0))) return;
        if (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()) return;
        long cost = Frequencies.cost(player.getLocation().distance(destination));
        if (!useEnergy(tool, cost)) {
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Needs %,d J", cost), NamedTextColor.RED));
            return;
        }
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        player.teleportAsync(destination);
    }

    /**
     * The first solid block the player looks at within {@code range}, stopping where the player's
     * region ends (Folia: other regions' blocks are not readable from here).
     */
    private static @Nullable Block targetBlock(Player player, int range) {
        Location eye = player.getEyeLocation();
        Vector step = eye.getDirection().multiply(0.25);
        Location at = eye.clone();
        for (int i = 0; i < range * 4; i++) {
            at.add(step);
            if (!Bukkit.isOwnedByCurrentRegion(at)) return null;
            Block block = at.getBlock();
            if (!block.isPassable()) return block;
        }
        return null;
    }
}
