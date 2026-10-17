package com.cleannrooster.exiled_combat;

import com.cleannrooster.exiled_combat.config.ExiledCombatConfig;
import com.cleannrooster.exiled_combat.network.AttackStartC2S;
import com.cleannrooster.exiled_combat.network.AttackStartS2C;
import com.cleannrooster.exiled_combat.network.ConfigSyncS2C;
import com.cleannrooster.exiled_combat.network.HitstopS2C;
import com.cleannrooster.exiled_combat.server.ServerCombat;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point: config, networking, and the server session tick. */
public final class ExiledCombat {
    public static final String MOD_ID = "exiled_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ExiledCombat() {
    }

    public static void init() {
        ExiledCombatConfig.load();
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, AttackStartC2S.ID, AttackStartC2S.CODEC,
            (payload, context) -> context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayerEntity player) {
                    ServerCombat.onAttackStart(player, payload);
                }
            }));
        // On a dedicated server the S2C types still need registering; clients register them with receivers.
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(AttackStartS2C.ID, AttackStartS2C.CODEC);
            NetworkManager.registerS2CPayloadType(ConfigSyncS2C.ID, ConfigSyncS2C.CODEC);
            NetworkManager.registerS2CPayloadType(HitstopS2C.ID, HitstopS2C.CODEC);
        }

        LifecycleEvent.SERVER_STOPPED.register(stopped -> ServerCombat.clear());
        TickEvent.SERVER_POST.register(ServerCombat::tick);
        PlayerEvent.PLAYER_JOIN.register(ExiledCombat::syncConfig);
        PlayerEvent.PLAYER_QUIT.register(player -> ServerCombat.forget(player.getUuid()));
    }

    private static void syncConfig(ServerPlayerEntity player) {
        NetworkManager.sendToPlayer(player, new ConfigSyncS2C(ExiledCombatConfig.localAsJson()));
    }
}
