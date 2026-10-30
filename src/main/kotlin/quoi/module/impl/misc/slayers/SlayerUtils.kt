package quoi.module.impl.misc.slayers

import net.minecraft.sounds.SoundEvents
import quoi.utils.skyblock.player.PlayerUtils

/** Standard slayer title alert: short stay, fast fade, orb pickup sound. */
internal fun slayerAlert(
    title: String,
    subtitle: String = "",
    playSound: Boolean = true,
    pitch: Float = 0f,
    stayAlive: Int = 15,
) = PlayerUtils.setTitle(
    title = title,
    subtitle = subtitle,
    playSound = playSound,
    sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
    pitch = pitch,
    stayAlive = stayAlive,
    fadeOut = 5,
)

/** Rate limiter for periodic scans and alerts. */
internal class Throttle(private val intervalMs: Long) {
    private var nextAt = 0L

    fun ready(now: Long = System.currentTimeMillis()): Boolean = now >= nextAt

    fun mark(now: Long = System.currentTimeMillis()) {
        nextAt = now + intervalMs
    }

    fun reset() {
        nextAt = 0L
    }
}
