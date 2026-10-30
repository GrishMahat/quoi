package quoi.module.impl.misc.slayers.vampire

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils.registryName
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.PlayerUtils

@Suppress("unused_expression")
object ManiaAlert : ToggleableGroup(
    VampireSlayer,
    "Mania alert",
    desc = "Alerts during the Mania phase. Green while you stand on a safe green/lime terracotta spot, red otherwise.",
) {
    private var nextScan = 0L
    private var nextAlert = 0L

    // Skyblocker keys the colour to the block under your feet: green dyed
    // terracotta is the safe spot; registry name keeps it working on any mappings
    private fun onSafeSpot(): Boolean =
        player.blockPosition().below().state.block.registryName.let {
            it.contains("terracotta") && (it.contains("green") || it.contains("lime"))
        }

    init {
        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) return@on
            val now = System.currentTimeMillis()
            if (now < nextScan) return@on
            nextScan = now + 250

            val boss = BloodfiendESP.pinBoss() ?: return@on
            val mania = getEntities<ArmorStand>(boss.position(), 6.0) {
                it.isAlive && it.customName?.string?.noControlCodes?.contains("MANIA") == true
            }.isNotEmpty()
            if (!mania || now < nextAlert) return@on
            nextAlert = now + 5_000

            PlayerUtils.setTitle(
                title = if (onSafeSpot()) "§a§lMANIA" else "§c§lMANIA",
                playSound = true,
                sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
                pitch = 0f,
                stayAlive = 15,
                fadeOut = 5,
            )
        }

        on<WorldEvent.Change> {
            nextScan = 0L
            nextAlert = 0L
        }
    }

    override fun onDisable() {
        nextScan = 0L
        nextAlert = 0L
        super.onDisable()
    }
}
