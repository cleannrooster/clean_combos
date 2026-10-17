package com.cleannrooster.clean_combos.network;

import com.cleannrooster.clean_combos.CleanCombos;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> nearby clients: a swing connected, so freeze the attacker's swing and shake its victims. The
 * attacker's own client predicts this itself and isn't sent it.
 */
public record HitstopS2C(int attackerId, int ticks, int[] victimIds) implements CustomPayload {

    public static final Id<HitstopS2C> ID = new Id<>(Identifier.of(CleanCombos.MOD_ID, "hitstop"));
    public static final PacketCodec<RegistryByteBuf, HitstopS2C> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.attackerId);
            buf.writeVarInt(value.ticks);
            buf.writeIntArray(value.victimIds);
        },
        buf -> new HitstopS2C(buf.readVarInt(), buf.readVarInt(), buf.readIntArray()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
