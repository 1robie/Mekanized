package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.data.DataMap;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.api.resource.Fluids;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mekanized uses MachineEngine's energy, water and lava (D29); its old keys still load. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class EngineResourcesTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void mekanizedUsesTheEngineResources() {
        assertSame(Energy.type(), MekanizedContent.ENERGY);
        assertSame(Fluids.category(), MekanizedContent.FLUID);
        assertSame(Fluids.water(), MekanizedContent.fluid("water"));
        assertSame(Fluids.lava(), MekanizedContent.fluid("lava"));
        assertTrue(Fluids.isFluid(MekanizedContent.fluid("brine")), "Mekanized's own fluids are in the global category");
        assertEquals(Key.key("mekanized", "fluid/brine"), MekanizedContent.fluid("brine").key());
        var types = this.engine.registries().registry(Registries.RESOURCE_TYPE);
        assertFalse(types.keys().contains(Key.key("mekanized", "energy")));
        assertFalse(types.keys().contains(Key.key("mekanized", "fluid/water")));
    }

    @Test
    void everyChemicalAndFluidHasAMenuColor() {
        var types = this.engine.registries().registry(Registries.RESOURCE_TYPE);
        for (Key key : types.keys()) {
            ResourceType type = types.getOrThrow(key);
            boolean ours = type.category() == MekanizedContent.CHEMICAL
                    || type.category() == MekanizedContent.FLUID && key.namespace().equals(MekanizedContent.NAMESPACE);
            if (ours) assertNotNull(type.color(), key + " has no color in chemicals.txt or fluids.txt");
        }
        assertEquals(0xB30505, types.getOrThrow(Key.key("mekanized", "redstone")).color().intValue(), "Mekanism's redstone tint");
        assertEquals(0x0D1455, MekanizedContent.fluid("heavy_water").color().intValue());
        assertEquals(0xFF7E1F, MekanizedContent.HEAT.color().intValue(), "heat is Mekanism's orange, on its type");
        assertEquals(0x3F76E4, Fluids.water().color().intValue(), "water's color is config.yml's default, on its type");
    }

    @Test
    void theOldKeysAreAliases() {
        var types = this.engine.registries().registry(Registries.RESOURCE_TYPE);
        assertSame(Energy.type(), types.getOrThrow(Key.key("mekanized", "energy")));
        assertSame(Fluids.water(), types.getOrThrow(Key.key("mekanized", "fluid/water")));
        assertSame(Fluids.lava(), types.getOrThrow(Key.key("mekanized", "fluid/lava")));
        assertSame(MekanizedRegistry.ENERGY_COST,
                this.engine.registries().registry(Registries.MODIFIER_TYPE).getOrThrow(Key.key("mekanized", "process_cost/energy")));
    }

    @Test
    void energyAndWaterSavedUnderTheOldKeysLoad() {
        var codec = this.engine.resourceCodecs().variant();
        ResourceVariant energy = codec.decode(DataMap.builder().put("type", "mekanized:energy").build()).value();
        ResourceVariant water = codec.decode(DataMap.builder().put("type", "mekanized:fluid/water").build()).value();
        assertSame(Energy.type(), energy.type());
        assertSame(Fluids.water(), water.type());
        assertEquals(DataMap.builder().put("type", "machineengine:energy").build(), codec.encode(energy), "saved again under the new key");
    }
}
