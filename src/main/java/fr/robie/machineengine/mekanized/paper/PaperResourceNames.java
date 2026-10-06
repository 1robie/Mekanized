package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.mekanized.MekanizedContent;

/** Names of Mekanized resources in menus. */
public final class PaperResourceNames {
    private PaperResourceNames() {
    }

    /** Menu names of Mekanized resources, translated on each player's client (Mekanism's language files). */
    public static String name(ResourceVariant variant) {
        var type = variant.type();
        if (type == MekanizedContent.ENERGY) return "Energy";
        String value = type.key().value();
        if (type.category() == MekanizedContent.CHEMICAL) return "<lang:chemical.mekanized." + value + ">";
        if (type.category() == MekanizedContent.FLUID) {
            String fluid = value.substring(value.indexOf('/') + 1);
            return fluid.equals("water") || fluid.equals("lava") ? "<lang:block.minecraft." + fluid + ">" : "<lang:block.mekanized." + fluid + ">";
        }
        return null;
    }
}
