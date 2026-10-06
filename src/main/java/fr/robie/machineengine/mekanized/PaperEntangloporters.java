package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.mekanized.machine.Entangloporters;
import fr.robie.machineengine.mekanized.paper.persist.LineStore;
import fr.robie.machineengine.paper.PaperItems;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
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
        LineStore.write(owner, file, text);
    }

    private static void load() {
        if (file == null || owner == null) return;
        Base64.Decoder base64 = Base64.getDecoder();
        LineStore.load(owner, file, 5, parts -> {
            String frequency = new String(base64.decode(parts[0]), StandardCharsets.UTF_8);
            ResourceVariant variant;
            if (parts[2].equals("i")) {
                variant = PaperItems.toResource(ItemStack.deserializeBytes(base64.decode(parts[3]))).variant();
            } else {
                ResourceType type = type(Key.key(parts[3]));
                if (type == null) return;
                variant = type.variant();
            }
            Entangloporters.load(frequency, parts[1], variant, Long.parseLong(parts[4]));
        });
    }

    private static @Nullable ResourceType type(Key key) {
        if (key.equals(MekanizedContent.ENERGY.key())) return MekanizedContent.ENERGY;
        return MekanizedContent.resourceType(key);
    }
}
