package quoi.mixins;

//~ item_in_hand
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.module.impl.render.ItemAnimations;

@Mixin(ItemInHandRenderer.class)
public abstract class LegacyItemInHandRendererMixin {

    @WrapOperation(
            method = "renderHandsWithItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"),
            require = 1
    )
    private float quoi$itemAnimationsSwing(LocalPlayer instance, float v, Operation<Float> original) {
        if (!ItemAnimations.INSTANCE.getEnabled()) return original.call(instance, v);

        return ItemAnimations.getSwingAnimation(v);
    }

    @Inject(
            method = "shouldInstantlyReplaceVisibleItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$itemAnimationsReequip(ItemStack itemStack, ItemStack itemStack2, CallbackInfoReturnable<Boolean> cir) {
        if (ItemAnimations.disableReequip()) cir.setReturnValue(true);
    }

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"),
            require = 1
    )
    private float quoi$itemAnimationsBob(LocalPlayer instance, float v, Operation<Float> original) {
        if (ItemAnimations.disableReequip() || ItemAnimations.disableSwingBob()) return 1f;
        return original.call(instance, v);
    }

    @Inject(
            method = "applyItemArmTransform",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$applyEquipOffset(PoseStack poseStack, HumanoidArm humanoidArm, float f, CallbackInfo ci) {
        if (ItemAnimations.disableReequip()) {
            int i = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate((float)i * 0.56f, -0.52f, -0.72f);
            ci.cancel();
        }
    }

    @Inject(
            method = "renderHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                    ordinal = 0
            ),
            require = 1
    )
    private void quoi$itemAnimationsMainTransform(float f, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LocalPlayer localPlayer, int i, CallbackInfo ci) {
        ItemAnimations.applyTransformations(poseStack);
    }

    @Inject(
            method = "renderItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
    )
    private void quoi$itemAnimationsItemScale(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext itemDisplayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        ItemAnimations.applyScale(poseStack, itemStack, itemDisplayContext);
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 4
    )
    private float quoi$disableSwingTrans1(float f) {
        return ItemAnimations.disableSwingTranslation() ? 0f : f;
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 5
    )
    private float quoi$disableSwingTrans2(float f) {
        return ItemAnimations.disableSwingTranslation() ? 0f : f;
    }

    @ModifyVariable(
            method = "renderPlayerArm",
            at = @At(value = "STORE"),
            ordinal = 6
    )
    private float quoi$disableSwingTrans3(float f) {
        return ItemAnimations.disableSwingTranslation() ? 0f : f;
    }

    @WrapOperation(
            method = "swingArm",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")
    )
    private void quoi$disableSwingTransGeneral(PoseStack instance, float f, float g, float h, Operation<Void> original) {
        if (ItemAnimations.disableSwingTranslation()) return;
        original.call(instance, f, g, h);
    }

    @Inject(
            method = "renderMap",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"),
            require = 1
    )
    private void quoi$mapScale2(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, ItemStack itemStack, CallbackInfo ci) {
        if (!ItemAnimations.INSTANCE.getEnabled()) return;
        poseStack.translate(64f, 64f, 0f);
        ItemAnimations.applyScale(poseStack);
        poseStack.translate(-64f, -64f, 0f);
    }
}
