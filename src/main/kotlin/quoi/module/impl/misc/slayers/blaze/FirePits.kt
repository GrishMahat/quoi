package quoi.module.impl.misc.slayers.blaze

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.SoundUtils
import quoi.utils.StringUtils
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils

object FirePits : ToggleableGroup(BlazeSlayer, "Fire pits", desc = "Warns when fire pits spawn at 33% boss health.") {
    private val warning by switch("Warning", true)
    private val soundAlert by switch("Sound alert", true).childOf(::warning)
    private val pitch by slider("Sound pitch", 0.8f, 0.5f, 2.0f, 0.1f).childOf(::soundAlert)

    private val healthRegex = Regex("""([\d,]+(?:\.\d+)?[BMK]?)(?:\s*/\s*([\d,]+(?:\.\d+)?[BMK]?))?\s*❤""", RegexOption.IGNORE_CASE)

    private var previousRatio = 1.0
    private var warningUntil = 0L
    private var triggered = false
    private var tickCount = 0

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
                previousRatio = 1.0
                triggered = false
                return@on
            }

            // Find the nametag ArmorStand floating near the boss containing the boss's current health
            val bossStand = getEntities<ArmorStand>(boss.position(), radius = 3.0) { stand ->
                val name = stand.customName?.string?.noControlCodes?.trim() ?: return@getEntities false
                name.contains("❤") && !name.endsWith(" 0❤")
            }.firstOrNull()

            if (bossStand != null) {
                val name = bossStand.customName?.string?.noControlCodes?.trim() ?: return@on
                val match = healthRegex.find(name)
                if (match != null) {
                    val current = StringUtils.parseNumber(match.groupValues[1])
                    val max = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(StringUtils::parseNumber)
                        ?: when (Slayers.questTier) {
                            3 -> 45_000_000.0
                            4 -> 150_000_000.0
                            else -> 0.0
                        }

                    if (max > 0.0 && current > 0.0) {
                        val ratio = current / max

                        if (!triggered && previousRatio > 0.33 && ratio <= 0.33) {
                            triggered = true
                            warningUntil = System.currentTimeMillis() + 2_000L

                            if (warning) {
                                PlayerUtils.setTitle(
                                    title = "§c§lFIRE PITS!",
                                    playSound = soundAlert,
                                    sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
                                    pitch = pitch,
                                    stayAlive = 40
                                )
                            }
                        }

                        previousRatio = ratio
                    }
                }
            }

            // Periodic warning sound cue matching SkyHanni behavior
            val now = System.currentTimeMillis()
            if (soundAlert && warningUntil > now) {
                tickCount++
                if (tickCount % 10 == 0) {
                    SoundUtils.play(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch = pitch)
                }
            } else {
                tickCount = 0
            }
        }

        on<SlayerEvent.State> { this@FirePits.reset() }
        on<WorldEvent.Change> { this@FirePits.reset() }
    }

    override fun onDisable() {
        reset()
    }

    private fun reset() {
        previousRatio = 1.0
        warningUntil = 0L
        triggered = false
        tickCount = 0
    }
}