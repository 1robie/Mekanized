package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.mekanized.machine.GearSettings;
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
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.addEnergy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.capacity;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.energy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.id;
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

    /** Magnetic attraction unit: items within 8 blocks fly to the player (20 J each, every 5 ticks). */
    private static void magnet(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        if (!"mekasuit_boots".equals(id(boots)) || !modules(boots).contains("magnetic_attraction_unit")) return;
        Location at = player.getLocation().add(0, 0.5, 0);
        for (Item item : player.getWorld().getNearbyEntitiesByType(Item.class, at, GearSettings.magnetRange)) {
            if (item.getPickupDelay() > 0 || !useEnergy(boots, GearSettings.magnetEnergyPerItem)) continue;
            item.setVelocity(at.toVector().subtract(item.getLocation().toVector()).normalize().multiply(0.6));
        }
    }

    /** Hydrogen: up while jumping (Mekanism's 0.15 a tick, up to 0.5, from 0 when falling), 1 mB a tick. */
    private void jetpack(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        String id = id(chest);
        boolean pack = "jetpack".equals(id) || "jetpack_armored".equals(id)
                || "mekasuit_bodyarmor".equals(id) && modules(chest).contains("jetpack_unit");
        if (!pack || player.isOnGround() || player.isFlying() || !jumping(player)) return;
        if (!useChemical(chest, GearSettings.jetpackHydrogenPerTick)) return;
        // The server does not know the player's own horizontal speed: steer with the movement keys.
        Vector velocity = steering(player);
        // A fall is cancelled at once: thrusting from a negative speed would only slow it down.
        velocity.setY(Math.min(Math.max(player.getVelocity().getY(), 0) + GearSettings.jetpackThrust,
                GearSettings.jetpackMaxSpeed));
        player.setVelocity(velocity);
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 0.6, 0), 2, 0.15, 0.05, 0.15, 0.01);
    }

    /** Horizontal speed from the movement keys held, along where the player looks (1.21.2+; before, the current drift). */
    private static Vector steering(Player player) {
        PlayerInput input = Nms.get().input(player);
        if (input == null) {
            Vector velocity = player.getVelocity();
            return new Vector(velocity.getX(), 0, velocity.getZ());
        }
        double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        double left = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        double speed = input.sprint() ? GearSettings.jetpackSprintSpeed : GearSettings.jetpackWalkSpeed;
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
            boolean breathed = "scuba_mask".equals(id(helmet)) && "scuba_tank".equals(id(chest)) && useChemical(chest, GearSettings.scubaOxygen)
                    || "mekasuit_helmet".equals(id(helmet)) && modules(helmet).contains("electrolytic_breathing_unit") && useEnergy(helmet, GearSettings.breathingEnergy);
            if (breathed) player.setRemainingAir(player.getMaximumAir());
        }
        if ("mekasuit_helmet".equals(id(helmet))) {
            Set<String> modules = modules(helmet);
            if (modules.contains("vision_enhancement_unit") && useEnergy(helmet, GearSettings.visionEnergy)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
            }
            if (modules.contains("nutritional_injection_unit") && player.getFoodLevel() < 20 && useEnergy(helmet, GearSettings.nutritionEnergy)) {
                player.setFoodLevel(player.getFoodLevel() + 1);
                player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 1));
            }
            if (modules.contains("inhalation_purification_unit")) {
                for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
                    if (HARMFUL.contains(effect.getType()) && useEnergy(helmet, GearSettings.purificationEnergy)) player.removePotionEffect(effect.getType());
                }
            }
        }
        // Flight: the gravitational modulating unit, 1 kJ a tick while flying.
        boolean canFly = "mekasuit_bodyarmor".equals(id(chest)) && modules(chest).contains("gravitational_modulating_unit")
                && energy(chest) >= GearSettings.flightEnergy;
        if (canFly) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                this.flying.add(player.getUniqueId());
            }
            if (player.isFlying()) useEnergy(chest, GearSettings.flightEnergy);
        } else if (this.flying.remove(player.getUniqueId()) && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if ("mekasuit_pants".equals(id(legs)) && modules(legs).contains("locomotive_boosting_unit") && player.isSprinting()
                && useEnergy(legs, GearSettings.locomotiveEnergy)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, false));
        }
        if ("mekasuit_boots".equals(id(boots)) && modules(boots).contains("hydraulic_propulsion_unit") && useEnergy(boots, GearSettings.hydraulicEnergy)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 1, true, false, false));
        }
        // Energy makers: sunlight on the helmet, lava or fire around the pants.
        if ("mekasuit_helmet".equals(id(helmet)) && modules(helmet).contains("solar_recharging_unit")
                && player.getWorld().isDayTime() && player.getLocation().getBlock().getLightFromSky() >= 15) {
            addEnergy(helmet, GearSettings.solarEnergy);
        }
        if ("mekasuit_pants".equals(id(legs)) && modules(legs).contains("geothermal_generator_unit")
                && (player.isInLava() || player.getFireTicks() > 0)) {
            addEnergy(legs, GearSettings.geothermalEnergy);
        }
        // Charge distribution: the body armour shares its energy with the other pieces and the held item.
        if ("mekasuit_bodyarmor".equals(id(chest)) && modules(chest).contains("charge_distribution_unit")) {
            for (ItemStack other : new ItemStack[]{helmet, legs, boots, player.getInventory().getItemInMainHand()}) {
                if (other == null || id(other) == null || capacity(other) <= 0) continue;
                long given = addEnergy(other, Math.min(GearSettings.distributionEnergy, energy(chest)));
                if (given > 0) useEnergy(chest, given);
            }
        }
        // Motorized servos: step up a full block (Minecraft 1.20.5+).
        boolean servo = "mekasuit_pants".equals(id(legs)) && modules(legs).contains("motorized_servo_unit") && energy(legs) >= GearSettings.servoMinEnergy;
        stepHeight(player, servo ? 1.1 : -1);
        if ("mekasuit_pants".equals(id(legs)) && modules(legs).contains("hydrostatic_repulsor_unit") && player.isInWater()
                && useEnergy(legs, GearSettings.repulsorEnergy)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, true, false, false));
        }
        // Gyroscopic stabilization: mining in the air or underwater is less slow.
        if ("mekasuit_bodyarmor".equals(id(chest)) && modules(chest).contains("gyroscopic_stabilization_unit")
                && (!player.isOnGround() || player.isInWater()) && useEnergy(chest, GearSettings.gyroscopicEnergy)) {
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

    /** Free runners and MekaSuit boots take the fall for energy (50 J a point of damage). */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        ItemStack boots = player.getInventory().getBoots();
        String id = id(boots);
        if (!"free_runners".equals(id) && !"free_runners_armored".equals(id) && !"mekasuit_boots".equals(id)) return;
        long cost = (long) Math.ceil(event.getDamage() * GearSettings.fallEnergyPerDamage);
        if (useEnergy(boots, cost)) {
            event.setCancelled(true);
            player.getInventory().setBoots(boots);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Fall absorbed: %,d J (%,d J left)", cost, energy(boots)),
                    NamedTextColor.GRAY));
        }
    }
}
