/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.gametest.FaultlineGameTests;
import software.hibiscus.hibiscusfaultline.network.FaultlineNetwork;
import software.hibiscus.hibiscusfaultline.server.BuildServer;
import software.hibiscus.hibiscusfaultline.server.VeinMineHandler;
import org.slf4j.Logger;

@Mod(HibiscusFaultline.MOD_ID)
public final class HibiscusFaultline {
    public static final String MOD_ID = "hibiscusfaultline";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HibiscusFaultline(IEventBus modBus, ModContainer container) {
        container.registerConfig(syncedConfigType(), ServerConfig.SPEC);
        modBus.addListener(FaultlineNetwork::register);
        VeinMineHandler.register(NeoForge.EVENT_BUS);
        BuildServer.register(modBus, NeoForge.EVENT_BUS);
        FaultlineGameTests.register(modBus);
    }

    /**
     * The server-owned config type that is synced to clients. NeoForge 26.3.0.39-beta renamed it from
     * {@code SERVER} to {@code SYNCED}; looking it up by name keeps one jar working on both sides of
     * the rename, because the bytecode never refers to the constant that might be missing.
     */
    private static ModConfig.Type syncedConfigType() {
        try {
            return ModConfig.Type.valueOf("SYNCED");
        } catch (IllegalArgumentException renamedLater) {
            return ModConfig.Type.valueOf("SERVER");
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
