package fr.robie.mekanized.paper.gear;

import fr.robie.mekanized.MekanizedContent;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.ItemDefinition;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The numbers of Mekanized's gear, as CraftEngine item behaviors holding options (D32): each item says
 * how much it costs and does, in its item config; the listeners and the suit ticker read them here.
 * The defaults are Mekanism's (and what {@code config.yml} held before).
 */
public final class GearBehaviors {
    private GearBehaviors() {
    }

    /** The first behavior of {@code type} on {@code stack}, or {@code null}. */
    public static <T> @Nullable T of(@Nullable ItemStack stack, Class<T> type) {
        if (stack == null || stack.isEmpty()) return null;
        return BukkitAdaptor.adapt(stack).getBehavior().map(behavior -> behavior.getFirst(type)).orElse(null);
    }

    /** The {@code mekanized:module} behavior of the module item {@code module_<name>}, or {@code null}. */
    public static @Nullable ModuleHolderBehavior.Module module(String name) {
        ItemDefinition item = CraftEngineItems.byId(net.momirealms.craftengine.core.util.Key.of(MekanizedContent.NAMESPACE, "module_" + name));
        return item == null ? null : item.behavior().getFirst(ModuleHolderBehavior.Module.class);
    }

    /** What the installed module {@code name} costs (Joules per second, per use or per item), 0 if unknown. */
    public static long moduleEnergy(String name) {
        ModuleHolderBehavior.Module module = module(name);
        return module == null ? 0 : module.energy();
    }

    /** The most levels of the module {@code name} one piece takes, 1 if unknown. */
    public static int moduleMaxLevel(String name) {
        ModuleHolderBehavior.Module module = module(name);
        return module == null ? 1 : module.maxLevel();
    }

    /**
     * {@code mekanized:mining_tool}: the atomic disassembler and the Meka-Tool.
     *
     * <pre>{@code
     * - type: mekanized:mining_tool
     *   energy-per-block: 100        # and the charge it needs to mine fast
     *   vein-limit: 64               # blocks one vein (sneaking) breaks
     *   vein-limit-with-unit: 256    # with the vein mining unit
     *   hit-energy: 2000             # per hit while charged
     *   damage: 20.0                 # damage dealt at least while charged
     *   amplified-damage: 20.0       # with the attack amplification unit
     *   charged-speed: 16.0          # mining speed while charged
     *   amplified-speed: 32.0        # with the excavation escalation unit
     * }</pre>
     */
    public record MiningTool(long energyPerBlock, int veinLimit, int veinLimitWithUnit, long hitEnergy, double damage,
                             double amplifiedDamage, float chargedSpeed, float amplifiedSpeed) {
        public static final ItemBehaviorFactory<Holder<MiningTool>> FACTORY = (pack, path, id, s) -> new Holder<>(new MiningTool(
                s.getLong("energy-per-block", 100), s.getInt("vein-limit", 64), s.getInt("vein-limit-with-unit", 256),
                s.getLong("hit-energy", 2000), s.getDouble("damage", 20), s.getDouble("amplified-damage", s.getDouble("damage", 20)),
                s.getFloat("charged-speed", 16), s.getFloat("amplified-speed", 32)));
    }

    /** {@code mekanized:electric_bow}: {@code energy-per-shot} (120): shots use energy, not arrows. */
    public record ElectricBow(long energyPerShot) {
        public static final ItemBehaviorFactory<Holder<ElectricBow>> FACTORY = (pack, path, id, s) ->
                new Holder<>(new ElectricBow(s.getLong("energy-per-shot", 120)));
    }

    /**
     * {@code mekanized:jetpack}: up while jumping, on hydrogen.
     *
     * <pre>{@code
     * - type: mekanized:jetpack
     *   hydrogen-per-tick: 1
     *   thrust: 0.15          # upward speed added each tick
     *   max-speed: 0.5        # top upward speed
     *   walk-speed: 0.3       # horizontal speed steered with the movement keys
     *   sprint-speed: 0.45
     *   requires-module: jetpack_unit   # optional: only with this module installed (the MekaSuit)
     * }</pre>
     */
    public record Jetpack(long hydrogenPerTick, double thrust, double maxSpeed, double walkSpeed, double sprintSpeed,
                          @Nullable String requiresModule) {
        public static final ItemBehaviorFactory<Holder<Jetpack>> FACTORY = (pack, path, id, s) -> new Holder<>(new Jetpack(
                s.getLong("hydrogen-per-tick", 1), s.getDouble("thrust", 0.15), s.getDouble("max-speed", 0.5),
                s.getDouble("walk-speed", 0.3), s.getDouble("sprint-speed", 0.45), s.getString("requires-module", (String) null)));
    }

    /**
     * {@code mekanized:scuba_tank}: {@code oxygen-per-refill} (20 mB) each time it refills the air, while the
     * {@code mask} item ({@code mekanized:scuba_mask}) is worn.
     */
    public record ScubaTank(long oxygenPerRefill, net.kyori.adventure.key.Key mask) {
        public static final ItemBehaviorFactory<Holder<ScubaTank>> FACTORY = (pack, path, id, s) -> new Holder<>(new ScubaTank(
                s.getLong("oxygen-per-refill", 20), net.kyori.adventure.key.Key.key(s.getString("mask", "mekanized:scuba_mask"))));
    }

    /** {@code mekanized:fall_absorber}: boots that take falls for {@code energy-per-damage} (50 J) a point of damage. */
    public record FallAbsorber(long energyPerDamage) {
        public static final ItemBehaviorFactory<Holder<FallAbsorber>> FACTORY = (pack, path, id, s) ->
                new Holder<>(new FallAbsorber(s.getLong("energy-per-damage", 50)));
    }

    /**
     * {@code mekanized:radiation_shielding}: armour that stops {@code protection} (0.25) of the radiation;
     * with {@code requires-module}, only while that module is installed (the MekaSuit).
     */
    public record RadiationShielding(double protection, @Nullable String requiresModule) {
        public static final ItemBehaviorFactory<Holder<RadiationShielding>> FACTORY = (pack, path, id, s) ->
                new Holder<>(new RadiationShielding(Math.clamp(s.getDouble("protection", 0.25), 0, 1),
                        s.getString("requires-module", (String) null)));
    }

    /**
     * {@code mekanized:qio_drive}: a QIO drive holding {@code items} items of at most {@code kinds} kinds
     * (D34). Any item with this behavior goes into a drive array.
     */
    public record QioDrive(long items, long kinds) {
        public static final ItemBehaviorFactory<Holder<QioDrive>> FACTORY = (pack, path, id, s) ->
                new Holder<>(new QioDrive(Math.max(1, s.getLong("items", 16_000)), Math.max(1, s.getLong("kinds", 128))));
    }

    /** The options of type {@code type} of the CraftEngine item {@code item} (a {@link Holder}), or {@code null}. */
    public static <T> @Nullable T options(net.kyori.adventure.key.Key item, Class<T> type) {
        ItemDefinition definition = CraftEngineItems.byId(net.momirealms.craftengine.core.util.Key.of(item.namespace(), item.value()));
        if (definition == null) return null;
        Object[] found = {null};
        definition.behavior().let(Holder.class, holder -> {
            if (found[0] == null && type.isInstance(holder.options())) found[0] = holder.options();
        });
        return type.cast(found[0]);
    }

    /** A behavior that only carries options, found with {@link #options(ItemStack, Class)}. */
    public static final class Holder<T> extends ItemBehavior {
        private final T options;

        Holder(T options) {
            this.options = options;
        }

        public T options() {
            return this.options;
        }
    }

    /** The options of type {@code type} on {@code stack} (a {@link Holder}), or {@code null}. */
    public static <T> @Nullable T options(@Nullable ItemStack stack, Class<T> type) {
        if (stack == null || stack.isEmpty()) return null;
        Object[] found = {null};
        BukkitAdaptor.adapt(stack).getBehavior().ifPresent(behavior -> behavior.let(Holder.class, holder -> {
            if (found[0] == null && type.isInstance(holder.options())) found[0] = holder.options();
        }));
        return type.cast(found[0]);
    }
}
