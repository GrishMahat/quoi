package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor;
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes;

import java.util.concurrent.CopyOnWriteArrayList;

@Pseudo
@Mixin(targets = "com.github.noamm9.features.impl.misc.NoRotate", remap = false)
public abstract class NoRotateMixin {
    @ModifyExpressionValue(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETSTATIC,
            target = "Lcom/github/noamm9/features/impl/misc/NoRotate;pendingTeleports:Ljava/util/concurrent/CopyOnWriteArrayList;"
    ))
    private static CopyOnWriteArrayList<?> quoi$clearPredictions(CopyOnWriteArrayList<?> predictions) {
        if (quoi$routing()) predictions.clear();
        return predictions;
    }

    @Unique
    private static boolean quoi$routing() {
        return AutoRoutes.INSTANCE.getRouting() || ClearExecutor.INSTANCE.getRouting();
    }

    @Inject(method = "attemptTeleport", at = @At("HEAD"), cancellable = true)
    private void quoi$blockPrediction(CallbackInfo ci) {
        if (quoi$routing()) ci.cancel();
    }
}
