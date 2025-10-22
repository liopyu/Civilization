package net.liopyu.civilization.net;

import net.liopyu.civilization.client.BuildPreviewClient;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Net {
    public static void register(RegisterPayloadHandlersEvent e) {
        var reg = e.registrar("1");
        reg.playToClient(BuildPreviewPacket.TYPE, BuildPreviewPacket.STREAM_CODEC, (pkt, ctx) -> {
            ctx.enqueueWork(() -> BuildPreviewClient.receive(pkt));
        });
    }
}
