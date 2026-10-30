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
import quoi.utils.skyblock.player.PlayerUtils

@Suppress("unused_expression")
object TwinClaws : ToggleableGroup(
    VampireSlayer,
    "Twinclaws alert",
    desc = "Shows a title when the Twinclaws timer reaches the alert delay.",
) {
    private val delay by slider("Alert delay", 3, 0, 5, unit = "s", desc = "Alerts when the Twinclaws timer drops below this.")

    // decimals: Twinclaws timers drop below 1s (e.g. "TWINCLAWS 0.8s")
    private val clawRegex = Regex("TWINCLAWS (\\d+(?:\\.\\d+)?)s")

    // live timer state for BloodfiendAssist
    var active = false
        private set
    var seconds = -1.0
        private set
    var lastScanAt = 0L
        private set

    private var nextScan = 0L
    private var nextAlert = 0L

    private fun updateState(hasClaws: Boolean, parsed: Double) {
        active = hasClaws
        seconds = parsed
    }

    init {
        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) {
                updateState(false, -1.0)
                return@on
            }
            val now = System.currentTimeMillis()
            if (now < nextScan) return@on
            nextScan = now + 250
            lastScanAt = now

            val boss = BloodfiendESP.pinBoss()
            if (boss == null) {
                updateState(false, -1.0)
                return@on
            }

            val parsed = getEntities<ArmorStand>(boss.position(), 5.0) { stand ->
                val name = stand.customName?.string?.noControlCodes ?: return@getEntities false
                clawRegex.containsMatchIn(name)
            }.mapNotNull { stand ->
                val name = stand.customName?.string?.noControlCodes ?: return@mapNotNull null
                clawRegex.find(name)?.groupValues?.get(1)?.toDoubleOrNull()
            }.minOrNull()
            updateState(parsed != null, parsed ?: -1.0)
            if (parsed == null) return@on

            if (parsed <= delay && now > nextAlert) {
                nextAlert = now + 5_000
                PlayerUtils.setTitle(
                    title = "§6§lTWINCLAWS",
                    playSound = true,
                    sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
                    pitch = 0f,
                    stayAlive = 15,
                    fadeOut = 5,
                )
            }
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