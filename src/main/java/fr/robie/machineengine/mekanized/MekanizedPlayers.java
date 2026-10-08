package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
import fr.robie.machineengine.api.player.MachinePlayer;
import fr.robie.machineengine.api.player.PlayerDataKey;
import fr.robie.machineengine.api.player.PlayerSetting;
import fr.robie.machineengine.mekanized.machine.Radiation;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/** What Mekanized keeps per player (D37), and the settings players change with {@code /machinesettings}. */
public final class MekanizedPlayers {
    /** The radiation dose, in Sv; gone on death. Older versions kept it in the player's PDC under the same key. */
    public static final PlayerDataKey<Double> RADIATION_DOSE = PlayerDataKey.persistent(key("radiation_dose"),
                    VersionedCodec.builder(key("radiation_dose"), Codecs.DOUBLE).build(), 0.0)
            .resetOnDeath()
            .migratePdc("mekanized:radiation_dose", PersistentDataType.DOUBLE)
            .display(dose -> Radiation.format(dose, ""));

    /**
     * Whether the gravitational modulating unit let the player fly. Saved: the server keeps the player's
     * permission to fly across restarts, so Mekanized must remember it gave it.
     */
    public static final PlayerDataKey<Boolean> SUIT_FLIGHT = PlayerDataKey.persistent(key("suit_flight"),
            VersionedCodec.builder(key("suit_flight"), Codecs.BOOL).build(), false);

    /** Whether the motorized servos raised the player's step height; saved for the same reason. */
    public static final PlayerDataKey<Boolean> RAISED_STEP = PlayerDataKey.persistent(key("raised_step"),
            VersionedCodec.builder(key("raised_step"), Codecs.BOOL).build(), false);

    /** When the player last teleported (ms), for the teleporters' cooldown. */
    public static final PlayerDataKey<Long> LAST_TELEPORT = PlayerDataKey.session(key("last_teleport"), 0L);

    /** Show the MekaSuit helmet's radiation and dose readings in the action bar. */
    public static final PlayerSetting<Boolean> RADIATION_HUD = PlayerSetting.bool(key("radiation_hud"), true);

    private MekanizedPlayers() {
    }

    static void register(RegistrationContext context) {
        context.playerData().register(RADIATION_DOSE);
        context.playerData().register(SUIT_FLIGHT);
        context.playerData().register(RAISED_STEP);
        context.playerData().register(LAST_TELEPORT);
        context.playerSettings().register(RADIATION_HUD);
    }

    /** The player's data; empty while MachineEngine has not loaded it (just after a reload, or before join). */
    public static @NotNull Optional<MachinePlayer> of(@NotNull Player player) {
        return MachineEngine.get().players().find(player.getUniqueId());
    }
}
