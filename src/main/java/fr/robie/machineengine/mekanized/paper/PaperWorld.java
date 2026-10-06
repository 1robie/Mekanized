package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.api.world.WorldPosition;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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

    /** The running machine at {@code block}, or {@code null} (none, a placeholder, or MachineEngine not enabled yet). Owner thread. */
    public static @Nullable MachineInstance machine(Block block) {
        return machine(position(block));
    }

    /** The running machine at {@code position}, or {@code null}. Owner thread. */
    public static @Nullable MachineInstance machine(WorldPosition position) {
        try {
            return MachineEngine.get().machineAt(position).orElse(null);
        } catch (IllegalStateException e) {
            return null; // MachineEngine has not enabled yet
        }
    }

    /** Whether the current thread owns {@code position} (Folia: its region; Paper: the main thread); false while its world is not loaded. */
    public static boolean isOwnedByCurrentThread(WorldPosition position) {
        World world = world(position);
        return world != null && Bukkit.isOwnedByCurrentRegion(world, position.position().x() >> 4, position.position().z() >> 4);
    }

    /** The loaded world with this key, or {@code null}. */
    public static @Nullable World world(Key key) {
        return Bukkit.getWorld(new NamespacedKey(key.namespace(), key.value()));
    }

    /** The loaded world of {@code position}, or {@code null}. */
    public static @Nullable World world(WorldPosition position) {
        return world(position.world());
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
