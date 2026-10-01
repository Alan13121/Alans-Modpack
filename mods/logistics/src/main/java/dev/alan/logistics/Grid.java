package dev.alan.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Every loaded channel block, antenna and teleporter on the server, in any dimension. Block entities add themselves
 * when they are loaded and remove themselves when they are removed or their chunk unloads, so channel lookups never
 * have to search the world.
 */
public final class Grid {
    private static final Set<ChannelBlockEntity> CHANNELS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<AntennaBlockEntity> ANTENNAS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<TeleporterBlockEntity> TELEPORTERS = Collections.newSetFromMap(new IdentityHashMap<>());

    private Grid() {}

    public static void add(ChannelBlockEntity be) { CHANNELS.add(be); }
    public static void remove(ChannelBlockEntity be) { CHANNELS.remove(be); }
    public static void add(AntennaBlockEntity be) { ANTENNAS.add(be); }
    public static void remove(AntennaBlockEntity be) { ANTENNAS.remove(be); }
    public static void add(TeleporterBlockEntity be) { TELEPORTERS.add(be); }
    public static void remove(TeleporterBlockEntity be) { TELEPORTERS.remove(be); }

    public static List<ChannelBlockEntity> channelBlocks() { return new ArrayList<>(CHANNELS); }
    public static List<AntennaBlockEntity> antennas() { return new ArrayList<>(ANTENNAS); }
    public static List<TeleporterBlockEntity> teleporters() { return new ArrayList<>(TELEPORTERS); }

    public static void clear() {
        CHANNELS.clear();
        ANTENNAS.clear();
        TELEPORTERS.clear();
    }
}
