package dev.alan.mineworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Remembers which mine world this cauldron leads to (and an optional name). */
public final class WorldCauldronBlockEntity extends BlockEntity {
    private String worldId = "";
    private @Nullable Component name;

    public WorldCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(MineWorldMod.WORLD_CAULDRON_ENTITY, pos, state);
    }

    public String worldId() { return worldId; }
    public @Nullable Component customName() { return name; }

    public void setCustomName(@Nullable Component name) { this.name = name; setChanged(); }

    /** Gives this cauldron a brand new world if it has none (a creative-tab cauldron, or a freshly converted one). */
    public String assignWorld() {
        if (worldId.isEmpty() && level != null && level.getServer() != null) {
            worldId = MineWorldData.get(level.getServer()).createWorld();
            DynamicLevels.queue(worldId);
            setChanged();
        }
        return worldId;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("world", worldId);
        if (name != null) output.store("name", net.minecraft.network.chat.ComponentSerialization.CODEC, name);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        worldId = input.getStringOr("world", "");
        name = input.read("name", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!worldId.isEmpty()) components.set(MineWorldMod.WORLD_ID, worldId);
        if (name != null) components.set(DataComponents.CUSTOM_NAME, name);
    }

    @Override protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        worldId = components.getOrDefault(MineWorldMod.WORLD_ID, "");
        name = components.get(DataComponents.CUSTOM_NAME);
    }
}
