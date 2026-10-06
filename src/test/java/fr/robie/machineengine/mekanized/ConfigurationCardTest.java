package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.mekanized.machine.ConfigurationCard;
import fr.robie.machineengine.mekanized.machine.ConfigurationCard.PortSetting;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The text a configuration card holds, written and read back. */
class ConfigurationCardTest {
    private static final String CRUSHER = "mekanized:crusher";

    @Test
    void aCardIsTypePortsEjectAndRedstoneJoinedWithSemicolons() {
        Set<RelativeSide> two = EnumSet.of(RelativeSide.LEFT, RelativeSide.BACK);
        String text = ConfigurationCard.encode(CRUSHER, List.of(
                new PortSetting("input", PortDirection.INPUT, two),
                new PortSetting("output", PortDirection.OUTPUT, EnumSet.noneOf(RelativeSide.class))), true, "high");
        String joined = String.join("+", two.stream().map(Enum::name).toList());
        assertEquals(CRUSHER + ";port:input=INPUT:" + joined + ";port:output=OUTPUT:;eject=true;redstone=high", text);
    }

    @Test
    void aMachineWithoutEjectOrRedstoneWritesNeither() {
        assertEquals(CRUSHER, ConfigurationCard.encode(CRUSHER, List.of(), null, null));
        assertEquals(CRUSHER + ";eject=false", ConfigurationCard.encode(CRUSHER, List.of(), false, null));
    }

    @Test
    void whatIsWrittenIsReadBack() {
        Set<RelativeSide> some = EnumSet.of(RelativeSide.FRONT, RelativeSide.TOP);
        String text = ConfigurationCard.encode(CRUSHER, List.of(
                new PortSetting("input", PortDirection.INPUT, some),
                new PortSetting("output", PortDirection.OUTPUT, EnumSet.noneOf(RelativeSide.class))), false, "low");
        ConfigurationCard.Card card = ConfigurationCard.parse(text);
        assertEquals(CRUSHER, card.type());
        assertEquals(List.of(new PortSetting("input", PortDirection.INPUT, some),
                new PortSetting("output", PortDirection.OUTPUT, EnumSet.noneOf(RelativeSide.class))), card.ports());
        assertEquals(Boolean.FALSE, card.eject());
        assertEquals("low", card.redstone());
    }

    @Test
    void aCardWithoutSettingsReadsAsNone() {
        ConfigurationCard.Card card = ConfigurationCard.parse(CRUSHER);
        assertEquals(CRUSHER, card.type());
        assertEquals(List.of(), card.ports());
        assertNull(card.eject());
        assertNull(card.redstone());
    }

    @Test
    void anEditedCardSkipsWhatItCannotRead() {
        ConfigurationCard.Card card = ConfigurationCard.parse(CRUSHER
                + ";port:a=NOWHERE:LEFT"           // unknown direction: the part is skipped
                + ";port:b=INPUT:LEFT+SIDEWAYS"    // unknown side: the direction is kept, the sides are not
                + ";port:c=OUTPUT"                 // no sides at all: the same
                + ";port:d"                        // no equals sign: skipped
                + ";mystery=1"                     // not a part of ours
                + ";port:e=INPUT:RIGHT");
        assertEquals(List.of(
                new PortSetting("b", PortDirection.INPUT, null),
                new PortSetting("c", PortDirection.OUTPUT, null),
                new PortSetting("e", PortDirection.INPUT, EnumSet.of(RelativeSide.RIGHT))), card.ports());
    }

    @Test
    void laterEjectAndRedstoneWin() {
        ConfigurationCard.Card card = ConfigurationCard.parse(CRUSHER + ";eject=true;redstone=a;eject=false;redstone=b;eject=nonsense");
        assertEquals(Boolean.FALSE, card.eject(), "Boolean.parseBoolean: anything but true is false");
        assertEquals("b", card.redstone());
    }
}
