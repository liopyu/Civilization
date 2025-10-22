package net.liopyu.civilization.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record BuildPreviewPacket(int entityId, List<BlockPos> positions, int ttl) implements CustomPacketPayload {
    public static final Type<BuildPreviewPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("civilization", "build_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildPreviewPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BuildPreviewPacket decode(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int n = buf.readVarInt();
            List<BlockPos> pos = new ArrayList<>(n);
            for (int i = 0; i < n; i++) pos.add(buf.readBlockPos());
            int ttl = buf.readVarInt();
            return new BuildPreviewPacket(id, pos, ttl);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BuildPreviewPacket pkt) {
            buf.writeVarInt(pkt.entityId());
            buf.writeVarInt(pkt.positions().size());
            for (BlockPos p : pkt.positions()) buf.writeBlockPos(p);
            buf.writeVarInt(pkt.ttl());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
