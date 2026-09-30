package dev.alan.mineworld.mixin;

import java.util.Map;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("levels") Map<ResourceKey<Level>, ServerLevel> mineworld$levels();
    @Accessor("storageSource") LevelStorageSource.LevelStorageAccess mineworld$storageSource();
    @Accessor("executor") Executor mineworld$executor();
}
