package com.github.fullavatar.chestlock;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ChestLock implements Component<ChunkStore> {

    public static final BuilderCodec<ChestLock> CODEC = BuilderCodec.builder(ChestLock.class, ChestLock::new)
        .append(
            new KeyedCodec<>("OwnerUuid", Codec.UUID_BINARY),
            (lock, ownerUuid) -> lock.ownerUuid = ownerUuid,
            lock -> lock.ownerUuid
        )
        .addValidator(Validators.nonNull())
        .add()
        .append(
            new KeyedCodec<>("AllowedPlayerUuids", new SetCodec<>(Codec.UUID_BINARY, HashSet::new, false)),
            (lock, allowedPlayerUuids) -> {
                lock.allowedPlayerUuids.clear();
                lock.allowedPlayerUuids.addAll(allowedPlayerUuids);
            },
            lock -> lock.allowedPlayerUuids
        )
        .addValidator(Validators.nonNull())
        .add()
        .append(
            new KeyedCodec<>("KnownPlayerNames", MapCodec.STRING_HASH_MAP_CODEC),
            (lock, names) -> {
                lock.knownPlayerNames.clear();
                lock.knownPlayerNames.putAll(names);
            },
            lock -> lock.knownPlayerNames
        )
        .addValidator(Validators.nonNull())
        .add()
        .build();

    private UUID ownerUuid;
    private final Set<UUID> allowedPlayerUuids = new HashSet<>();
    private final Map<String, String> knownPlayerNames = new HashMap<>();

    private ChestLock() {
    }

    public ChestLock(@Nonnull UUID ownerUuid) {
        this.ownerUuid = Objects.requireNonNull(ownerUuid);
    }

    @Nonnull
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Nonnull
    public Set<UUID> getAllowedPlayerUuids() {
        return Set.copyOf(allowedPlayerUuids);
    }

    @Nullable
    public String getKnownPlayerName(@Nonnull UUID playerUuid) {
        return knownPlayerNames.get(playerUuid.toString());
    }

    public boolean rememberPlayerName(@Nonnull UUID playerUuid, @Nonnull String username) {
        Objects.requireNonNull(username);
        if (!hasAccess(playerUuid) || username.isBlank()) return false;
        return !username.equals(knownPlayerNames.put(playerUuid.toString(), username));
    }

    @Nonnull
    public List<UUID> findAllowedPlayersByName(@Nonnull String username) {
        return allowedPlayerUuids.stream()
            .filter(uuid -> username.equalsIgnoreCase(getKnownPlayerName(uuid)))
            .sorted()
            .toList();
    }

    public boolean hasAccess(@Nonnull UUID playerUuid) {
        return ownerUuid.equals(playerUuid) || allowedPlayerUuids.contains(playerUuid);
    }

    public boolean addAllowedPlayer(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid);
        return !ownerUuid.equals(playerUuid) && allowedPlayerUuids.add(playerUuid);
    }

    public boolean removeAllowedPlayer(@Nonnull UUID playerUuid) {
        Objects.requireNonNull(playerUuid);
        if (ownerUuid.equals(playerUuid) || !allowedPlayerUuids.remove(playerUuid)) return false;
        knownPlayerNames.remove(playerUuid.toString());
        return true;
    }

    public boolean clearAllowedPlayers() {
        if (allowedPlayerUuids.isEmpty()) return false;
        allowedPlayerUuids.clear();
        knownPlayerNames.keySet().removeIf(key -> !key.equals(ownerUuid.toString()));
        return true;
    }

    @Nonnull
    @Override
    public ChestLock clone() {
        final var copy = new ChestLock(ownerUuid);
        copy.allowedPlayerUuids.addAll(allowedPlayerUuids);
        copy.knownPlayerNames.putAll(knownPlayerNames);
        return copy;
    }
}
