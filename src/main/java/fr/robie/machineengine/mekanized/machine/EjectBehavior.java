package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.world.RelativeSide;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism's auto-eject: while {@link #EJECT} is on, a machine pushes what its output ports hold out
 * of the sides those ports are configured on (side configuration), into the machines there. Energy
 * ports are left alone: generators and cubes push energy themselves.
 */
public final class EjectBehavior implements MachineBehavior {
    public static final MachineDataKey<Boolean> EJECT = DataKeys.bool("eject", false);
    /** How often an idle machine with outputs left tries again (neighbors freeing space do not wake it). */
    private static final long RETRY_TICKS = 20;

    private final MachineBehavior inner;

    private EjectBehavior(MachineBehavior inner) {
        this.inner = inner;
    }

    public static @NotNull MachineBehavior.Factory of(@NotNull MachineBehavior.Factory inner) {
        return type -> new EjectBehavior(inner.create(type));
    }

    @Override
    public void tick(@NotNull MachineContext context) {
        this.inner.tick(context);
        if (!context.get(EJECT)) return;
        boolean left = false;
        for (PortDefinition port : context.type().ports()) {
            if ("energy".equals(port.group()) || !context.portDirection(port.id()).allowsExtraction()) continue;
            for (RelativeSide side : context.portSides(port.id())) {
                context.push(side, port.storage(), variant -> true, Long.MAX_VALUE);
            }
            left |= context.storage(port.storage()).views().stream().anyMatch(view -> !view.isEmpty());
        }
        // Idle with outputs left: look again later. A running process keeps its own schedule.
        if (left && context.process().current() == null) context.wakeIn(RETRY_TICKS);
    }

    @Override
    public void onWake(@NotNull MachineContext context, @NotNull WakeReason reason) {
        this.inner.onWake(context, reason);
    }

    @Override
    public void onLoad(@NotNull MachineContext context) {
        this.inner.onLoad(context);
    }

    @Override
    public void onUnload(@NotNull MachineContext context) {
        this.inner.onUnload(context);
    }

    @Override
    public void catchUp(@NotNull MachineContext context, long ticks) {
        this.inner.catchUp(context, ticks);
    }

    @Override
    public void onRemove(@NotNull MachineContext context) {
        this.inner.onRemove(context);
    }
}
