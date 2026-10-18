package com.cleannrooster.clean_combos;

import com.cleannrooster.clean_combos.combat.CombatSounds;
import com.cleannrooster.clean_combos.combat.SpearReach;
import com.cleannrooster.clean_combos.config.CleanCombosConfig;
import com.cleannrooster.clean_combos.network.AttackStartC2S;
import com.cleannrooster.clean_combos.network.AttackStartS2C;
import com.cleannrooster.clean_combos.network.ConfigSyncS2C;
import com.cleannrooster.clean_combos.network.HitstopS2C;
import com.cleannrooster.clean_combos.server.ServerCombat;
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
public final class CleanCombos {
    public static final String MOD_ID = "clean_combos";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CleanCombos() {
    }

    public static void init() {
        CleanCombosConfig.load();
        CombatSounds.register();
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
        TickEvent.PLAYER_POST.register(SpearReach::tick);
        PlayerEvent.PLAYER_JOIN.register(CleanCombos::syncConfig);
        PlayerEvent.PLAYER_QUIT.register(player -> ServerCombat.forget(player.getUuid()));
    }

    private static void syncConfig(ServerPlayerEntity player) {
        NetworkManager.sendToPlayer(player, new ConfigSyncS2C(CleanCombosConfig.localAsJson()));
    }
}
