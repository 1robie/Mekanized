package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.paper.MachineLocator;
import fr.robie.machineengine.paper.PaperWorlds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Bukkit blocks, worlds and players to MachineEngine positions and machines, shared by the Paper-side classes. */
public final class PaperWorld {
    private PaperWorld() {
    }

    /** The engine position of {@code block}. */
    public static WorldPosition position(Block block) {
        return WorldPosition.of(block.getWorld().getKey(), block.getX(), block.getY(), block.getZ());
    }

    /** The machine at {@code block}, or {@code null} (no machine, or no locator yet). It may not be running ({@code instance() == null}). */
    public static @Nullable LoadedMachine machine(Block block) {
        MachineLocator locator = MachineLocator.current();
        return locator == null ? null : locator.machine(position(block));
    }

    /** The machine at {@code position}, or {@code null}. */
    public static @Nullable LoadedMachine machine(WorldPosition position) {
        MachineLocator locator = MachineLocator.current();
        return locator == null ? null : locator.machine(position);
    }

    /** The loaded world of {@code position}, or {@code null}. */
    public static @Nullable World world(WorldPosition position) {
        return PaperWorlds.world(position.world());
    }

    /** The block corner of {@code position}, or {@code null} while its world is not loaded. */
    public static @Nullable Location location(WorldPosition position) {
        World world = world(position);
        return world == null ? null : new Location(world, position.position().x(), position.position().y(), position.position().z());
    }

    /** Gives {@code stack} to the player; what does not fit drops at their feet. */
    public static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }
}
