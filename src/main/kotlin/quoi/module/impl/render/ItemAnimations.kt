package quoi.module.impl.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import kotlin.math.pow
import net.minecraft.client.renderer.entity.state.FishingHookRenderState
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectUtil
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.SwingAnimation
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.utils.ui.settingFromK0

// Kyleen
object ItemAnimations : Module(
    "Item Animations",
    desc = "Changes how the held item looks on screen"
) {
    private var x by slider("X", 0.0f, -1.0f, 1.0f, 0.1)
    private var y by slider("Y", 0.0f, -1.0f, 1.0f, 0.1)
    private var z by slider("Z", 0.0f, -1.0f, 1.0f, 0.1)
    private var yaw by slider("Yaw", 0.0f, -180.0f, 180.0f, 1.0)
    private var pitch by slider("Pitch", 0.0f, -180.0f, 180.0f, 1.0)
    private var roll by slider("Roll", 0.0f, -180.0f, 180.0f, 1.0)
    private var scale by slider("Scale", 0.0, -4.0, 4.0, 0.1)
    private var swingSpeed by slider("Swing speed", 0.0, -4.0, 4.0, 0.1)
    private val ignoreEffects by switch("Ignore effects")
    private val applyInThirdPerson by switch("Apply in third person").json("Third person")
    private val applyToOtherPlayers by switch("Apply to other players").json("Other players")
    private val noReequipReset by switch("No re-equip reset")
    private val inplaceSwing by switch("Swing in place")
    private val noSwing by switch("No swing animation")
    @Suppress("unused")
    private val reset by button("Reset") { resetSettings() }

    private var swinging = false
    private var swingTimeTick = 0
    private var attackAnim = 0f
    private var prevAttackAnim = 0f
    //? if >=26.3 {
    /*private var swingDescription: LivingEntity.SwingDescription? = null
    *///?}
    private val thirdPersonSwings = mutableMapOf<Int, ThirdPersonSwing>()

    //? if <26.3 {
    private data class ThirdPersonSwing(val startTime: Double, var previousVanilla: Float)
    //?} else {
    /*private data class ThirdPersonSwing(
        val startTime: Double,
        var previousVanilla: Float,
        val description: LivingEntity.SwingDescription?
    )
    *///?}

    override fun onDisable() {
        super.onDisable()
        swinging = false
        //? if >=26.3 {
        /*swingDescription = null
        *///?}
        thirdPersonSwings.clear()
    }

    private fun resetSettings() {
        setOf(
            ::x, ::y, ::z,
            ::yaw, ::pitch,
            ::roll, ::scale, ::swingSpeed
        ).forEach { settingFromK0(it).reset() }
    }

    private fun calcSwingSpeed() = 2.0.pow(swingSpeed)

    private fun disableSwingRotation(): Boolean = enabled && noSwing

    private fun getCurrentSwingDuration(): Int {
        if (ignoreEffects) return 6
        return if (MobEffectUtil.hasDigSpeed(player)) {
            6 - (1 + MobEffectUtil.getDigSpeedAmplification(player))
        } else {
            6 + (1 + (player.getEffect(MobEffects.MINING_FATIGUE)?.amplifier ?: -1)) * 2
        }
    }

    @JvmStatic fun disableReequip(): Boolean = enabled && noReequipReset

    @JvmStatic
    fun disableSwingTranslation(): Boolean {
        return enabled && inplaceSwing
    }

    @JvmStatic
    fun disableSwingBob(): Boolean {
        return disableSwingTranslation() || disableSwingRotation()
    }

    @JvmStatic
    fun applyTransformations(pose: PoseStack) {
        if (!enabled) return

        //? if <26.3 {
        pose.mulPose(Axis.XP.rotationDegrees(pitch))
        pose.mulPose(Axis.YP.rotationDegrees(yaw))
        pose.mulPose(Axis.ZP.rotationDegrees(roll))
        //?} else {
        /*pose.rotateDegrees(Axis.XP, pitch)
        pose.rotateDegrees(Axis.YP, yaw)
        pose.rotateDegrees(Axis.ZP, roll)
        *///?}

        if (x != 0.0f || y != 0.0f || z != 0.0f) {
            pose.translate(x, y, z)
        }
    }

    @JvmStatic
    fun applyFishingRodLineTransformations(hook: FishingHook, state: FishingHookRenderState) {
        if (!enabled) return

        val player = hook.playerOwner ?: return
        if (player != mc.player) return
        if (!mc.options.cameraType.isFirstPerson) return
        if (!isHoldingFishingRod(player)) return

        val camera = mc.gameRenderer.mainCamera
        val left = camera.leftVector()
        val up = camera.upVector()
        val look = player.lookAngle
        val offset = state.lineOriginOffset

        state.lineOriginOffset = offset.add(
            (-left.x() * x + up.x() * y).toDouble() + look.x * z,
            (-left.y() * x + up.y() * y).toDouble() + look.y * z,
            (-left.z() * x + up.z() * y).toDouble() + look.z * z
        )
    }

    private fun isHoldingFishingRod(player: Player): Boolean {
        return player.mainHandItem.item is FishingRodItem || player.offhandItem.item is FishingRodItem
    }

    @JvmStatic
    fun applyScale(pose: PoseStack) {
        applyScale(pose, null, null)
    }

    @JvmStatic
    fun applyScale(pose: PoseStack, stack: ItemStack?, context: ItemDisplayContext?) {
        val itemAnimationScale = if (enabled) 2.0.pow(scale).toFloat() else 1f
        if (itemAnimationScale != 1f) pose.scale(itemAnimationScale, itemAnimationScale, itemAnimationScale)
    }

    @JvmStatic
    fun applyThirdPersonPlayerTransformations(pose: PoseStack, playerId: Int) {
        if (!shouldApplyThirdPerson(playerId)) return

        applyTransformations(pose)
        applyScale(pose)
    }

    @JvmStatic
    //? if <26.3 {
    fun getThirdPersonSwingAnimation(current: Float, playerId: Int): Float {
    //?} else {
    /*fun getThirdPersonSwingAnimation(current: Float, playerId: Int, description: LivingEntity.SwingDescription?): Float {
    *///?}
        if (!shouldApplyThirdPerson(playerId)) return current
        if (disableSwingRotation()) return 0f

        val pt = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        if (playerId == player.id) return getSwingAnimation(pt)

        val speed = calcSwingSpeed().takeIf { it != 1.0 } ?: return current.also { thirdPersonSwings.remove(playerId) }
        val currentTime = level.gameTime.toDouble() + pt
        val existingSwing = thirdPersonSwings[playerId]
        val existingProgress = existingSwing?.progress(currentTime, speed)

        val vanillaStartedSwing = current > 0f && (existingSwing == null || existingSwing.previousVanilla <= 0f || current < existingSwing.previousVanilla)
        val swing = if (vanillaStartedSwing && (existingProgress == null || existingProgress >= 0.5f))
            //? if <26.3 {
            ThirdPersonSwing(currentTime, current).also { thirdPersonSwings[playerId] = it }
            //?} else {
            /*ThirdPersonSwing(currentTime, current, description).also { thirdPersonSwings[playerId] = it }
            *///?}
        else existingSwing ?: return current

        val progress = swing.progress(currentTime, speed)
        swing.previousVanilla = current

        return if (progress >= 1f) {
            thirdPersonSwings.remove(playerId)
            0f
        } else progress.coerceIn(0f, 1f)
    }

    private fun ThirdPersonSwing.progress(time: Double, speed: Double) =
        ((time - startTime) * speed / getCurrentSwingDuration()).toFloat()
    //? if >=26.3 {
    /*@JvmStatic
    fun getThirdPersonSwingDescription(current: LivingEntity.SwingDescription?, playerId: Int): LivingEntity.SwingDescription? {
        if (!shouldApplyThirdPerson(playerId)) return current
        return if (playerId == player.id) getSwingDescription(current)
        else thirdPersonSwings[playerId]?.description ?: current
    }

    @JvmStatic
    fun getSwingDescription(current: LivingEntity.SwingDescription?): LivingEntity.SwingDescription? {
        if (!enabled) return current
        return if (swinging || prevAttackAnim > 0f) swingDescription ?: current else current
    }
    *///?}

    private fun shouldApplyThirdPerson(playerId: Int): Boolean {
        return enabled &&
            (if (playerId == player.id) applyInThirdPerson else applyToOtherPlayers)
    }

    @JvmStatic
    fun getSwingAnimation(pt: Float): Float {
        if (disableSwingRotation()) return 0f

        var d = attackAnim - prevAttackAnim
        if (d < 0.0) d++
        return prevAttackAnim + d * pt
    }

    @JvmStatic
    //? if <26.3 {
    fun onSwing() {
    //?} else {
    /*fun onSwing(hand: InteractionHand, animation: SwingAnimation) {
    *///?}
        if (!enabled) return
        if (swinging && swingTimeTick >= 0 && (swingTimeTick * calcSwingSpeed()) < getCurrentSwingDuration() / 2) return
        swingTimeTick = -1
        swinging = true
        //? if >=26.3 {
        /*swingDescription = LivingEntity.SwingDescription(hand, animation, getCurrentSwingDuration())
        *///?}
    }

    init {
        on<TickEvent.End> {
            prevAttackAnim = attackAnim
            val total = getCurrentSwingDuration()

            if (swinging) {
                swingTimeTick++
                val currentProgress = swingTimeTick * calcSwingSpeed()
                if (currentProgress >= total) {
                    swingTimeTick = 0
                    swinging = false
                }
            } else {
                swingTimeTick = 0
            }

            attackAnim = (swingTimeTick * calcSwingSpeed()).toFloat() / total
        }
    }
}
