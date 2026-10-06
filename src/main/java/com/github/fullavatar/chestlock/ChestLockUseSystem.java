package com.github.fullavatar.chestlock;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ChestLockUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    @Nonnull
    private final ComponentType<ChunkStore, ChestLock> chestLockComponentType;

    public ChestLockUseSystem(@Nonnull ComponentType<ChunkStore, ChestLock> chestLockComponentType) {
        super(UseBlockEvent.Pre.class);
        this.chestLockComponentType = chestLockComponentType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull UseBlockEvent.Pre event) {
        if (event.isCancelled() || event.getInteractionType() != InteractionType.Use) return;

        final var playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null) return;

        final var world = store.getExternalData().getWorld();
        final var target = event.getTargetBlock();
        final var blockRef = BlockModule.getBlockEntity(world, target.x, target.y, target.z);
        if (blockRef == null) return;

        final var chunkStore = world.getChunkStore().getStore();
        if (chunkStore.getComponent(blockRef, ItemContainerBlock.getComponentType()) == null) return;

        final var lock = chunkStore.getComponent(blockRef, chestLockComponentType);
        checkAccess(event, playerRef, lock);
    }

    private static void checkAccess(@Nonnull UseBlockEvent.Pre event,
                                    @Nonnull PlayerRef playerRef,
                                    @Nullable ChestLock lock) {
        if (lock == null || lock.hasAccess(playerRef.getUuid())) return;

        event.setCancelled(true);
        playerRef.sendMessage(Message.raw("You do not have access to this locked container."));
    }
}
