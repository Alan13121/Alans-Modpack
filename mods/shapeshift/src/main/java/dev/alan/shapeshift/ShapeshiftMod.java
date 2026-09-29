package dev.alan.shapeshift;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.player.Player;
import com.mojang.serialization.Codec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShapeshiftMod implements ModInitializer {
    public static final String MOD_ID = "shapeshift";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }

    /** Forms this player has unlocked. Survives death; only the owner needs it. */
    public static final AttachmentType<Unlocks> UNLOCKS = AttachmentRegistry.create(id("unlocks"), b -> b
        .persistent(Unlocks.CODEC)
        .copyOnDeath()
        .syncWith(Unlocks.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
    /** Entity type id of the current form; absent while human. Everyone nearby needs it to render the player. */
    public static final AttachmentType<String> FORM = AttachmentRegistry.create(id("form"), b -> b
        .persistent(Codec.STRING)
        .syncWith(ByteBufCodecs.stringUtf8(256), AttachmentSyncPredicate.all()));
    /** Game time a creeper-form player lit their fuse; absent when not fusing. Synced so everyone sees the swelling. */
    public static final AttachmentType<Long> FUSE = AttachmentRegistry.create(id("fuse"), b -> b
        .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

    @Override public void onInitialize() {
        ShapeshiftConfig.load();
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("forms"), new FormDefinitions.Loader());

        PayloadTypeRegistry.serverboundPlay().register(RequestForm.TYPE, RequestForm.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FormSync.TYPE, FormSync.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UseAbility.TYPE, UseAbility.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseAbility.TYPE, (payload, context) -> Actives.use(context.player()));
        // Fires for each player on join and after /reload: send the definitions and re-apply edited forms.
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            ServerPlayNetworking.send(player, new FormSync(FormDefinitions.serverDefinitions()));
            Shapeshifter.refresh(player);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Reactions.onEntityLoad(entity));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) Shapeshifter.tick(player, server.getTickCount());
        });
        ServerPlayNetworking.registerGlobalReceiver(RequestForm.TYPE, (payload, context) -> {
            String id = payload.entityId();
            if (id.isEmpty()) Shapeshifter.request(context.player(), null);
            else Forms.type(id).ifPresent(type -> Shapeshifter.request(context.player(), type));
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Player || !(source.getEntity() instanceof ServerPlayer killer)) return;
            if (Shapeshifter.unlock(killer, entity.getType()))
                killer.sendSystemMessage(Component.translatable("shapeshift.unlocked", entity.getType().getDescription())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) Shapeshifter.clearStats(newPlayer);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            String saved = player.getAttached(FORM);
            if (saved == null) return;
            // The saved form may have been blocked or its mod removed since the last session.
            var type = Forms.type(saved).filter(Forms::isLivingForm);
            if (type.isEmpty() || ShapeshiftConfig.isBlocked(saved)) Shapeshifter.clearStats(player);
            else player.refreshDimensions();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Shapeshifter.forget(handler.getPlayer()));

        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) ->
            ShapeshiftCommands.register(dispatcher, context));
    }
}
