package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.utils.render.CustomRenderLayer;

@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group")
public class OverlayQuadSortingMixin {
    @ModifyExpressionValue(method = "getOrAddDraw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;useImprovedTransparency()Z"))
    private boolean quoi$sortBlendedOverlays(boolean improvedTransparency, @Local(argsOnly = true) RenderType renderType) {
        // ESP is composited after the OIT resolve, so it still needs back-to-front sorting.
        return improvedTransparency && renderType != CustomRenderLayer.INSTANCE.getTRIANGLE_STRIP_ESP();
    }
}
