package quoi.module.impl.misc.slayers.vampire

import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.impl.misc.slayers.slayerAlert
import quoi.module.settings.group.ToggleableGroup

@Suppress("unused_expression")
object TwinClaws : ToggleableGroup(
    VampireSlayer,
    "Twinclaws alert",
    desc = "Shows a title when the Twinclaws timer reaches the alert delay.",
) {
    private val delay by slider("Alert delay", 3.0, 0.0, 5.0, 0.1, unit = "s", desc = "Alerts when the Twinclaws timer drops below this.")
    private val realert by slider("Re-alert delay", 5, 1, 30, 1, unit = "s", desc = "Seconds before the alert repeats while the timer stays low.")

    private var nextAlert = 0L

    init {
        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) {
                nextAlert = 0L
                return@on
            }
            val seconds = VampireSlayer.twinclawsSeconds ?: return@on
            if (seconds > delay) return@on

            val now = System.currentTimeMillis()
            if (now < nextAlert) return@on
            nextAlert = now + realert * 1_000L
            slayerAlert("§6§lTWINCLAWS")
        }

        on<SlayerEvent.State> { nextAlert = 0L }
        on<WorldEvent.Change> { nextAlert = 0L }
    }

    override fun onDisable() {
        nextAlert = 0L
        super.onDisable()
    }
}
