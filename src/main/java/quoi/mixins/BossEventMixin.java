package quoi.mixins;

import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.BossBarEvent;
import quoi.mixins.accessors.LerpingBossEventAccessor;
import quoi.utils.StringUtils;

@Mixin(BossEvent.class)
public abstract class BossEventMixin {
    @Inject(method = "setName", at = @At("TAIL"))
    private void onSetName(Component name, CallbackInfo ci) {
        if ((Object) this instanceof LerpingBossEvent bossEvent) {
            String message = StringUtils.INSTANCE.getFormattedString(name);
            float progress = ((LerpingBossEventAccessor) bossEvent).getTargetPercent();
            new BossBarEvent.Update(message, StringUtils.INSTANCE.getNoControlCodes(message), name, progress).post();
        }
    }
}
