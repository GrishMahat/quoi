package quoi.module.impl.misc.slayers.vampire

import net.minecraft.world.entity.LivingEntity
import quoi.api.colour.Colour
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
object BloodfiendESP : ToggleableGroup(
    VampireSlayer,
    "Bloodfiend ESP",
    desc = "Highlights the Bloodfiend boss while your own quest fight is active, and alerts when it can be weakened with a Steak.",
) {
    private val steakAlert by switch(
        "Steak alert",
        desc = "Shows a title when the Bloodfiend becomes stakeworthy."
    )

    private val alert = Throttle(5_000)

    val renderBosses: List<Pair<LivingEntity, Colour?>>
        get() {
            if (!enabled || Slayers.questState != QuestState.KILLING) return emptyList()
            return VampireSlayer.boss?.let { listOf(it to Colour.RED) } ?: emptyList()
        }

    init {
        on<TickEvent.End> {
            if (!steakAlert || !VampireSlayer.steakable) return@on

            val now = System.currentTimeMillis()
            if (!alert.ready(now)) return@on
            alert.mark(now)
            slayerAlert("§c§lSTEAK!")
        }

        on<SlayerEvent.State> { alert.reset() }
        on<WorldEvent.Change> { alert.reset() }
    }

    override fun onDisable() {
        alert.reset()
        super.onDisable()
    }
}
