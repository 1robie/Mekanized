package fr.robie.mekanized.machine;

import fr.robie.machineengine.api.resource.ResourceVariant;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * How the modification station sees gear and module items (D40). Their modules and options live in
 * CraftEngine items; the Paper side reads and edits them ({@code PaperStationItems}), headless tests fake it.
 * Variants are {@code machineengine:item} resources; edits return the changed gear as a new variant.
 */
public interface StationItems {

    /** A module item's options. */
    record ModuleItem(@NotNull String name, int maxLevel, @NotNull Set<Key> fits, boolean toggleable) {
    }

    /** The modules of {@code gear}, or {@code null} if it is not gear that takes modules. */
    @Nullable List<GearRules.Installed> modules(@NotNull ResourceVariant gear);

    /** The module {@code item} is, or {@code null}. */
    @Nullable ModuleItem module(@NotNull ResourceVariant item);

    /** The options of the module {@code name}, or {@code null}. */
    @Nullable ModuleItem moduleNamed(@NotNull String name);

    /** One item of the module {@code name}, or {@code null}. */
    @Nullable ResourceVariant moduleItem(@NotNull String name);

    /** {@code gear} with one more level of {@code module}, or {@code null} if it is at its max. */
    @Nullable ResourceVariant install(@NotNull ResourceVariant gear, @NotNull String module);

    /** {@code gear} with up to {@code levels} levels of {@code module} taken out (energy over the new capacity lost). */
    @NotNull ResourceVariant remove(@NotNull ResourceVariant gear, @NotNull String module, int levels);

    /** {@code gear} with {@code module} switched on or off, or {@code null} if it cannot be switched. */
    @Nullable ResourceVariant toggle(@NotNull ResourceVariant gear, @NotNull String module);

    /** The energy {@code gear} would lose if {@code levels} levels of {@code module} came out. */
    long energyLost(@NotNull ResourceVariant gear, @NotNull String module, int levels);
}
