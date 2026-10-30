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

@Suppress("unused_expression")
object Stake : ToggleableGroup(
    VampireSlayer,
    "Stake alert",
    desc = "Alerts when the Bloodfiend becomes stakeworthy. Auto swapping lives in Bloodfiend assist.",
) {
    private val alert = Throttle(5_000)

    init {
        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) {
                alert.reset()
                return@on
            }
            if (!VampireSlayer.steakable) return@on

            val now = System.currentTimeMillis()
            if (!alert.ready(now)) return@on
            alert.mark(now)

            slayerAlert("§6§lSTEAK!")
        }

        on<SlayerEvent.State> { alert.reset() }
        on<WorldEvent.Change> { alert.reset() }
    }

    override fun onDisable() {
        alert.reset()
        super.onDisable()
    }
}
