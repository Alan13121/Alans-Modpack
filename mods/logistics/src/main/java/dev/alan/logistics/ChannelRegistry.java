package dev.alan.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every named channel of the world. New channels get ids from {@link #FIRST_ID} up; ids below that are the old numbered
 * channels, which exist implicitly as public channels called "頻道 N" until someone renames them.
 */
public final class ChannelRegistry extends SavedData {
    public static final int FIRST_ID = 10000;
    public static final int MAX_PER_OWNER = 32;

    private static final Codec<ChannelRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("next_id", FIRST_ID).forGetter(r -> r.nextId),
        ChannelInfo.CODEC.listOf().optionalFieldOf("channels", List.of()).forGetter(r -> new ArrayList<>(r.channels.values()))
    ).apply(i, ChannelRegistry::new));

    public static final SavedDataType<ChannelRegistry> TYPE = new SavedDataType<>(
        LogisticsMod.id("channels"), ChannelRegistry::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private int nextId;
    private final Map<Integer, ChannelInfo> channels = new LinkedHashMap<>();

    public ChannelRegistry() { this(FIRST_ID, List.of()); }

    private ChannelRegistry(int nextId, List<ChannelInfo> list) {
        this.nextId = Math.max(FIRST_ID, nextId);
        for (ChannelInfo info : list) channels.put(info.id(), info);
    }

    public static ChannelRegistry get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }

    /** The server's registry once it has started, for code that only has a channel id (null on a client). */
    private static @Nullable ChannelRegistry current;

    public static void setCurrent(@Nullable ChannelRegistry registry) { current = registry; }

    /**
     * True when the id may be used: unknown registry (client) or old numbered channel or a channel that still exists.
     * Deleted channels stop working everywhere.
     */
    public static boolean isLive(int id) {
        if (id <= 0) return false;
        if (id < FIRST_ID || current == null) return true;
        return current.channels.containsKey(id);
    }

    public @Nullable ChannelInfo info(int id) {
        ChannelInfo info = channels.get(id);
        if (info != null) return info;
        if (id > 0 && id < FIRST_ID) return new ChannelInfo(id, "頻道 " + id, ChannelInfo.NO_OWNER, "", true);
        return null;
    }

    public boolean canUse(int id, UUID player, boolean op) {
        ChannelInfo info = info(id);
        return info != null && (info.isPublic() || op || info.owner().equals(player));
    }

    public boolean canManage(int id, UUID player, boolean op) {
        ChannelInfo info = info(id);
        return info != null && (op || (info.hasOwner() && info.owner().equals(player)));
    }

    /** Every channel the player may use among the stored ones, plus the given old numbered channels that are in use. */
    public List<ChannelInfo> visibleTo(UUID player, boolean op, Iterable<Integer> legacyInUse) {
        List<ChannelInfo> out = new ArrayList<>();
        for (ChannelInfo info : channels.values()) if (info.isPublic() || op || info.owner().equals(player)) out.add(info);
        for (int id : legacyInUse) {
            ChannelInfo info = id > 0 && id < FIRST_ID && !channels.containsKey(id) ? info(id) : null;
            if (info != null) out.add(info);
        }
        out.sort(java.util.Comparator.comparing((ChannelInfo i) -> i.name().toLowerCase(java.util.Locale.ROOT)).thenComparingInt(ChannelInfo::id));
        return out;
    }

    /** "ok", or a lang key describing why the name is rejected. */
    public String checkName(String name, UUID owner, int exceptId) {
        if (name.isEmpty() || name.length() > ChannelInfo.MAX_NAME) return "logistics.channel.error.name";
        for (ChannelInfo info : channels.values())
            if (info.id() != exceptId && info.owner().equals(owner) && info.name().equalsIgnoreCase(name)) return "logistics.channel.error.duplicate";
        return "ok";
    }

    public int countOwned(UUID owner) {
        int n = 0;
        for (ChannelInfo info : channels.values()) if (info.owner().equals(owner)) n++;
        return n;
    }

    public ChannelInfo create(String name, UUID owner, String ownerName, boolean isPublic) {
        ChannelInfo info = new ChannelInfo(nextId++, name, owner, ownerName, isPublic);
        channels.put(info.id(), info);
        setDirty();
        return info;
    }

    /** Stores an implicit old channel so it can be edited. */
    private void materialize(int id) {
        if (!channels.containsKey(id)) {
            ChannelInfo info = info(id);
            if (info != null) channels.put(id, info);
        }
    }

    public void rename(int id, String name) {
        materialize(id);
        ChannelInfo old = channels.get(id);
        if (old == null) return;
        channels.put(id, new ChannelInfo(id, name, old.owner(), old.ownerName(), old.isPublic()));
        setDirty();
    }

    public void setPublic(int id, boolean isPublic) {
        materialize(id);
        ChannelInfo old = channels.get(id);
        if (old == null) return;
        channels.put(id, new ChannelInfo(id, old.name(), old.owner(), old.ownerName(), isPublic));
        setDirty();
    }

    /** Removes a channel; old numbered channels cannot be deleted (they come back as the implicit public channel). */
    public boolean delete(int id) {
        if (id < FIRST_ID || channels.remove(id) == null) return false;
        setDirty();
        return true;
    }
}
