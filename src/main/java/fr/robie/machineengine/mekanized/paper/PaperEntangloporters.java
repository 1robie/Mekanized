package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.mekanized.machine.Entangloporters;
import fr.robie.machineengine.mekanized.machine.SaveLines;
import fr.robie.machineengine.mekanized.paper.persist.LineStore;
import fr.robie.machineengine.api.paper.PaperItems;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Saves the quantum entangloporters' shared buffers in {@code plugins/Mekanized/entangloporters.txt}:
 * every 30 seconds when they changed, and when the plugin stops.
 */
public final class PaperEntangloporters {
    private static final long SAVE_TICKS = 600;
    private static @Nullable Path file;
    private static @Nullable Plugin owner;

    private PaperEntangloporters() {
    }

    public static void install(Plugin plugin) {
        owner = plugin;
        file = plugin.getDataFolder().toPath().resolve("entangloporters.txt");
        load();
        PaperWorld.scheduler().runTimer(() -> {
            if (Entangloporters.takeDirty()) save();
        }, SAVE_TICKS, SAVE_TICKS);
    }

    /** One line per stored resource: frequency, channel, {@code k} and a resource key or {@code i} and item bytes, amount. */
    public static synchronized void save() {
        if (file == null || owner == null) return;
        Base64.Encoder base64 = Base64.getEncoder();
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Map<String, Map<ResourceVariant, Long>>> frequency : Entangloporters.buffers().entrySet()) {
            synchronized (frequency.getValue()) {
                for (Map.Entry<String, Map<ResourceVariant, Long>> channel : frequency.getValue().entrySet()) {
                    for (Map.Entry<ResourceVariant, Long> stored : channel.getValue().entrySet()) {
                        ResourceVariant variant = stored.getKey();
                        boolean items = Entangloporters.isItems(variant.category());
                        String payload;
                        if (items) {
                            ItemStack item = PaperItems.toItemStack(variant, 1);
                            if (item == null) continue;
                            payload = base64.encodeToString(item.serializeAsBytes());
                        } else {
                            payload = variant.type().key().asString();
                        }
                        text.append(SaveLines.entangloporter(new SaveLines.Stored(frequency.getKey(), channel.getKey(), items, payload,
                                stored.getValue())));
                    }
                }
            }
        }
        LineStore.write(owner, file, text);
    }

    private static void load() {
        if (file == null || owner == null) return;
        Base64.Decoder base64 = Base64.getDecoder();
        LineStore.load(owner, file, SaveLines.ENTANGLOPORTER_FIELDS, parts -> {
            SaveLines.Stored stored = SaveLines.readEntangloporter(parts);
            ResourceVariant variant;
            if (stored.item()) {
                variant = PaperItems.toResource(ItemStack.deserializeBytes(base64.decode(stored.payload()))).variant();
            } else {
                ResourceType type = type(Key.key(stored.payload()));
                if (type == null) return;
                variant = type.variant();
            }
            Entangloporters.load(stored.frequency(), stored.channel(), variant, stored.amount());
        });
    }

    /** Through the registry, so keys saved before a rename (mekanized:energy) follow its aliases. */
    private static @Nullable ResourceType type(Key key) {
        return MachineEngine.get().registries().registry(Registries.RESOURCE_TYPE).get(key).orElse(null);
    }
}
