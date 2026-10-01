package dev.alan.logistics.client;

import dev.alan.logistics.ChannelSync;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The channels the server last said this player can use (see {@link ChannelSync}). */
public final class ClientChannels {
    private static List<ChannelSync.Entry> list = List.of();

    private ClientChannels() {}

    static void set(List<ChannelSync.Entry> entries) { list = List.copyOf(entries); }

    public static List<ChannelSync.Entry> all() { return list; }

    public static ChannelSync.@Nullable Entry get(int id) {
        for (ChannelSync.Entry e : list) if (e.id() == id) return e;
        return null;
    }

    /** "name (owner)" for a known channel, otherwise "#id"; empty for 0. */
    public static String label(int id) {
        if (id <= 0) return "";
        ChannelSync.Entry e = get(id);
        if (e == null) return "#" + id;
        return e.ownerName().isEmpty() ? e.name() : e.name() + " (" + e.ownerName() + ")";
    }

    /** The channel after (or before) {@code id} in the list, with 0 (= none) as the first stop; wraps around. */
    public static int step(int id, int direction) {
        int n = list.size() + 1;
        int index = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id() == id) index = i + 1;
        index = Math.floorMod(index + direction, n);
        return index == 0 ? 0 : list.get(index - 1).id();
    }
}
