package com.cleannrooster.exiled_combat;

import com.cleannrooster.exiled_combat.client.ClientCombat;
import com.cleannrooster.exiled_combat.client.WeaponTooltip;
import com.cleannrooster.exiled_combat.client.anim.ProceduralSwingAnimation;
import com.cleannrooster.exiled_combat.client.debug.SwingDebugOverlay;
import com.cleannrooster.exiled_combat.client.targeting.ScreenProjector;
import com.cleannrooster.exiled_combat.client.trail.TrailEffects;
import com.cleannrooster.exiled_combat.config.ExiledCombatConfig;
import com.cleannrooster.exiled_combat.network.AttackStartS2C;
import com.cleannrooster.exiled_combat.network.ConfigSyncS2C;
import com.cleannrooster.exiled_combat.network.HitstopS2C;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.networking.NetworkManager;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Client entry point: S2C receivers, the client combat tick, and the procedural animation layer. */
public final class ExiledCombatClient {

    /** Above PlayerAnimator's usual 1000 so swings win over idle emotes and similar layers. */
    private static final int ANIMATION_LAYER_PRIORITY = 1500;

    private ExiledCombatClient() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AttackStartS2C.ID, AttackStartS2C.CODEC,
            (payload, context) -> context.queue(() -> ClientCombat.onRemoteStart(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, HitstopS2C.ID, HitstopS2C.CODEC,
            (payload, context) -> context.queue(() -> ClientCombat.onRemoteHitstop(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ConfigSyncS2C.ID, ConfigSyncS2C.CODEC,
            (payload, context) -> context.queue(() -> ExiledCombatConfig.applyServerJson(payload.json())));

        ClientTickEvent.CLIENT_POST.register(ClientCombat::tick);
        ClientTooltipEvent.ITEM.register((stack, lines, context, type) -> WeaponTooltip.append(stack, lines));
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ClientCombat.reset();
            ScreenProjector.reset();
            ExiledCombatConfig.clearServerValues();
        });

        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) ->
            stack.addAnimLayer(ANIMATION_LAYER_PRIORITY, new ProceduralSwingAnimation(player)));

        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> dispatcher.register(
            ClientCommandRegistrationEvent.literal(ExiledCombat.MOD_ID).then(
                ClientCommandRegistrationEvent.literal("debug").executes(ctx -> {
                    boolean on = SwingDebugOverlay.toggle();
                    ctx.getSource().arch$sendSuccess(() -> Text.literal("Swing debug overlay " + (on ? "on" : "off")), false);
                    return 1;
                })).then(
                ClientCommandRegistrationEvent.literal("item").executes(ctx -> {
                    ProceduralSwingAnimation layer = ProceduralSwingAnimation.layerFor(ctx.getSource().arch$getPlayer());
                    String line = layer != null ? layer.debugLine() : "no animation layer for the local player";
                    ctx.getSource().arch$sendSuccess(() -> Text.literal(line), false);
                    return 1;
                }))));
    }

    /** Called by each loader's translucent-stage world-render hook: trails, then the debug overlay. */
    public static void renderWorld(MatrixStack matrices, Camera camera, float tickDelta) {
        TrailEffects.render(matrices, camera, tickDelta);
        SwingDebugOverlay.render(matrices, camera, tickDelta);
    }

    /** Called by each loader's end-of-frame world-render hook with that frame's camera matrices. */
    public static void onWorldRendered(Matrix4f view, Matrix4f projection, Vec3d cameraPos) {
        ScreenProjector.capture(view, projection, cameraPos);
    }
}
