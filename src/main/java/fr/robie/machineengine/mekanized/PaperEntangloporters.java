package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.mekanized.machine.Entangloporters;
import fr.robie.machineengine.paper.PaperItems;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Saves the quantum entangloporters' shared buffers in {@code plugins/Mekanized/entangloporters.txt}:
 * every 30 seconds when they changed, and when the plugin stops.
 */
final class PaperEntangloporters {
    private static final long SAVE_TICKS = 600;
    private static @Nullable Path file;
    private static @Nullable Plugin owner;

    private PaperEntangloporters() {
    }

    static void install(Plugin plugin) {
        owner = plugin;
        file = plugin.getDataFolder().toPath().resolve("entangloporters.txt");
        load();
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (Entangloporters.takeDirty()) save();
        }, SAVE_TICKS, SAVE_TICKS);
    }

    /** One line per stored resource: frequency, channel, {@code k} and a resource key or {@code i} and item bytes, amount. */
    static synchronized void save() {
        if (file == null || owner == null) return;
        Base64.Encoder base64 = Base64.getEncoder();
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Map<String, Map<ResourceVariant, Long>>> frequency : Entangloporters.buffers().entrySet()) {
            String name = base64.encodeToString(frequency.getKey().getBytes(StandardCharsets.UTF_8));
            synchronized (frequency.getValue()) {
                for (Map.Entry<String, Map<ResourceVariant, Long>> channel : frequency.getValue().entrySet()) {
                    for (Map.Entry<ResourceVariant, Long> stored : channel.getValue().entrySet()) {
                        ResourceVariant variant = stored.getKey();
                        String payload;
                        if (Entangloporters.isItems(variant.category())) {
                            ItemStack item = PaperItems.toItemStack(variant, 1);
                            if (item == null) continue;
                            payload = "i|" + base64.encodeToString(item.serializeAsBytes());
                        } else {
                            payload = "k|" + variant.type().key().asString();
                        }
                        text.append(name).append('|').append(channel.getKey()).append('|').append(payload).append('|')
                                .append(stored.getValue()).append('\n');
                    }
                }
            }
        }
        PaperFiles.write(owner, file, text);
    }

    private static void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        Base64.Decoder base64 = Base64.getDecoder();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|");
                if (parts.length != 5) continue;
                String frequency = new String(base64.decode(parts[0]), StandardCharsets.UTF_8);
                ResourceVariant variant;
                if (parts[2].equals("i")) {
                    variant = PaperItems.toResource(ItemStack.deserializeBytes(base64.decode(parts[3]))).variant();
                } else {
                    ResourceType type = type(Key.key(parts[3]));
                    if (type == null) continue;
                    variant = type.variant();
                }
                Entangloporters.load(frequency, parts[1], variant, Long.parseLong(parts[4]));
            }
        } catch (IOException | RuntimeException e) {
            if (owner != null) owner.getLogger().warning("Could not read " + file + ": " + e.getMessage());
        }
    }

    private static @Nullable ResourceType type(Key key) {
        if (key.equals(MekanizedContent.ENERGY.key())) return MekanizedContent.ENERGY;
        if (key.value().startsWith("fluid/")) return MekanizedContent.fluid(key.value().substring("fluid/".length()));
        return MekanizedContent.chemical(key.value());
    }
}
