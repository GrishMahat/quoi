package quoi.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.module.impl.dungeon.autoclear.impl.InteractiveMap;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @WrapWithCondition(
            method = "releaseAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;release()V"
            )
    )
    private static boolean quoi$preserveMapSneak(KeyMapping keyMapping) {
        return keyMapping != Minecraft.getInstance().options.keyShift || !InteractiveMap.INSTANCE.getMapOpen();
    }
}
