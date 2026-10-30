package quoi.module.impl.misc.slayers.blaze

import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.impl.misc.slayers.Throttle
import quoi.module.impl.misc.slayers.slayerAlert
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils
import quoi.utils.StringUtils.noControlCodes

object FirePits : ToggleableGroup(BlazeSlayer, "Fire pits", desc = "Warns when fire pits spawn below a set boss health percentage.") {
    private val warning by switch("Warning", true, desc = "Show a warning when fire pits spawn.")
    private val threshold by slider("Warning at", 33, 5, 90, 1, unit = "%", desc = "Boss health percentage that triggers the warning.").childOf(::warning)
    private val playSound by switch("Play sound", true, desc = "Play a sound when the warning triggers.").childOf(::warning)
    private val soundPitch by slider("Pitch", 0.8f, 0.5f, 2.0f, 0.1f).childOf(::playSound)

    private val healthRegex = Regex("""([\d,]+(?:\.\d+)?[BMK]?)(?:\s*/\s*([\d,]+(?:\.\d+)?[BMK]?))?\s*❤""", RegexOption.IGNORE_CASE)

    private val scan = Throttle(250)
    private var previousRatio = 1.0
    private var triggered = false

    init {
        on<TickEvent.End> {
            if (!enabled) return@on
            if (Slayers.questState != QuestState.KILLING) {
                reset()
                return@on
            }

            // Fire Pits only appear on Tier 3 and Tier 4 Inferno Demonlords
            if (Slayers.questTier < 3) return@on

            val boss = BlazeSlayer.blazeBoss ?: run {
                reset()
                return@on
            }

            val now = System.currentTimeMillis()
            if (!scan.ready(now)) return@on
            scan.mark(now)

            // Find the nametag ArmorStand floating near the boss containing the boss's current health
            val bossStand = getEntities<ArmorStand>(boss.position(), radius = 3.0) { stand ->
                val name = stand.customName?.string?.noControlCodes?.trim() ?: return@getEntities false
                name.contains("❤") && !name.endsWith(" 0❤")
            }.minByOrNull { it.distanceToSqr(boss) } ?: return@on

            val name = bossStand.customName?.string?.noControlCodes?.trim() ?: return@on
            val match = healthRegex.find(name) ?: return@on
            val current = StringUtils.parseNumber(match.groupValues[1])
            val max = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(StringUtils::parseNumber)
                ?: when (Slayers.questTier) {
                    3 -> 45_000_000.0
                    4 -> 150_000_000.0
                    else -> 0.0
                }
            if (max <= 0.0 || current <= 0.0) return@on

            val ratio = current / max
            val trigger = threshold / 100.0
            if (!triggered && previousRatio > trigger && ratio <= trigger) {
                triggered = true
                if (warning) slayerAlert("§c§lFIRE PITS!", playSound = playSound, pitch = soundPitch, stayAlive = 40)
            }
            previousRatio = ratio
        }

        on<SlayerEvent.State> { this@FirePits.reset() }
        on<WorldEvent.Change> { this@FirePits.reset() }
    }

    override fun onDisable() {
        reset()
    }

    private fun reset() {
        scan.reset()
        previousRatio = 1.0
        triggered = false
    }
}
