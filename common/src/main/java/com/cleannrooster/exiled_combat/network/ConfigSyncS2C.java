package com.cleannrooster.exiled_combat.network;

import com.cleannrooster.exiled_combat.ExiledCombat;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** The server's whole gameplay config as JSON, sent on join so clients play by the server's rules. */
public record ConfigSyncS2C(String json) implements CustomPayload {

    public static final Id<ConfigSyncS2C> ID = new Id<>(Identifier.of(ExiledCombat.MOD_ID, "config_sync"));
    public static final PacketCodec<RegistryByteBuf, ConfigSyncS2C> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeString(value.json, 262144),
        buf -> new ConfigSyncS2C(buf.readString(262144)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
