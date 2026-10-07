package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.platform.Nms;
import fr.robie.machineengine.api.platform.PlayerInput;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.Material;
import fr.robie.machineengine.api.paper.PaperItems;
import static fr.robie.machineengine.mekanized.paper.gear.GearBehaviors.moduleEnergy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.addEnergy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.capacity;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.energy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.modules;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useChemical;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useEnergy;

/**
 * The per-player work of the suits and packs: jetpack, flight, step height, the MekaSuit module effects,
 * the magnetic attraction unit and the free runners, run for each player by their own scheduler.
 */
public final class SuitTicker implements Listener {
    private static final Set<PotionEffectType> HARMFUL = Set.of(PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.HUNGER,
            PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.WEAKNESS, PotionEffectType.DARKNESS);

    private final Plugin plugin;
    /** Players this plugin let fly (gravitational modulating unit). */
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();

    /** Players whose step height the servos raised. */
    private final Set<UUID> stepping = ConcurrentHashMap.newKeySet();

    private SuitTicker(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void install(Plugin plugin) {
        SuitTicker ticker = new SuitTicker(plugin);
        Bukkit.getPluginManager().registerEvents(ticker, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) ticker.start(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    private void start(Player player) {
        long[] tick = {0};
        player.getScheduler().runAtFixedRate(this.plugin, task -> {
            if (!player.isOnline()) return;
            jetpack(player);
            if (++tick[0] % 5 == 0) magnet(player);
            if (tick[0] % 20 == 0) everySecond(player);
        }, null, 1, 1);
    }

    /** Magnetic attraction unit: items within its {@code range} fly to the player ({@code energy} each, every 5 ticks). */
    private static void magnet(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (!modules(boots).contains("magnetic_attraction_unit")) return;
        ModuleHolderBehavior.Module unit = GearBehaviors.module("magnetic_attraction_unit");
        if (unit == null || unit.range() <= 0) return;
        Location at = player.getLocation().add(0, 0.5, 0);
        for (Item item : player.getWorld().getNearbyEntitiesByType(Item.class, at, unit.range())) {
            if (item.getPickupDelay() > 0 || !useEnergy(boots, unit.energy())) continue;
            item.setVelocity(at.toVector().subtract(item.getLocation().toVector()).normalize().multiply(0.6));
        }
    }

    /** A worn jetpack ({@code mekanized:jetpack}): up while jumping, from 0 when falling, for hydrogen each tick. */
    private void jetpack(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        GearBehaviors.Jetpack pack = GearBehaviors.options(chest, GearBehaviors.Jetpack.class);
        if (pack == null || pack.requiresModule() != null && !modules(chest).contains(pack.requiresModule())) return;
        if (player.isOnGround() || player.isFlying() || !jumping(player)) return;
        if (!useChemical(chest, pack.hydrogenPerTick())) return;
        // The server does not know the player's own horizontal speed: steer with the movement keys.
        Vector velocity = steering(player, pack);
        // A fall is cancelled at once: thrusting from a negative speed would only slow it down.
        velocity.setY(Math.min(Math.max(player.getVelocity().getY(), 0) + pack.thrust(), pack.maxSpeed()));
        player.setVelocity(velocity);
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 0.6, 0), 2, 0.15, 0.05, 0.15, 0.01);
    }

    /** Horizontal speed from the movement keys held, along where the player looks (1.21.2+; before, the current drift). */
    private static Vector steering(Player player, GearBehaviors.Jetpack pack) {
        PlayerInput input = Nms.get().input(player);
        if (input == null) {
            Vector velocity = player.getVelocity();
            return new Vector(velocity.getX(), 0, velocity.getZ());
        }
        double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        double left = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        double speed = input.sprint() ? pack.sprintSpeed() : pack.walkSpeed();
        double yaw = Math.toRadians(player.getLocation().getYaw());
        Vector move = new Vector(-Math.sin(yaw) * forward + Math.cos(yaw) * left, 0, Math.cos(yaw) * forward + Math.sin(yaw) * left);
        return move.lengthSquared() == 0 ? move : move.normalize().multiply(speed);
    }

    /** Whether the player holds jump (1.21.2+; older servers: never, the jetpack needs it). */
    private static boolean jumping(Player player) {
        PlayerInput input = Nms.get().input(player);
        return input != null && input.jump();
    }

    /** The MekaSuit and scuba set, once a second. */
    private void everySecond(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        // Breathing: the scuba set's oxygen, or the helmet's breathing unit (energy).
        if (player.getRemainingAir() < player.getMaximumAir()) {
            GearBehaviors.ScubaTank tank = GearBehaviors.options(chest, GearBehaviors.ScubaTank.class);
            boolean masked = tank != null && tank.mask().equals(PaperItems.idOf(helmet == null ? new ItemStack(Material.AIR) : helmet));
            boolean breathed = masked && useChemical(chest, tank.oxygenPerRefill())
                    || modules(helmet).contains("electrolytic_breathing_unit") && useEnergy(helmet, moduleEnergy("electrolytic_breathing_unit"));
            if (breathed) player.setRemainingAir(player.getMaximumAir());
        }
        {
            Set<String> modules = modules(helmet);
            if (modules.contains("vision_enhancement_unit") && useEnergy(helmet, moduleEnergy("vision_enhancement_unit"))) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
            }
            if (modules.contains("nutritional_injection_unit") && player.getFoodLevel() < 20 && useEnergy(helmet, moduleEnergy("nutritional_injection_unit"))) {
                player.setFoodLevel(player.getFoodLevel() + 1);
                player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 1));
            }
            if (modules.contains("inhalation_purification_unit")) {
                for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
                    if (HARMFUL.contains(effect.getType()) && useEnergy(helmet, moduleEnergy("inhalation_purification_unit"))) player.removePotionEffect(effect.getType());
                }
            }
        }
        // Flight: the gravitational modulating unit, 1 kJ a tick while flying.
        boolean canFly = modules(chest).contains("gravitational_modulating_unit")
                && energy(chest) >= moduleEnergy("gravitational_modulating_unit");
        if (canFly) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                this.flying.add(player.getUniqueId());
            }
            if (player.isFlying()) useEnergy(chest, moduleEnergy("gravitational_modulating_unit"));
        } else if (this.flying.remove(player.getUniqueId()) && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (modules(legs).contains("locomotive_boosting_unit") && player.isSprinting()
                && useEnergy(legs, moduleEnergy("locomotive_boosting_unit"))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, false));
        }
        if (modules(boots).contains("hydraulic_propulsion_unit") && useEnergy(boots, moduleEnergy("hydraulic_propulsion_unit"))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 1, true, false, false));
        }
        // Energy makers: sunlight on the helmet, lava or fire around the pants.
        if (modules(helmet).contains("solar_recharging_unit")
                && player.getWorld().isDayTime() && player.getLocation().getBlock().getLightFromSky() >= 15) {
            addEnergy(helmet, moduleEnergy("solar_recharging_unit"));
        }
        if (modules(legs).contains("geothermal_generator_unit")
                && (player.isInLava() || player.getFireTicks() > 0)) {
            addEnergy(legs, moduleEnergy("geothermal_generator_unit"));
        }
        // Charge distribution: the body armour shares its energy with the other pieces and the held item.
        if (modules(chest).contains("charge_distribution_unit")) {
            for (ItemStack other : new ItemStack[]{helmet, legs, boots, player.getInventory().getItemInMainHand()}) {
                if (other == null || other.isEmpty() || capacity(other) <= 0) continue;
                long given = addEnergy(other, Math.min(moduleEnergy("charge_distribution_unit"), energy(chest)));
                if (given > 0) useEnergy(chest, given);
            }
        }
        // Motorized servos: step up a full block (Minecraft 1.20.5+).
        boolean servo = modules(legs).contains("motorized_servo_unit") && energy(legs) >= moduleEnergy("motorized_servo_unit");
        stepHeight(player, servo ? 1.1 : -1);
        if (modules(legs).contains("hydrostatic_repulsor_unit") && player.isInWater()
                && useEnergy(legs, moduleEnergy("hydrostatic_repulsor_unit"))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, true, false, false));
        }
        // Gyroscopic stabilization: mining in the air or underwater is less slow.
        if (modules(chest).contains("gyroscopic_stabilization_unit")
                && (!player.isOnGround() || player.isInWater()) && useEnergy(chest, moduleEnergy("gyroscopic_stabilization_unit"))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 2, true, false, false));
        }
    }

    /** Sets the step height ({@code height} below 0: back to normal, if it was raised). */
    private void stepHeight(Player player, double height) {
        if (height < 0 && !this.stepping.remove(player.getUniqueId())) return;
        Attribute attribute = null;
        for (String key : List.of("step_height", "generic.step_height")) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (attribute != null) break;
        }
        var instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance == null) return;
        instance.setBaseValue(height < 0 ? 0.6 : height);
        if (height >= 0) this.stepping.add(player.getUniqueId());
    }

    /**
     * Boots with {@code mekanized:fall_absorber} (free runners, MekaSuit boots) take the fall for energy. An
     * event: worn items have no hook in CraftEngine.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        ItemStack boots = player.getInventory().getBoots();
        GearBehaviors.FallAbsorber absorber = GearBehaviors.options(boots, GearBehaviors.FallAbsorber.class);
        if (absorber == null) return;
        long cost = (long) Math.ceil(event.getDamage() * absorber.energyPerDamage());
        if (useEnergy(boots, cost)) {
            event.setCancelled(true);
            player.getInventory().setBoots(boots);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Fall absorbed: %,d J (%,d J left)", cost, energy(boots)),
                    NamedTextColor.GRAY));
        }
    }
}
