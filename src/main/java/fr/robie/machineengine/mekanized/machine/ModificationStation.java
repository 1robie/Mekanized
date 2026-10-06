package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineType;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/** The modification station: it takes modules back out of gear (the Paper side does that); the block itself does nothing. */
final class ModificationStation {
    private ModificationStation() {
    }

    static void register(RegistrationContext context) {
        context.machineTypes().register(MachineType.builder(key("modification_station"))
                .behavior(type -> MachineContext::sleep).build());
    }
}
