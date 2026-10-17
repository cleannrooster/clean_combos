package com.cleannrooster.exiled_combat.network;

import com.cleannrooster.exiled_combat.ExiledCombat;
import com.cleannrooster.exiled_combat.combat.ComboSlot;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> nearby clients so other players see the swing. Carries the server's timings, since a remote
 * client can't reproduce another player's attack-speed attribute.
 */
public record AttackStartS2C(int attackerId, int targetId, ComboSlot slot, float snapDistance, float startYaw,
                             float startPitch, boolean airborne,
                             int windupTicks, int lockTicks, int recoveryTicks) implements CustomPayload {

    public static final Id<AttackStartS2C> ID = new Id<>(Identifier.of(ExiledCombat.MOD_ID, "attack_start_remote"));
    public static final PacketCodec<RegistryByteBuf, AttackStartS2C> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.attackerId);
            buf.writeVarInt(value.targetId);
            buf.writeEnumConstant(value.slot);
            buf.writeFloat(value.snapDistance);
            buf.writeFloat(value.startYaw);
            buf.writeFloat(value.startPitch);
            buf.writeBoolean(value.airborne);
            buf.writeVarInt(value.windupTicks);
            buf.writeVarInt(value.lockTicks);
            buf.writeVarInt(value.recoveryTicks);
        },
        buf -> new AttackStartS2C(buf.readVarInt(), buf.readVarInt(), buf.readEnumConstant(ComboSlot.class),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
