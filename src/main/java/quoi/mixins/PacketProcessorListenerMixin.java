package quoi.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.api.events.PacketEvent;
import quoi.api.events.core.EventDispatcher;

// based on https://github.com/Noamm9/NoammAddons/blob/1.1.9/src/main/java/com/github/noamm9/mixin/MixinPacketProcessorListener.java
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class PacketProcessorListenerMixin {
    @WrapOperation(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
            )
    )
    private void quoi$handlePacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (listener.flow() != PacketFlow.CLIENTBOUND) {
            original.call(packet, listener);
            return;
        }
        if (EventDispatcher.onPacketReceived(packet)) return;
        original.call(packet, listener);
        new PacketEvent.ReceivedPost(packet).post();
    }
}
