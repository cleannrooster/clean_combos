package com.cleannrooster.clean_combos;

import com.cleannrooster.clean_combos.api.CleanCombosApi;
import com.cleannrooster.clean_combos.client.ClientCombat;
import com.cleannrooster.clean_combos.client.WeaponTooltip;
import com.cleannrooster.clean_combos.client.anim.IdleStanceAnimation;
import com.cleannrooster.clean_combos.client.anim.ProceduralSwingAnimation;
import com.cleannrooster.clean_combos.client.debug.SwingDebugOverlay;
import com.cleannrooster.clean_combos.client.targeting.ScreenProjector;
import com.cleannrooster.clean_combos.client.trail.TrailEffects;
import com.cleannrooster.clean_combos.combat.Moveset;
import com.cleannrooster.clean_combos.combat.WeaponType;
import com.cleannrooster.clean_combos.config.CleanCombosClientConfig;
import com.cleannrooster.clean_combos.config.CleanCombosConfig;
import com.cleannrooster.clean_combos.network.AttackStartS2C;
import com.cleannrooster.clean_combos.network.ConfigSyncS2C;
import com.cleannrooster.clean_combos.network.HitstopS2C;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.networking.NetworkManager;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Locale;

/** Client entry point: S2C receivers, the client combat tick, and the procedural animation layer. */
public final class CleanCombosClient {

    /** Above PlayerAnimator's usual 1000 so swings win over idle emotes and similar layers. */
    private static final int ANIMATION_LAYER_PRIORITY = 1500;

    private CleanCombosClient() {
    }

    public static void init() {
        CleanCombosApi.installClient(ClientCombat::sessionFor, ClientCombat::comboFor);
        CleanCombosClientConfig.load();
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AttackStartS2C.ID, AttackStartS2C.CODEC,
            (payload, context) -> context.queue(() -> ClientCombat.onRemoteStart(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, HitstopS2C.ID, HitstopS2C.CODEC,
            (payload, context) -> context.queue(() -> ClientCombat.onRemoteHitstop(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ConfigSyncS2C.ID, ConfigSyncS2C.CODEC,
            (payload, context) -> context.queue(() -> CleanCombosConfig.applyServerJson(payload.json())));

        ClientTickEvent.CLIENT_POST.register(ClientCombat::tick);
        ClientTooltipEvent.ITEM.register((stack, lines, context, type) -> WeaponTooltip.append(stack, lines));
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ClientCombat.reset();
            ScreenProjector.reset();
            CleanCombosConfig.clearServerValues();
        });

        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) ->
            {
                // The ready stance sits under the attack layer, which clears it out as an attack starts.
                stack.addAnimLayer(ANIMATION_LAYER_PRIORITY - 1, new IdleStanceAnimation(player));
                stack.addAnimLayer(ANIMATION_LAYER_PRIORITY, new ProceduralSwingAnimation(player));
            });

        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> dispatcher.register(
            ClientCommandRegistrationEvent.literal(CleanCombos.MOD_ID).then(
                ClientCommandRegistrationEvent.literal("debug").executes(ctx -> {
                    boolean on = SwingDebugOverlay.toggle();
                    ctx.getSource().arch$sendSuccess(() -> Text.literal("Swing debug overlay " + (on ? "on" : "off")), false);
                    return 1;
                })).then(
                ClientCommandRegistrationEvent.literal("item").executes(ctx -> {
                    var player = ctx.getSource().arch$getPlayer();
                    ctx.getSource().arch$sendSuccess(() -> Text.literal("Main hand: ")
                        .append(describe(player.getMainHandStack())), false);
                    ctx.getSource().arch$sendSuccess(() -> Text.literal("Off hand: ")
                        .append(describe(player.getOffHandStack())), false);
                    ProceduralSwingAnimation layer = ProceduralSwingAnimation.layerFor(player);
                    String line = layer != null ? layer.debugLine() : "no animation layer for the local player";
                    ctx.getSource().arch$sendSuccess(() -> Text.literal(line), false);
                    return 1;
                }))));
    }

    /** "Rapier (rapier), Swing attack" — the weapon type with its id, and the moveset it attacks with. */
    private static Text describe(ItemStack stack) {
        WeaponType type = CleanCombosApi.weaponType(stack);
        Moveset moveset = CleanCombosApi.moveset(stack);
        return Text.translatable(type.translationKey()).append(" (" + type.id() + "), ")
            .append(moveset != null ? Text.literal(moveset.name().toLowerCase(Locale.ROOT) + " moveset")
                : Text.literal("left to vanilla"));
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
