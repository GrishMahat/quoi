package quoi.mixins;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.core.EventDispatcher;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$sendPacket(Packet<?> packet, ChannelFutureListener channelFutureListener, boolean flush, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (connection.getReceiving() != PacketFlow.CLIENTBOUND ||
                !(connection.getPacketListener() instanceof ClientPacketListener) ||
                packet instanceof ServerboundKeepAlivePacket) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> {
                if (connection.isConnected()) connection.send(packet, channelFutureListener, flush);
            });
            ci.cancel();
            return;
        }
        if (EventDispatcher.onPacketSent(packet)) ci.cancel();
    }
}
