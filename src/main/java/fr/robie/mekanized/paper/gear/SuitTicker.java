package fr.robie.mekanized.paper.gear;

import fr.robie.machineengine.api.player.MachinePlayer;
import fr.robie.mekanized.MekanizedPlayers;
import fr.robie.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.platform.Nms;
import fr.robie.machineengine.api.platform.PlayerInput;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import static fr.robie.mekanized.paper.gear.GearBehaviors.moduleEnergy;
import static fr.robie.mekanized.paper.gear.GearItems.addEnergy;
import static fr.robie.mekanized.paper.gear.GearItems.capacity;
import static fr.robie.mekanized.paper.gear.GearItems.energy;
import static fr.robie.mekanized.paper.gear.GearItems.modules;
import static fr.robie.mekanized.paper.gear.GearItems.useChemical;
import static fr.robie.mekanized.paper.gear.GearItems.useEnergy;

/**
 * The per-player work of the suits and packs: jetpack, flight, step height, the MekaSuit module effects,
 * the magnetic attraction unit and the free runners, run for each player by their own scheduler.
 */
public final class SuitTicker implements Listener {
    private SuitTicker() {
    }

    public static void install(Plugin plugin) {
        SuitTicker ticker = new SuitTicker();
        Bukkit.getPluginManager().registerEvents(ticker, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) ticker.start(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    private void start(Player player) {
        long[] tick = {0};
        PaperWorld.scheduler().runTimer(player, () -> {
            if (!player.isOnline()) return;
            jetpack(player);
            if (++tick[0] % 5 == 0) magnet(player);
            if (tick[0] % 20 == 0) everySecond(player);
        }, null, 1, 1);
    }

    /** Magnetic attraction unit: items within its {@code range} per level fly to the player at its {@code pull-speed} ({@code energy} each, every 5 ticks). */
    private static void magnet(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        int level = GearItems.level(boots, "magnetic_attraction_unit");
        if (level == 0) return;
        ModuleHolderBehavior.Module unit = GearBehaviors.module("magnetic_attraction_unit");
        if (unit == null || unit.range() <= 0) return;
        Location at = player.getLocation().add(0, 0.5, 0);
        for (Item item : player.getWorld().getNearbyEntitiesByType(Item.class, at, (double) unit.range() * level)) {
            if (item.getPickupDelay() > 0 || !useEnergy(boots, unit.energy(level))) continue;
            item.setVelocity(at.toVector().subtract(item.getLocation().toVector()).normalize().multiply(unit.pullSpeed()));
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
            effect(player, helmet, "vision_enhancement_unit", PotionEffectType.NIGHT_VISION);
            if (modules.contains("nutritional_injection_unit") && player.getFoodLevel() < 20 && useEnergy(helmet, moduleEnergy("nutritional_injection_unit"))) {
                player.setFoodLevel(player.getFoodLevel() + 1);
                player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 1));
            }
            ModuleHolderBehavior.Module purifier = GearBehaviors.module("inhalation_purification_unit");
            if (modules.contains("inhalation_purification_unit") && purifier != null) {
                for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
                    if (purifier.removesEffects().contains(effect.getType().getKey().toString())
                            && useEnergy(helmet, purifier.energy())) player.removePotionEffect(effect.getType());
                }
            }
        }
        // Flight: the gravitational modulating unit, 1 kJ a tick while flying.
        boolean canFly = modules(chest).contains("gravitational_modulating_unit")
                && energy(chest) >= moduleEnergy("gravitational_modulating_unit");
        MachinePlayer data = MekanizedPlayers.of(player).orElse(null);
        if (data == null) return;
        if (canFly) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                data.set(MekanizedPlayers.SUIT_FLIGHT, true);
            }
            if (player.isFlying()) useEnergy(chest, moduleEnergy("gravitational_modulating_unit"));
        } else if (data.get(MekanizedPlayers.SUIT_FLIGHT) && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            data.set(MekanizedPlayers.SUIT_FLIGHT, false);
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        // Boosts: their effect and energy by level, as their item config says.
        if (player.isSprinting()) effect(player, legs, "locomotive_boosting_unit", PotionEffectType.SPEED);
        effect(player, boots, "hydraulic_propulsion_unit", PotionEffectType.JUMP_BOOST);
        // Energy makers, their energy by level: sunlight on the helmet, lava or fire around the pants.
        if (player.getWorld().isDayTime() && player.getLocation().getBlock().getLightFromSky() >= 15) make(helmet, "solar_recharging_unit");
        if (player.isInLava() || player.getFireTicks() > 0) make(legs, "geothermal_generator_unit");
        // Charge distribution: the body armour shares its energy with the other pieces and the held item.
        if (modules(chest).contains("charge_distribution_unit")) {
            for (ItemStack other : new ItemStack[]{helmet, legs, boots, player.getInventory().getItemInMainHand()}) {
                if (other == null || other.isEmpty() || capacity(other) <= 0) continue;
                long given = addEnergy(other, Math.min(moduleEnergy("charge_distribution_unit"), energy(chest)));
                if (given > 0) useEnergy(chest, given);
            }
        }
        // Motorized servos: step up its step-height (Minecraft 1.20.5+).
        ModuleHolderBehavior.Module servo = GearBehaviors.module("motorized_servo_unit");
        int servoLevel = GearItems.level(legs, "motorized_servo_unit");
        boolean stepping = servo != null && servoLevel > 0 && servo.stepHeight() > 0 && energy(legs) >= servo.energy(servoLevel);
        stepHeight(player, data, stepping ? servo.stepHeight() : -1);
        if (player.isInWater()) effect(player, legs, "hydrostatic_repulsor_unit", PotionEffectType.DOLPHINS_GRACE);
        // Gyroscopic stabilization: mining in the air or underwater is less slow.
        if (!player.isOnGround() || player.isInWater()) effect(player, chest, "gyroscopic_stabilization_unit", PotionEffectType.HASTE);
    }

    /**
     * A potion effect module in {@code gear}, installed and on: renews its effect ({@code effect-amplifier}
     * per level, {@code effect-duration}) for its energy.
     */
    private static void effect(Player player, ItemStack gear, String name, PotionEffectType type) {
        int level = GearItems.level(gear, name);
        ModuleHolderBehavior.Module unit = level == 0 ? null : GearBehaviors.module(name);
        if (unit == null || !useEnergy(gear, unit.energy(level))) return;
        player.addPotionEffect(new PotionEffect(type, unit.effectDuration(), unit.amplifier(level), true, false, false));
    }

    /** An energy maker module in {@code gear}, installed and on: adds its energy (by level, as configured). */
    private static void make(ItemStack gear, String name) {
        int level = GearItems.level(gear, name);
        ModuleHolderBehavior.Module unit = level == 0 ? null : GearBehaviors.module(name);
        if (unit != null) addEnergy(gear, unit.energy(level));
    }

    /** Sets the step height ({@code height} below 0: back to normal, if it was raised). */
    private static void stepHeight(Player player, MachinePlayer data, double height) {
        if (height < 0 && !data.get(MekanizedPlayers.RAISED_STEP)) return;
        data.set(MekanizedPlayers.RAISED_STEP, height >= 0);
        Attribute attribute = null;
        for (String key : List.of("step_height", "generic.step_height")) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            if (attribute != null) break;
        }
        var instance = attribute == null ? null : player.getAttribute(attribute);
        if (instance == null) return;
        instance.setBaseValue(height < 0 ? 0.6 : height);
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
