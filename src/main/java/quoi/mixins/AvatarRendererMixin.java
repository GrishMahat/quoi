package quoi.mixins;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.render.ItemAnimations;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN")
    )
    private void quoi$itemAnimationsThirdPersonSwing(Avatar avatar, AvatarRenderState avatarRenderState, float tickProgress, CallbackInfo ci) {
        //? if <26.3 {
        InteractionHand hand = avatar.swingingArm != null ? avatar.swingingArm : InteractionHand.MAIN_HAND;
        if (ItemAnimations.isMap(avatar.getItemInHand(hand))) return;
        avatarRenderState.attackTime = ItemAnimations.getThirdPersonSwingAnimation(avatarRenderState.attackTime, avatarRenderState.id);
        //?} else {
        /*InteractionHand hand = avatarRenderState.currentSwing != null ? avatarRenderState.currentSwing.hand() : InteractionHand.MAIN_HAND;
        if (ItemAnimations.isMap(avatar.getItemInHand(hand))) return;
        avatarRenderState.swingAnimation = ItemAnimations.getThirdPersonSwingAnimation(avatarRenderState.swingAnimation, avatarRenderState.id, avatarRenderState.currentSwing);
        avatarRenderState.currentSwing = ItemAnimations.getThirdPersonSwingDescription(avatarRenderState.currentSwing, avatarRenderState.id);
        *///?}
    }
}
