package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.Liquifier;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.chemical;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.energy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.id;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.modules;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useChemical;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useEnergy;

/**
 * The gear used by hand: the atomic disassembler and Meka-Tool (vein mining, blasting, farming, shearing,
 * teleporting), the electric bow, the flamethrower and the canteen. Right-clicks are CraftEngine item
 * behaviors (D32); breaking, hitting and shooting are still events. Their energy, chemical and modules
 * are in {@link GearItems}.
 */
public final class GearTools implements Listener {
    /** Blocks being broken by vein mining (their own break events must not start another vein). */
    private final Set<Block> veining = ConcurrentHashMap.newKeySet();

    private GearTools() {
    }

    public static void install(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new GearTools(), plugin);
    }

    // -- tools ----------------------------------------------------------------------------------

    /**
     * Every block costs a mining tool ({@code mekanized:mining_tool}) energy; sneaking, whole veins of ore
     * and logs (its {@code vein-limit}, {@code vein-limit-with-unit} with the vein mining unit). This stays an
     * event: each extra block must fire its own break event for protection plugins (CraftEngine's
     * {@code onBreakBlock} cannot do that, nor stop the break).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (this.veining.contains(event.getBlock())) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        GearBehaviors.MiningTool mining = GearBehaviors.options(tool, GearBehaviors.MiningTool.class);
        if (mining == null) return;
        if (!useEnergy(tool, mining.energyPerBlock())) return;
        boolean vein = player.isSneaking();
        Set<String> modules = modules(tool);
        int limit = modules.contains("vein_mining_unit") ? mining.veinLimitWithUnit() : mining.veinLimit();
        Block start = event.getBlock();
        if (!vein && modules.contains("blasting_unit")) {
            blast(player, tool, start, mining.energyPerBlock());
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
                if (!PaperWorld.scheduler().isOwnedByCurrentThread(next.getLocation())) continue;
                if (next.getType() != start.getType() || !seen.add(next)) continue;
                if (!useEnergy(tool, mining.energyPerBlock())) return;
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
    private void blast(Player player, ItemStack tool, Block start, long energyPerBlock) {
        Vector look = player.getEyeLocation().getDirection();
        double ax = Math.abs(look.getX()), ay = Math.abs(look.getY()), az = Math.abs(look.getZ());
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                Block next = ay >= ax && ay >= az ? start.getRelative(a, 0, b) : ax >= az ? start.getRelative(0, a, b) : start.getRelative(a, b, 0);
                if (!PaperWorld.scheduler().isOwnedByCurrentThread(next.getLocation())) continue;
                if (next.getType().isAir() || next.getType().getHardness() < 0 || next.isLiquid()) continue;
                if (CraftEngineBlocks.isCustomBlock(next)) continue;
                if (!useEnergy(tool, energyPerBlock)) return;
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
        GearBehaviors.MiningTool mining = GearBehaviors.options(tool, GearBehaviors.MiningTool.class);
        long cost = mining == null ? 0 : mining.energyPerBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = center.getRelative(dx, 0, dz);
                boolean soil = block.getType() == Material.DIRT || block.getType() == Material.GRASS_BLOCK || block.getType() == Material.DIRT_PATH;
                if (!soil || !block.getRelative(BlockFace.UP).getType().isAir()
                        || !useEnergy(tool, cost)) continue;
                block.setType(Material.FARMLAND);
            }
        }
        player.getWorld().playSound(center.getLocation(), org.bukkit.Sound.ITEM_HOE_TILL, 1f, 1f);
    }

    /**
     * A charged mining tool hits for its {@code damage} at least ({@code amplified-damage} with the attack
     * amplification unit), for {@code hit-energy}. An event: CraftEngine's item behaviors have no attack hook.
     */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        GearBehaviors.MiningTool mining = GearBehaviors.options(tool, GearBehaviors.MiningTool.class);
        if (mining == null || !useEnergy(tool, mining.hitEnergy())) return;
        event.setDamage(Math.max(event.getDamage(), modules(tool).contains("attack_amplification_unit")
                ? mining.amplifiedDamage() : mining.damage()));
    }

    /**
     * A charged electric bow ({@code mekanized:electric_bow}) does not use arrows ({@code energy-per-shot} a
     * shot): an arrow is still needed to shoot. An event: item behaviors have no shoot hook.
     */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        GearBehaviors.ElectricBow electric = GearBehaviors.options(bow, GearBehaviors.ElectricBow.class);
        if (electric == null || !useEnergy(bow, electric.energyPerShot())) return;
        event.setConsumeItem(false);
        if (event.getProjectile() instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        }
        if (event.getEntity() instanceof Player player) PaperWorld.scheduler().run(player, () -> player.updateInventory(), null);
    }

    /** {@code mekanized:flamethrower}: a burst of fire in front, for {@code hydrogen-per-use} (5 mB), doing {@code damage} (2.0). */
    public static final class FlamethrowerBehavior extends PaperItemBehavior {
        public static final ItemBehaviorFactory<FlamethrowerBehavior> FACTORY = (pack, path, id, section) ->
                new FlamethrowerBehavior(section.getLong("hydrogen-per-use", 5), section.getDouble("damage", 2));

        private final long hydrogen;
        private final double damage;

        private FlamethrowerBehavior(long hydrogen, double damage) {
            this.hydrogen = hydrogen;
            this.damage = damage;
        }

        @Override
        protected boolean use(Use use) {
            flame(use.player(), use.item(), this.hydrogen, this.damage);
            return true;
        }
    }

    /** {@code mekanized:canteen}: drinks nutritional paste. */
    public static final class CanteenBehavior extends PaperItemBehavior {
        public static final ItemBehaviorFactory<CanteenBehavior> FACTORY = (pack, path, id, section) -> new CanteenBehavior();

        @Override
        protected boolean use(Use use) {
            drink(use.player(), use.item());
            return true;
        }
    }

    /**
     * {@code mekanized:meka_tool}: the units used by right-click: teleportation (in the air), farming
     * (on the top of a block), shearing (on an animal).
     */
    public static final class MekaToolBehavior extends PaperItemBehavior {
        public static final ItemBehaviorFactory<MekaToolBehavior> FACTORY = (pack, path, id, section) ->
                new MekaToolBehavior(section.getInt("teleport-range", 100), section.getLong("shear-energy", 500));

        private final int teleportRange;
        private final long shearEnergy;

        private MekaToolBehavior(int teleportRange, long shearEnergy) {
            this.teleportRange = teleportRange;
            this.shearEnergy = shearEnergy;
        }

        @Override
        protected boolean use(Use use) {
            Set<String> modules = modules(use.item());
            if (!use.onBlock() && modules.contains("teleportation_unit")) {
                teleport(use.player(), use.item(), this.teleportRange);
                return true;
            }
            if (use.onBlock() && use.face() == BlockFace.UP && modules.contains("farming_unit")) {
                till(use.player(), use.item(), use.block());
                return true;
            }
            return false;
        }

        /** Shearing unit: a sheep, mooshroom or snow golem ({@code shear-energy}). */
        @Override
        protected boolean useOnEntity(Player player, ItemStack tool, Entity target) {
            if (!modules(tool).contains("shearing_unit")) return false;
            if (!(target instanceof Shearable shearable) || !shearable.readyToBeSheared()) return false;
            if (!useEnergy(tool, this.shearEnergy)) return false;
            shearable.shear(net.kyori.adventure.sound.Sound.Source.PLAYER);
            return true;
        }
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

    /** Sets the mobs in front on fire, for {@code hydrogen} mB a burst. */
    private static void flame(Player player, ItemStack thrower, long hydrogen, double damage) {
        if (!useChemical(thrower, hydrogen)) {
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
            living.damage(damage, player);
        }
    }

    /** The teleportation unit: to the block looked at, up to {@code range} blocks (the teleporter's cost: 1 kJ plus 10 J a block by default). */
    private static void teleport(Player player, ItemStack tool, int range) {
        Block target = targetBlock(player, range);
        if (target == null) return;
        Location destination = target.getLocation().add(0.5, 1, 0.5);
        if (!PaperWorld.scheduler().isOwnedByCurrentThread(destination.clone().add(0, 1, 0))) return;
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
            if (!PaperWorld.scheduler().isOwnedByCurrentThread(at)) return null;
            Block block = at.getBlock();
            if (!block.isPassable()) return block;
        }
        return null;
    }
}
