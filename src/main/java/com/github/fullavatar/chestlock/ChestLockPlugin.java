package com.github.fullavatar.chestlock;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class ChestLockPlugin extends JavaPlugin {

    public ChestLockPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        final var chestLockComponentType = getChunkStoreRegistry().registerComponent(ChestLock.class, "ChestLock", ChestLock.CODEC);
        getEntityStoreRegistry().registerSystem(new ChestLockUseSystem(chestLockComponentType));
        getCommandRegistry().registerCommand(new ChestLockCommand(chestLockComponentType));
    }
}
