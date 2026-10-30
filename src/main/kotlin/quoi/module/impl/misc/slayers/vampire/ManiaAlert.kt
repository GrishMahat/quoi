package quoi.module.impl.misc.slayers.vampire

import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.impl.misc.slayers.Throttle
import quoi.module.impl.misc.slayers.slayerAlert
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.WorldUtils.registryName
import quoi.utils.WorldUtils.state

@Suppress("unused_expression")
object ManiaAlert : ToggleableGroup(
    VampireSlayer,
    "Mania alert",
    desc = "Alerts during the Mania phase. Green while you stand on a safe green/lime terracotta spot, red otherwise.",
) {
    private val alert = Throttle(5_000)

    // Skyblocker keys the colour to the block under your feet: green dyed
    // terracotta is the safe spot; registry name keeps it working on any mappings
    private fun onSafeSpot(): Boolean =
        player.blockPosition().below().state.block.registryName.let {
            it.contains("terracotta") && (it.contains("green") || it.contains("lime"))
        }

    init {
        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) {
                alert.reset()
                return@on
            }
            if (VampireSlayer.standNames.none { it.contains("MANIA") }) return@on

            val now = System.currentTimeMillis()
            if (!alert.ready(now)) return@on
            alert.mark(now)

            slayerAlert(if (onSafeSpot()) "§a§lMANIA" else "§c§lMANIA")
        }

        on<SlayerEvent.State> { alert.reset() }
        on<WorldEvent.Change> { alert.reset() }
    }

    override fun onDisable() {
        alert.reset()
        super.onDisable()
    }
}
