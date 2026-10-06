package com.github.fullavatar.chestlock;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.util.InteractionValidation;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChestLockCommand extends AbstractPlayerCommand {

    @Nonnull
    private final ComponentType<ChunkStore, ChestLock> chestLockComponentType;

    public ChestLockCommand(@Nonnull ComponentType<ChunkStore, ChestLock> chestLockComponentType) {
        super("chestlock", "Show whether the container you are looking at is locked.");
        this.chestLockComponentType = chestLockComponentType;
        requireNoPermission();
        addSubCommand(new LockCommand());
        addSubCommand(new PlayerAccessCommand(true));
        addSubCommand(new PlayerAccessCommand(false));
        addSubCommand(new RemoveAllCommand());
        addSubCommand(new ListCommand());
        addSubCommand(new UnlockCommand());
    }

    @Nonnull
    @Override
    public Message getUsageString(@Nonnull CommandSender sender) {
        return Message.raw("""
            ChestLock - look at a container within reach.
            /chestlock
              Show whether the container is locked and who owns it.
            /chestlock lock
              Claim an unlocked container.
            /chestlock add <connected-player>
              Owner only: add a connected player.
            /chestlock remove <allowed-player-or-uuid>
              Owner only: remove an invited player, even offline.
            /chestlock remove-all
              Owner only: remove all guests and keep the lock.
            /chestlock list
              Show the owner; players with access also see the guests.
            /chestlock unlock [--force] [--confirm]
              Owner only, or an operator with --force. Add --confirm to unlock.
            --help displays this help.
            """);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        final var target = findContainerTarget(context, store, ref, world);
        if (target == null) return;

        showLockStatus(context, target.lock());
    }

    @Nullable
    private ContainerTarget findContainerTarget(@Nonnull CommandContext context,
                                                @Nonnull Store<EntityStore> store,
                                                @Nonnull Ref<EntityStore> ref,
                                                @Nonnull World world) {
        final var blockRef = getTargetContainer(ref, store, world);
        if (blockRef == null) {
            context.sendMessage(Message.raw("Invalid target: look at an ItemContainerBlock within reach."));
            return null;
        }

        final var chunkStore = world.getChunkStore().getStore();
        final var existingLock = chunkStore.getComponent(blockRef, chestLockComponentType);
        return new ContainerTarget(chunkStore, blockRef, existingLock);
    }

    private static void showLockStatus(@Nonnull CommandContext context, @Nullable ChestLock lock) {
        context.sendMessage(Message.raw(lock == null
            ? "This chest is not locked."
            : "Chest locked by " + describePlayer(lock.getOwnerUuid(), lock)));
    }

    private void lockContainer(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull ContainerTarget target) {
        if (target.lock() != null) {
            context.sendMessage(Message.raw("Chest already locked by " + describePlayer(target.lock().getOwnerUuid(), target.lock())));
            return;
        }

        final var blockStateInfo = getSaveableBlockStateInfo(context, target.store(), target.ref());
        if (blockStateInfo == null) return;

        final var lock = new ChestLock(playerRef.getUuid());
        lock.rememberPlayerName(playerRef.getUuid(), playerRef.getUsername());
        target.store().addComponent(target.ref(), chestLockComponentType, lock);
        blockStateInfo.markNeedsSaving(target.store());
        context.sendMessage(Message.raw("Chest locked by " + describePlayer(playerRef.getUuid())));
    }

    private record ContainerTarget(@Nonnull Store<ChunkStore> store,
                                   @Nonnull Ref<ChunkStore> ref,
                                   @Nullable ChestLock lock) {
    }

    private abstract class ContainerCommand extends AbstractPlayerCommand {

        private ContainerCommand(@Nonnull String name, @Nonnull String description) {
            super(name, description);
            requireNoPermission();
        }

        @Override
        protected final void execute(@Nonnull CommandContext context,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull Ref<EntityStore> ref,
                                     @Nonnull PlayerRef playerRef,
                                     @Nonnull World world) {
            final var target = findContainerTarget(context, store, ref, world);
            if (target == null) return;

            if (target.lock() != null) {
                rememberOnlinePlayerNames(target.store(), target.ref(), target.lock());
            }
            execute(context, playerRef, target);
        }

        protected abstract void execute(@Nonnull CommandContext context,
                                        @Nonnull PlayerRef playerRef,
                                        @Nonnull ContainerTarget target);
    }

    private final class LockCommand extends ContainerCommand {

        private LockCommand() {
            super("lock", "Claim an unlocked container.");
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull ContainerTarget target) {
            lockContainer(context, playerRef, target);
        }
    }

    private final class PlayerAccessCommand extends ContainerCommand {

        private final boolean add;
        @Nonnull
        private final RequiredArg<String> playerArg;

        private PlayerAccessCommand(boolean add) {
            super(add ? "add" : "remove", add
                ? "Allow a connected player to access your container."
                : "Remove an invited player's access, even offline.");
            this.add = add;
            playerArg = withRequiredArg("player", add
                ? "The exact nickname of a connected player."
                : "An invited player's last known nickname or UUID.",
                add
                    ? new PlayerNameArgumentType("Connected player", "The exact nickname of a connected player.", ChestLockCommand::connectedPlayerNames)
                    : new PlayerNameArgumentType("Allowed player", "An invited player's last known nickname or UUID.", ChestLockCommand.this::allowedPlayerNames));
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull ContainerTarget target) {
            changePlayerAccess(context, target.store(), target.ref(), playerRef, target.lock(), playerArg.get(context), add);
        }
    }

    private final class RemoveAllCommand extends ContainerCommand {

        private RemoveAllCommand() {
            super("remove-all", "Owner only: remove all guests and keep the lock.");
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull ContainerTarget target) {
            removeAllPlayerAccess(context, target.store(), target.ref(), playerRef, target.lock());
        }
    }

    private final class ListCommand extends ContainerCommand {

        private ListCommand() {
            super("list", "Show the owner and, if you have access, the invited players.");
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull ContainerTarget target) {
            listPlayerAccess(context, playerRef, target.lock());
        }
    }

    private final class UnlockCommand extends ContainerCommand {

        @Nonnull
        private final FlagArg forceArg = withFlagArg("force", "Force an unlock as an operator.");
        @Nonnull
        private final FlagArg confirmArg = withFlagArg("confirm", "Confirm removal of the lock and all guest access.");

        private UnlockCommand() {
            super("unlock", "Remove your lock, or force an unlock as an operator; requires --confirm.");
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull ContainerTarget target) {
            unlockContainer(context, target.store(), target.ref(), playerRef, target.lock(), forceArg.get(context), confirmArg.get(context));
        }
    }

    private static void listPlayerAccess(@Nonnull CommandContext context,
                                         @Nonnull PlayerRef playerRef,
                                         @Nullable ChestLock lock) {
        if (lock == null) {
            context.sendMessage(Message.raw("This chest is not locked."));
            return;
        }
        final var message = new StringBuilder("Owner: ").append(describePlayer(lock.getOwnerUuid(), lock));
        if (!lock.hasAccess(playerRef.getUuid())) {
            context.sendMessage(Message.raw(message.toString()));
            return;
        }

        final var guests = lock.getAllowedPlayerUuids().stream()
            .filter(uuid -> !uuid.equals(lock.getOwnerUuid()))
            .map(uuid -> describePlayer(uuid, lock))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        message.append("\nAllowed players:");
        if (guests.isEmpty()) {
            message.append(" none");
        } else {
            for (String guest : guests) {
                message.append("\n- ").append(guest);
            }
        }
        context.sendMessage(Message.raw(message.toString()));
    }

    private static void removeAllPlayerAccess(@Nonnull CommandContext context,
                                              @Nonnull Store<ChunkStore> chunkStore,
                                              @Nonnull Ref<ChunkStore> blockRef,
                                              @Nonnull PlayerRef playerRef,
                                              @Nullable ChestLock lock) {
        if (lock == null) {
            context.sendMessage(Message.raw("This chest is not locked."));
            return;
        }
        if (!lock.getOwnerUuid().equals(playerRef.getUuid())) {
            context.sendMessage(Message.raw("You cannot remove players from this chest: it is locked by " + describePlayer(lock.getOwnerUuid())));
            return;
        }
        if (lock.getAllowedPlayerUuids().isEmpty()) {
            context.sendMessage(Message.raw("No guests have access to this chest."));
            return;
        }

        final var blockStateInfo = getSaveableBlockStateInfo(context, chunkStore, blockRef);
        if (blockStateInfo == null) return;

        lock.clearAllowedPlayers();
        blockStateInfo.markNeedsSaving(chunkStore);
        context.sendMessage(Message.raw("All guest access removed. This chest remains locked."));
    }

    private void unlockContainer(@Nonnull CommandContext context,
                                 @Nonnull Store<ChunkStore> chunkStore,
                                 @Nonnull Ref<ChunkStore> blockRef,
                                 @Nonnull PlayerRef playerRef,
                                 @Nullable ChestLock lock,
                                 boolean force,
                                 boolean confirm) {
        if (lock == null) {
            context.sendMessage(Message.raw("This chest is not locked."));
            return;
        }
        if (force) {
            if (!PermissionsModule.get().getGroupsForUser(playerRef.getUuid()).contains(HytalePermissionsProvider.GROUP_ADMIN)) {
                context.sendMessage(Message.raw("Only an operator can force-unlock this chest."));
                return;
            }
        } else if (!lock.getOwnerUuid().equals(playerRef.getUuid())) {
            context.sendMessage(Message.raw("You cannot unlock this chest: it is locked by " + describePlayer(lock.getOwnerUuid())));
            return;
        }
        if (!confirm) {
            context.sendMessage(Message.raw("Unlocking removes the lock and all guest access. Confirm with /chestlock unlock"
                + (force ? " --force" : "") + " --confirm."));
            return;
        }

        final var blockStateInfo = getSaveableBlockStateInfo(context, chunkStore, blockRef);
        if (blockStateInfo == null) return;

        chunkStore.removeComponent(blockRef, chestLockComponentType);
        blockStateInfo.markNeedsSaving(chunkStore);
        context.sendMessage(Message.raw("Chest unlocked."));
    }

    private static void changePlayerAccess(@Nonnull CommandContext context,
                                           @Nonnull Store<ChunkStore> chunkStore,
                                           @Nonnull Ref<ChunkStore> blockRef,
                                           @Nonnull PlayerRef playerRef,
                                           @Nullable ChestLock lock,
                                           @Nonnull String username,
                                           boolean add) {
        if (lock == null) {
            context.sendMessage(Message.raw(add
                ? "This chest is not locked. Lock it before adding players."
                : "This chest is not locked."));
            return;
        }
        if (!lock.getOwnerUuid().equals(playerRef.getUuid())) {
            context.sendMessage(Message.raw("You cannot " + (add ? "add players to" : "remove players from")
                + " this chest: it is locked by " + describePlayer(lock.getOwnerUuid())));
            return;
        }

        final UUID targetUuid;
        final String targetName;
        if (add) {
            final var allowedPlayer = Universe.get().getPlayerByUsername(username, NameMatching.EXACT_IGNORE_CASE);
            if (allowedPlayer == null) {
                context.sendMessage(Message.raw("No connected player named " + username + "."));
                return;
            }
            targetUuid = allowedPlayer.getUuid();
            targetName = allowedPlayer.getUsername();
        } else {
            targetUuid = resolveInvitedPlayer(context, lock, username);
            if (targetUuid == null) return;
            targetName = describePlayer(targetUuid, lock);
        }
        if (!add && lock.getOwnerUuid().equals(targetUuid)) {
            context.sendMessage(Message.raw("The owner always has access. Use /chestlock unlock to remove the lock."));
            return;
        }
        if (lock.hasAccess(targetUuid) == add) {
            context.sendMessage(Message.raw("Player " + targetName + (add
                ? " already has access to this chest."
                : " does not have access to this chest.")));
            return;
        }

        final var blockStateInfo = getSaveableBlockStateInfo(context, chunkStore, blockRef);
        if (blockStateInfo == null) return;

        if (add) {
            lock.addAllowedPlayer(targetUuid);
            lock.rememberPlayerName(targetUuid, targetName);
        } else {
            lock.removeAllowedPlayer(targetUuid);
        }
        blockStateInfo.markNeedsSaving(chunkStore);
        context.sendMessage(Message.raw(add
            ? "Player " + targetName + " can now access this chest."
            : "Access removed for player " + targetName + "."));
    }

    @Nullable
    private static UUID resolveInvitedPlayer(@Nonnull CommandContext context,
                                             @Nonnull ChestLock lock,
                                             @Nonnull String username) {
        try {
            return UUID.fromString(username);
        } catch (IllegalArgumentException ignored) {
            // A nickname is resolved against this container's invitations, not a remote profile service.
        }
        final var matches = lock.findAllowedPlayersByName(username);
        if (matches.size() > 1) {
            context.sendMessage(Message.raw("Several invited players match " + username + ". Use a UUID: " + matches));
            return null;
        }
        if (!matches.isEmpty()) return matches.getFirst();

        final var onlinePlayer = Universe.get().getPlayerByUsername(username, NameMatching.EXACT_IGNORE_CASE);
        if (onlinePlayer != null) return onlinePlayer.getUuid();

        context.sendMessage(Message.raw("No invited player named " + username + ". Use /chestlock list to view the available names or UUIDs."));
        return null;
    }

    private static void rememberOnlinePlayerNames(@Nonnull Store<ChunkStore> chunkStore,
                                                  @Nonnull Ref<ChunkStore> blockRef,
                                                  @Nonnull ChestLock lock) {
        final var blockStateInfo = chunkStore.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null || !blockStateInfo.getSectionRef().isValid()) return;

        boolean changed = false;
        final var owner = Universe.get().getPlayer(lock.getOwnerUuid());
        if (owner != null) {
            changed = lock.rememberPlayerName(owner.getUuid(), owner.getUsername());
        }
        for (UUID uuid : lock.getAllowedPlayerUuids()) {
            final var player = Universe.get().getPlayer(uuid);
            if (player != null) {
                changed |= lock.rememberPlayerName(uuid, player.getUsername());
            }
        }
        if (changed) blockStateInfo.markNeedsSaving(chunkStore);
    }

    @Nonnull
    private static List<String> connectedPlayerNames(@Nonnull CommandSender sender) {
        return Universe.get().getPlayers().stream().map(PlayerRef::getUsername).toList();
    }

    @Nonnull
    private List<String> allowedPlayerNames(@Nonnull CommandSender sender) {
        if (!(sender instanceof PlayerRef playerRef)) return List.of();
        final var worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) return List.of();
        final var world = Universe.get().getWorld(worldUuid);
        if (world == null) return List.of();
        if (world.isInThread()) return allowedPlayerNamesInWorld(playerRef, world);

        // Client suggestion requests run off the world thread; bound the wait during world changes or lag.
        final var request = new CompletableFuture<List<String>>();
        try {
            world.execute(() -> {
                if (request.isDone()) return;
                try {
                    request.complete(allowedPlayerNamesInWorld(playerRef, world));
                } catch (RuntimeException exception) {
                    request.completeExceptionally(exception);
                }
            });
            return request.get(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            // A missing suggestion must not fail a packet handler when the player changes worlds.
        } finally {
            request.cancel(false);
        }
        return List.of();
    }

    @Nonnull
    private List<String> allowedPlayerNamesInWorld(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        final var ref = playerRef.getReference();
        final var store = world.getEntityStore().getStore();
        if (ref == null || !ref.isValid() || ref.getStore() != store) return List.of();
        final var blockRef = getTargetContainer(ref, store, world);
        if (blockRef == null) return List.of();
        final var lock = world.getChunkStore().getStore().getComponent(blockRef, chestLockComponentType);
        if (lock == null || !lock.getOwnerUuid().equals(playerRef.getUuid())) return List.of();
        return lock.getAllowedPlayerUuids().stream()
            .filter(uuid -> !uuid.equals(lock.getOwnerUuid()))
            .map(uuid -> describePlayer(uuid, lock))
            .toList();
    }

    @Nullable
    private static BlockModule.BlockStateInfo getSaveableBlockStateInfo(@Nonnull CommandContext context,
                                                                       @Nonnull Store<ChunkStore> chunkStore,
                                                                       @Nonnull Ref<ChunkStore> blockRef) {
        final var blockStateInfo = chunkStore.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null || !blockStateInfo.getSectionRef().isValid()) {
            context.sendMessage(Message.raw("Unable to save the lock change for this container."));
            return null;
        }
        return blockStateInfo;
    }

    @Nullable
    private static Ref<ChunkStore> getTargetContainer(@Nonnull Ref<EntityStore> ref,
                                                     @Nonnull Store<EntityStore> store,
                                                     @Nonnull World world) {
        final var target = TargetUtil.getTargetBlockOrigin(
            ref, blockId -> blockId == BlockType.EMPTY_ID, InteractionManager.MAX_REACH_DISTANCE, store);
        if (target == null) return null;

        final var heldItem = InventoryComponent.getItemInHand(store, ref);
        if (!InteractionValidation.canPlayerInteractWithBlock(ref, store, heldItem, target)) return null;

        final var blockRef = BlockModule.getBlockEntity(world, target.x, target.y, target.z);
        if (blockRef == null) return null;

        final var chunkStore = world.getChunkStore().getStore();
        return chunkStore.getComponent(blockRef, ItemContainerBlock.getComponentType()) != null ? blockRef : null;
    }

    @Nonnull
    private static String describePlayer(@Nonnull UUID playerUuid) {
        return describePlayer(playerUuid, null);
    }

    @Nonnull
    private static String describePlayer(@Nonnull UUID playerUuid, @Nullable ChestLock lock) {
        final var player = Universe.get().getPlayer(playerUuid);
        if (player != null) return player.getUsername();
        final var knownName = lock != null ? lock.getKnownPlayerName(playerUuid) : null;
        return knownName != null && !knownName.isBlank() ? knownName : playerUuid.toString();
    }
}
