package dev.alan.logistics;

import net.minecraft.world.SimpleContainer;

/** A block entity with one item slot shown by {@link DeviceMenu}, plus a few ints the screen displays. */
public interface DeviceEntity {
    SimpleContainer slot();

    /** The i-th extra number the menu synchronises (see {@link DeviceMenu.Kind#extras}). */
    int extra(int index);
}
