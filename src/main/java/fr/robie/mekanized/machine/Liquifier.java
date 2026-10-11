package fr.robie.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.upgrade.StandardModifiers;
import fr.robie.mekanized.MekanizedConfig;
import fr.robie.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


/**
 * The nutritional liquifier: food in, nutritional paste out, 50 mB per hunger point
 * the food restores. A canteen filled from it feeds its owner.
 */
public final class Liquifier {
    /** Paste per hunger point (Mekanism's). */
    public static final long PASTE_PER_FOOD = 50;
    /** Ticks per item (speed upgrades shorten it) and Joules per tick: its block config's options (D34). */
    public static final ConfigKey<Long> TICKS = ConfigKey.whole("ticks", 100, 1, 72_000).clamped();
    public static final ConfigKey<Long> ENERGY_PER_TICK = ConfigKey.whole("energy-per-tick", 200, 0, MekanizedConfig.MAX).clamped();
    /** Hunger points of Minecraft's foods. */
    public static final Map<String, Integer> NUTRITION = Map.ofEntries(
            Map.entry("apple", 4), Map.entry("baked_potato", 5), Map.entry("beef", 3), Map.entry("beetroot", 1),
            Map.entry("beetroot_soup", 6), Map.entry("bread", 5), Map.entry("carrot", 3), Map.entry("chicken", 2),
            Map.entry("chorus_fruit", 4), Map.entry("cod", 2), Map.entry("cooked_beef", 8), Map.entry("cooked_chicken", 6),
            Map.entry("cooked_cod", 5), Map.entry("cooked_mutton", 6), Map.entry("cooked_porkchop", 8), Map.entry("cooked_rabbit", 5),
            Map.entry("cooked_salmon", 6), Map.entry("cookie", 2), Map.entry("dried_kelp", 1), Map.entry("enchanted_golden_apple", 4),
            Map.entry("glow_berries", 2), Map.entry("golden_apple", 4), Map.entry("golden_carrot", 6), Map.entry("honey_bottle", 6),
            Map.entry("melon_slice", 2), Map.entry("mushroom_stew", 6), Map.entry("mutton", 2), Map.entry("poisonous_potato", 2),
            Map.entry("porkchop", 3), Map.entry("potato", 1), Map.entry("pufferfish", 1), Map.entry("pumpkin_pie", 8),
            Map.entry("rabbit", 3), Map.entry("rabbit_stew", 10), Map.entry("rotten_flesh", 4), Map.entry("salmon", 2),
            Map.entry("spider_eye", 2), Map.entry("suspicious_stew", 6), Map.entry("sweet_berries", 2), Map.entry("tropical_fish", 1));

    private Liquifier() {
    }

    /** Hunger points {@code item} restores; 0 if it is not a food. */
    public static int nutrition(@NotNull ResourceVariant item) {
        ItemData data = item.components().get(Items.DATA);
        if (item.type() != Items.ITEM || data == null || !data.item().namespace().equals("minecraft")) return 0;
        return NUTRITION.getOrDefault(data.item().value(), 0);
    }

    static void register(RegistrationContext context) {
        ResourcePredicate food = (variant, tags) -> nutrition(variant) > 0;
        ResourcePredicate paste = (variant, tags) -> variant.type() == MekanizedContent.fluid("nutritional_paste");
        context.machineTypes().register(BasicMachines.base("nutritional_liquifier", 40_000)
                .storage(StorageDefinition.slots("input", 1, 64).filter(food))
                .storage(StorageDefinition.tank("paste", 10_000).filter(paste))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).group("items").build())
                .port(PortDefinition.builder("paste", "paste").direction(PortDirection.OUTPUT).filter(paste).group("fluid").build())
                .upgradeSlots(2, MekanizedRegistry::standardUpgrade)
                .behavior(MachineParts.powered(liquifier()))
                .build());
    }

    /** Turns one food into paste per {@link #TICKS}, paying the whole energy when it starts. */
    static @NotNull MachineBehavior.Factory liquifier() {
        return type -> context -> {
            ResourceType paste = MekanizedContent.fluid("nutritional_paste");
            long ticks = Math.max(1, context.modified(StandardModifiers.PROCESS_DURATION, context.options().get(TICKS)));
            long cost = context.modifiedCost(MekanizedContent.ENERGY, context.options().get(ENERGY_PER_TICK)) * ticks;
            StorageView food = context.storage("input").views().stream().filter(view -> !view.isEmpty()).findFirst().orElse(null);
            if (paste == null || food == null) {
                context.setActive(false);
                context.waitForInput();
                return;
            }
            long made = nutrition(food.variant()) * PASTE_PER_FOOD;
            try (Transaction transaction = context.transactions().openOuter()) {
                boolean done = MachineParts.pay(context.storage("energy"), cost, transaction)
                        && context.storage("input").extract(food.variant(), 1, transaction) == 1
                        && context.storage("paste").insert(paste.variant(), made, transaction) == made;
                if (!done) {
                    context.setActive(false);
                    context.wakeIn(20);
                    return;
                }
                transaction.commit();
            }
            context.setActive(true);
            context.wakeIn(ticks);
        };
    }
}
