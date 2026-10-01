package dev.alan.logistics;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import net.minecraft.world.inventory.ContainerData;

/**
 * Menu data that carries a channel's energy (a long, in slots 0 and 1) plus a few ints. On the server the values are
 * computed live; on the client the same class just stores what the menu synchronised.
 */
public final class EnergyData implements ContainerData {
    private final LongSupplier energy;
    private final IntSupplier[] extras;
    private final int[] stored;

    /** Client side: {@code extras} plain ints after the energy. */
    public EnergyData(int extras) {
        this.energy = null;
        this.extras = null;
        this.stored = new int[2 + extras];
    }

    public EnergyData(LongSupplier energy, IntSupplier... extras) {
        this.energy = energy;
        this.extras = extras;
        this.stored = new int[2 + extras.length];
    }

    @Override public int get(int index) {
        if (energy == null) return stored[index];
        if (index < 2) {
            long value = energy.getAsLong();
            return index == 0 ? (int) value : (int) (value >> 32);
        }
        return extras[index - 2].getAsInt();
    }

    @Override public void set(int index, int value) { stored[index] = value; }

    @Override public int getCount() { return stored.length; }

    /** The energy in a menu's data; -1 means the block has no usable channel. */
    public static long read(ContainerData data) { return ((long) data.get(1) << 32) | (data.get(0) & 0xFFFFFFFFL); }
}
