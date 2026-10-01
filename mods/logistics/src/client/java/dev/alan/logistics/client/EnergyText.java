package dev.alan.logistics.client;

import dev.alan.logistics.EnergyData;
import net.minecraft.network.chat.Component;

/** The one-line energy readout shown in terminals, devices and teleporters. */
final class EnergyText {
    private EnergyText() {}

    static Component of(long energy) {
        return energy < 0 ? Component.translatable("logistics.energy.none") : Component.translatable("logistics.energy", energy);
    }

    static Component of(EnergyData data) { return of(EnergyData.read(data)); }
}
