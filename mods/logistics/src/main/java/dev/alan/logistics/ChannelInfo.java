package dev.alan.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** A named channel. {@code owner} is {@link #NO_OWNER} for channels that came from the old numbered ones. */
public record ChannelInfo(int id, String name, UUID owner, String ownerName, boolean isPublic) {
    public static final UUID NO_OWNER = new UUID(0, 0);
    public static final int MAX_NAME = 24;

    public static final Codec<ChannelInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("id").forGetter(ChannelInfo::id),
        Codec.STRING.fieldOf("name").forGetter(ChannelInfo::name),
        UUIDUtil.CODEC.optionalFieldOf("owner", NO_OWNER).forGetter(ChannelInfo::owner),
        Codec.STRING.optionalFieldOf("owner_name", "").forGetter(ChannelInfo::ownerName),
        Codec.BOOL.optionalFieldOf("public", true).forGetter(ChannelInfo::isPublic)
    ).apply(i, ChannelInfo::new));

    public boolean hasOwner() { return !NO_OWNER.equals(owner); }

    /** Name as shown in lists: "name (owner)". */
    public String label() { return hasOwner() && !ownerName.isEmpty() ? name + " (" + ownerName + ")" : name; }
}
