package com.cleannrooster.clean_combos.network;

import com.cleannrooster.clean_combos.CleanCombos;
import com.cleannrooster.clean_combos.combat.ComboSlot;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client -> server once in range (or immediately for an in-place swing).
 *
 * @param targetId     entity id, or -1 for attack-in-place
 * @param facingYaw    starting facing; the server recomputes it toward a valid target
 * @param facingPitch  starting pitch; likewise recomputed toward a valid target
 * @param lookYaw      the player's look at click time, which mid-swing turning is measured from. Sent because
 *                     the server's copy of the look can be a tick of mouse movement stale at this point
 * @param lookPitch    likewise for pitch
 * @param snapDistance blocks the client will snap to reach the target, 0 for none; the server re-measures it
 * @param airborne     the attack was started in the air (a jump attack)
 * @param sprinting    the attack was started sprinting forward (a swing becomes a running thrust)
 */
public record AttackStartC2S(int targetId, float facingYaw, float facingPitch, float lookYaw, float lookPitch,
                             ComboSlot slot, float snapDistance, boolean airborne, boolean sprinting)
        implements CustomPayload {

    public static final Id<AttackStartC2S> ID = new Id<>(Identifier.of(CleanCombos.MOD_ID, "attack_start"));
    public static final PacketCodec<RegistryByteBuf, AttackStartC2S> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.targetId);
            buf.writeFloat(value.facingYaw);
            buf.writeFloat(value.facingPitch);
            buf.writeFloat(value.lookYaw);
            buf.writeFloat(value.lookPitch);
            buf.writeEnumConstant(value.slot);
            buf.writeFloat(value.snapDistance);
            buf.writeBoolean(value.airborne);
            buf.writeBoolean(value.sprinting);
        },
        buf -> new AttackStartC2S(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readEnumConstant(ComboSlot.class), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
