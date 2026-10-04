package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.mekanized.machine.EnergyItems;
import fr.robie.machineengine.paper.MachineLocator;
import fr.robie.machineengine.paper.PaperItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mekanized's tools and armour (Phase 6 wave 5), on Paper: their energy (charged like the energy
 * tablet: energy cubes, chargepads), their chemical (hydrogen, oxygen: right-click a machine holding
 * it), and modules (sneak-use a module while wearing, or holding in the off hand, the gear it fits).
 */
final class PaperGear implements Listener {
    static final NamespacedKey CHEMICAL = new NamespacedKey(MekanizedContent.NAMESPACE, "gear_chemical");
    static final NamespacedKey MODULES = new NamespacedKey(MekanizedContent.NAMESPACE, "modules");

    /** Gear holding a chemical: item -> {chemical, capacity in mB}. */
    private static final Map<String, Object[]> CHEMICALS = Map.of(
            "jetpack", new Object[]{"hydrogen", 24_000L}, "jetpack_armored", new Object[]{"hydrogen", 24_000L},
            "flamethrower", new Object[]{"hydrogen", 24_000L}, "scuba_tank", new Object[]{"oxygen", 24_000L},
            "mekasuit_bodyarmor", new Object[]{"hydrogen", 24_000L});
    /** Modules -> the gear they go in. */
    private static final Map<String, String> MODULE_TARGETS = Map.ofEntries(
            Map.entry("electrolytic_breathing_unit", "mekasuit_helmet"), Map.entry("vision_enhancement_unit", "mekasuit_helmet"),
            Map.entry("nutritional_injection_unit", "mekasuit_helmet"), Map.entry("inhalation_purification_unit", "mekasuit_helmet"),
            Map.entry("jetpack_unit", "mekasuit_bodyarmor"), Map.entry("gravitational_modulating_unit", "mekasuit_bodyarmor"),
            Map.entry("elytra_unit", "mekasuit_bodyarmor"), Map.entry("locomotive_boosting_unit", "mekasuit_pants"),
            Map.entry("hydraulic_propulsion_unit", "mekasuit_boots"), Map.entry("excavation_escalation_unit", "meka_tool"),
            Map.entry("attack_amplification_unit", "meka_tool"), Map.entry("silk_touch_unit", "meka_tool"),
            Map.entry("fortune_unit", "meka_tool"), Map.entry("vein_mining_unit", "meka_tool"), Map.entry("teleportation_unit", "meka_tool"));
    private static final Set<PotionEffectType> HARMFUL = Set.of(PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.HUNGER,
            PotionEffectType.NAUSEA, PotionEffectType.BLINDNESS, PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.WEAKNESS, PotionEffectType.DARKNESS);

    private final Plugin plugin;
    /** Players this plugin let fly (gravitational modulating unit). */
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();
    /** Blocks being broken by vein mining (their own break events must not start another vein). */
    private final Set<Block> veining = ConcurrentHashMap.newKeySet();

    private PaperGear(Plugin plugin) {
        this.plugin = plugin;
    }

    static void install(Plugin plugin) {
        PaperGear gear = new PaperGear(plugin);
        Bukkit.getPluginManager().registerEvents(gear, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) gear.start(player);
    }

    // -- item data ------------------------------------------------------------------------------

    private static @Nullable String id(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Key key = PaperItems.idOf(stack);
        return key.namespace().equals(MekanizedContent.NAMESPACE) ? key.value() : null;
    }

    static long energy(ItemStack stack) {
        Long energy = stack.getItemMeta().getPersistentDataContainer().get(PaperEnergyItems.ENERGY, PersistentDataType.LONG);
        return energy == null ? 0 : energy;
    }

    /** Takes {@code joules} from the gear if it has them all. */
    static boolean useEnergy(ItemStack stack, long joules) {
        long energy = energy(stack);
        if (energy < joules) return false;
        String id = id(stack);
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(PaperEnergyItems.ENERGY, PersistentDataType.LONG, energy - joules);
            refresh(meta, MekanizedContent.key(id));
        });
        return true;
    }

    static long chemical(ItemStack stack) {
        Long amount = stack.getItemMeta().getPersistentDataContainer().get(CHEMICAL, PersistentDataType.LONG);
        return amount == null ? 0 : amount;
    }

    private static boolean useChemical(ItemStack stack, long amount) {
        long held = chemical(stack);
        if (held < amount) return false;
        String id = id(stack);
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(CHEMICAL, PersistentDataType.LONG, held - amount);
            refresh(meta, MekanizedContent.key(id));
        });
        return true;
    }

    static Set<String> modules(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return Set.of();
        String list = stack.getItemMeta().getPersistentDataContainer().get(MODULES, PersistentDataType.STRING);
        return list == null || list.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(list.split(",")));
    }

    /** Rewrites the lore (energy, chemical, modules) and the tool speeds of a gear item. */
    static void refresh(ItemMeta meta, Key item) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<Component> lore = new ArrayList<>();
        long capacity = EnergyItems.CAPACITY.getOrDefault(item, 0L);
        Long energy = pdc.get(PaperEnergyItems.ENERGY, PersistentDataType.LONG);
        if (capacity > 0) lore.add(line(String.format(Locale.ROOT, "Energy: %,d / %,d J", energy == null ? 0 : energy, capacity)));
        Object[] chemical = CHEMICALS.get(item.value());
        if (chemical != null) {
            Long held = pdc.get(CHEMICAL, PersistentDataType.LONG);
            lore.add(line(String.format(Locale.ROOT, "%s: %,d / %,d mB", capitalize((String) chemical[0]), held == null ? 0 : held, (long) chemical[1])));
        }
        String modules = pdc.get(MODULES, PersistentDataType.STRING);
        if (modules != null && !modules.isEmpty()) {
            for (String module : modules.split(",")) lore.add(line("+ " + capitalize(module.replace('_', ' '))).color(NamedTextColor.AQUA));
        }
        meta.lore(lore);
        // Disassembler and Meka-Tool: fast on what pickaxes, axes, shovels and hoes mine while charged.
        if (item.value().equals("atomic_disassembler") || item.value().equals("meka_tool")) {
            boolean charged = energy != null && energy >= 100;
            float speed = !charged ? 1f : modules != null && modules.contains("excavation_escalation_unit") ? 32f : 16f;
            ToolComponent tool = meta.getTool();
            tool.setRules(new ArrayList<>());
            tool.setDefaultMiningSpeed(charged ? 4f : 1f);
            tool.setDamagePerBlock(0);
            for (Tag<org.bukkit.Material> tag : List.of(Tag.MINEABLE_PICKAXE, Tag.MINEABLE_AXE, Tag.MINEABLE_SHOVEL, Tag.MINEABLE_HOE)) {
                tool.addRule(tag, speed, charged);
            }
            meta.setTool(tool);
        }
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // -- the per-player work ---------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    private void start(Player player) {
        long[] tick = {0};
        player.getScheduler().runAtFixedRate(this.plugin, task -> {
            if (!player.isOnline()) return;
            jetpack(player);
            if (++tick[0] % 20 == 0) everySecond(player);
        }, null, 1, 1);
    }

    /** Hydrogen: up while jumping (Mekanism's 0.15 a tick, up to 0.5, from 0 when falling), 1 mB a tick. */
    private void jetpack(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        String id = id(chest);
        boolean pack = "jetpack".equals(id) || "jetpack_armored".equals(id)
                || "mekasuit_bodyarmor".equals(id) && modules(chest).contains("jetpack_unit");
        if (!pack || player.isOnGround() || player.isFlying() || !jumping(player)) return;
        if (!useChemical(chest, 1)) return;
        // The server does not know the player's own horizontal speed: steer with the movement keys.
        Vector velocity = steering(player);
        // A fall is cancelled at once: thrusting from a negative speed would only slow it down.
        velocity.setY(Math.min(Math.max(player.getVelocity().getY(), 0) + 0.15, 0.5));
        player.setVelocity(velocity);
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 0.6, 0), 2, 0.15, 0.05, 0.15, 0.01);
    }

    /** Horizontal speed from the movement keys held, along where the player looks (Paper 1.21.3+). */
    private static Vector steering(Player player) {
        try {
            Object input = player.getClass().getMethod("getCurrentInput").invoke(player);
            Class<?> type = input.getClass();
            double forward = ((boolean) type.getMethod("isForward").invoke(input) ? 1 : 0) - ((boolean) type.getMethod("isBackward").invoke(input) ? 1 : 0);
            double left = ((boolean) type.getMethod("isLeft").invoke(input) ? 1 : 0) - ((boolean) type.getMethod("isRight").invoke(input) ? 1 : 0);
            double speed = (boolean) type.getMethod("isSprint").invoke(input) ? 0.45 : 0.3;
            double yaw = Math.toRadians(player.getLocation().getYaw());
            Vector move = new Vector(-Math.sin(yaw) * forward + Math.cos(yaw) * left, 0, Math.cos(yaw) * forward + Math.sin(yaw) * left);
            return move.lengthSquared() == 0 ? move : move.normalize().multiply(speed);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Vector velocity = player.getVelocity();
            return new Vector(velocity.getX(), 0, velocity.getZ());
        }
    }

    /** Whether the player holds jump (Paper 1.21.3+; older servers: never, the jetpack needs it). */
    private static boolean jumping(Player player) {
        try {
            Object input = player.getClass().getMethod("getCurrentInput").invoke(player);
            return (boolean) input.getClass().getMethod("isJump").invoke(input);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** The MekaSuit and scuba set, once a second. */
    private void everySecond(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();
        // Breathing: the scuba set's oxygen, or the helmet's breathing unit (energy).
        if (player.getRemainingAir() < player.getMaximumAir()) {
            boolean breathed = "scuba_mask".equals(id(helmet)) && "scuba_tank".equals(id(chest)) && useChemical(chest, 20)
                    || "mekasuit_helmet".equals(id(helmet)) && modules(helmet).contains("electrolytic_breathing_unit") && useEnergy(helmet, 2_000);
            if (breathed) player.setRemainingAir(player.getMaximumAir());
        }
        if ("mekasuit_helmet".equals(id(helmet))) {
            Set<String> modules = modules(helmet);
            if (modules.contains("vision_enhancement_unit") && useEnergy(helmet, 200)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
            }
            if (modules.contains("nutritional_injection_unit") && player.getFoodLevel() < 20 && useEnergy(helmet, 2_500)) {
                player.setFoodLevel(player.getFoodLevel() + 1);
                player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 1));
            }
            if (modules.contains("inhalation_purification_unit")) {
                for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
                    if (HARMFUL.contains(effect.getType()) && useEnergy(helmet, 2_000)) player.removePotionEffect(effect.getType());
                }
            }
        }
        // Flight: the gravitational modulating unit, 1 kJ a tick while flying.
        boolean canFly = "mekasuit_bodyarmor".equals(id(chest)) && modules(chest).contains("gravitational_modulating_unit")
                && energy(chest) >= 20_000;
        if (canFly) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                this.flying.add(player.getUniqueId());
            }
            if (player.isFlying()) useEnergy(chest, 20_000);
        } else if (this.flying.remove(player.getUniqueId()) && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if ("mekasuit_pants".equals(id(legs)) && modules(legs).contains("locomotive_boosting_unit") && player.isSprinting()
                && useEnergy(legs, 2_000)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, false));
        }
        if ("mekasuit_boots".equals(id(boots)) && modules(boots).contains("hydraulic_propulsion_unit") && useEnergy(boots, 200)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 1, true, false, false));
        }
    }

    /** Free runners and MekaSuit boots take the fall for energy (50 J a point of damage). */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        ItemStack boots = player.getInventory().getBoots();
        String id = id(boots);
        if (!"free_runners".equals(id) && !"free_runners_armored".equals(id) && !"mekasuit_boots".equals(id)) return;
        long cost = (long) Math.ceil(event.getDamage() * 50);
        if (useEnergy(boots, cost)) {
            event.setCancelled(true);
            player.getInventory().setBoots(boots);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Fall absorbed: %,d J (%,d J left)", cost, energy(boots)),
                    NamedTextColor.GRAY));
        }
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
        boolean veinable = start.getType().getKey().getKey().endsWith("_ore") || Tag.LOGS.isTagged(start.getType());
        if (!vein || !veinable) return;
        Set<Block> seen = new HashSet<>(Set.of(start));
        ArrayDeque<Block> queue = new ArrayDeque<>(List.of(start));
        int broken = 0;
        while (!queue.isEmpty() && broken < limit) {
            Block at = queue.poll();
            for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Block next = at.getRelative(face);
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
        if (event.getProjectile() instanceof org.bukkit.entity.AbstractArrow arrow) {
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.CREATIVE_ONLY);
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
            install(player, hand, id.substring("module_".length()));
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null && CHEMICALS.containsKey(id) && fill(player, hand, id, block)) {
            event.setCancelled(true);
            return;
        }
        switch (id) {
            case "flamethrower" -> {
                event.setCancelled(true);
                flame(player, hand);
            }
            case "network_reader" -> {
                if (block == null) return;
                event.setCancelled(true);
                read(player, block);
            }
            case "meka_tool" -> {
                if (event.getAction() == Action.RIGHT_CLICK_AIR && modules(hand).contains("teleportation_unit")) teleport(player, hand);
            }
            default -> {
            }
        }
    }

    /** Puts the module into the gear it fits: the worn armour piece, or the Meka-Tool in the off hand. */
    private void install(Player player, ItemStack moduleStack, String module) {
        String target = MODULE_TARGETS.get(module);
        if (target == null) {
            player.sendActionBar(Component.text("This module does nothing in Mekanized", NamedTextColor.RED));
            return;
        }
        ItemStack gear = switch (target) {
            case "mekasuit_helmet" -> player.getInventory().getHelmet();
            case "mekasuit_bodyarmor" -> player.getInventory().getChestplate();
            case "mekasuit_pants" -> player.getInventory().getLeggings();
            case "mekasuit_boots" -> player.getInventory().getBoots();
            default -> player.getInventory().getItemInOffHand();
        };
        if (!target.equals(id(gear))) {
            player.sendActionBar(Component.text("Wear the " + target.replace('_', ' ') + " (a Meka-Tool: in the off hand) to install it",
                    NamedTextColor.RED));
            return;
        }
        Set<String> modules = new LinkedHashSet<>(modules(gear));
        if (!modules.add(module)) {
            player.sendActionBar(Component.text("Already installed", NamedTextColor.RED));
            return;
        }
        gear.editMeta(meta -> {
            meta.getPersistentDataContainer().set(MODULES, PersistentDataType.STRING, String.join(",", modules));
            switch (module) {
                case "elytra_unit" -> setGlider(meta);
                case "silk_touch_unit" -> meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
                case "fortune_unit" -> meta.addEnchant(Enchantment.FORTUNE, 3, true);
                default -> {
                }
            }
            refresh(meta, MekanizedContent.key(target));
        });
        if (player.getGameMode() != GameMode.CREATIVE) moduleStack.setAmount(moduleStack.getAmount() - 1);
        player.sendActionBar(Component.text("Installed: " + module.replace('_', ' '), NamedTextColor.GREEN));
    }

    /** The elytra unit: the chestplate glides (Paper 1.21.2+). */
    private static void setGlider(ItemMeta meta) {
        try {
            meta.getClass().getMethod("setGlider", boolean.class).invoke(meta, true);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // an older server: no gliding
        }
    }

    /** Fills the gear's chemical from a machine holding it. */
    private boolean fill(Player player, ItemStack gear, String id, Block block) {
        MachineLocator locator = MachineLocator.current();
        LoadedMachine machine = locator == null ? null
                : locator.machine(WorldPosition.of(block.getWorld().getKey(), block.getX(), block.getY(), block.getZ()));
        if (machine == null || machine.instance() == null) return false;
        String chemical = (String) CHEMICALS.get(id)[0];
        long room = (long) CHEMICALS.get(id)[1] - chemical(gear);
        ResourceType type = MekanizedContent.chemical(chemical);
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
                meta.getPersistentDataContainer().set(CHEMICAL, PersistentDataType.LONG, total);
                refresh(meta, MekanizedContent.key(id));
            });
            machine.instance().wake(WakeReason.RESOURCE_EXTRACTED);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Filled %,d mB of %s", taken, chemical), NamedTextColor.GREEN));
            return true;
        }
        return false;
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
        MachineLocator locator = MachineLocator.current();
        LoadedMachine machine = locator == null ? null
                : locator.machine(WorldPosition.of(block.getWorld().getKey(), block.getX(), block.getY(), block.getZ()));
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
        RayTraceResult hit = player.rayTraceBlocks(100);
        if (hit == null || hit.getHitBlock() == null) return;
        Block target = hit.getHitBlock();
        Location destination = target.getLocation().add(0.5, 1, 0.5);
        if (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()) return;
        long cost = 1_000 + (long) (player.getLocation().distance(destination) * 10);
        if (!useEnergy(tool, cost)) {
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Needs %,d J", cost), NamedTextColor.RED));
            return;
        }
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        player.teleportAsync(destination);
    }
}
