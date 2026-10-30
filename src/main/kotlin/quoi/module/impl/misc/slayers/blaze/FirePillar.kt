package quoi.module.impl.misc.slayers.blaze

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.getEntities
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.interact
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.textPair

object FirePillar : ToggleableGroup(BlazeSlayer, "Fire pillar", desc = "Warns about and optionally auto-cloaks your fire pillar.") {
    private val warning by switch("Warning", true, desc = "Show warning alerts for the fire pillar.")
    private val warnSeconds by slider("Warning time", 2, 1, 8, 1, unit = "s", desc = "Seconds remaining before detonation to trigger the warning.").childOf(::warning)
    private val spawnAlert by switch("Spawn alert", true, desc = "Show an alert immediately when a fire pillar appears.").childOf(::warning)
    private val playSound by switch("Play sound", true, desc = "Play a sound alert when warnings trigger.").childOf(::warning)
    private val soundPitch by slider("Pitch", 1.0f, 0.5f, 2.0f, 0.1f).childOf(::playSound)

    private val autoCloak by switch("Auto cloak", true, desc = "Automatically toggles Wither Cloak before detonation.")
    private val cloakSeconds by slider("Cloak at", 1, 1, 3, 1, unit = "s", desc = "Seconds remaining before detonation to activate Wither Cloak.").childOf(::autoCloak)
    private val autoUncloak by switch("Auto uncloak", true, desc = "Automatically deactivates Wither Cloak after the pillar detonates.").childOf(::autoCloak)
    private val uncloakDelay by slider("Uncloak delay", 15, 5, 40, 1, unit = "t", desc = "Delay in ticks after detonation before uncloaking.").childOf(::autoUncloak)
    private val autoDelay by slider("Swap delay", 3, 1, 10, 1, unit = "t", desc = "Delay in ticks between swapping, clicking, and restoring slot.").childOf(::autoCloak)
    private val bossRadius by slider("Detection radius", 20.0, 5.0, 40.0, 1.0, unit = "blocks", desc = "Radius to detect active fire pillar stands.")

    @Suppress("unused")
    private val hud by textHud("Fire pillar timer", Colour.RED) {
        visibleIf { this@FirePillar.enabled && currentSeconds != null }
        textPair(
            string = "Fire Pillar:",
            supplier = {
                currentSeconds?.let { s ->
                    val color = when {
                        s <= 1 -> "§4"
                        s <= 3 -> "§c"
                        s <= 5 -> "§e"
                        else -> "§a"
                    }
                    "$color${s}s"
                } ?: "&cInactive"
            },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.setting(desc = "Displays active fire pillar countdown HUD").asParent()

    private val firePillarRegex = Regex("""(\d+)s\s+(\d+)\s+hits""", RegexOption.IGNORE_CASE)

    private enum class Action { IDLE, CLOAKING, UNCLOAKING }

    private var activePillarId: Int? = null
    private var currentSeconds: Int? = null
    private var warnedSpawn = false
    private var warnedDetonation = false
    private var cloakedForPillar = false

    private var inCloak = false
    private var cloakCooldownEnd = 0L
    private var pendingAction = Action.IDLE
    private var swapBackSlot = -1

    init {
        on<ChatEvent.Packet> {
            when (unformatted) {
                "Creeper Veil Activated!" -> inCloak = true
                "Creeper Veil De-activated!" -> disableCloak(5_000L)
                "Creeper Veil De-activated! (Expired)",
                "Not enough mana! Creeper Veil De-activated!" -> disableCloak(10_000L)
            }
        }

        on<TickEvent.End> {
            if (!enabled) return@on
            if (Slayers.questState != QuestState.KILLING && Slayers.questState != QuestState.SPAWNING) {
                reset()
                return@on
            }

            val boss = BlazeSlayer.blazeBoss
            val center = boss?.position() ?: player.position()
            val firePillar = getEntities<ArmorStand>(center, radius = bossRadius).firstOrNull { stand ->
                val name = stand.customName?.string?.noControlCodes?.trim() ?: return@firstOrNull false
                firePillarRegex.containsMatchIn(name)
            }

            if (firePillar == null) {
                if (activePillarId != null) {
                    onPillarFinished()
                }
                currentSeconds = null
                activePillarId = null
                return@on
            }

            val name = firePillar.customName?.string?.noControlCodes?.trim() ?: return@on
            val match = firePillarRegex.find(name) ?: return@on
            val seconds = match.groupValues[1].toIntOrNull() ?: return@on
            currentSeconds = seconds

            // New fire pillar appeared
            if (activePillarId != firePillar.id) {
                activePillarId = firePillar.id
                warnedSpawn = false
                warnedDetonation = false
                cloakedForPillar = false

                if (warning && spawnAlert) {
                    showAlert(
                        title = "§c§lFIRE PILLAR!",
                        subtitle = "§e${seconds}s §7remaining",
                        pitch = soundPitch,
                        stayAlive = 25
                    )
                    warnedSpawn = true
                }
            }

            // Warning alert at or below slider threshold
            if (seconds <= warnSeconds && !warnedDetonation) {
                warnedDetonation = true
                showAlert(
                    title = "§4§lDETONATING IN ${seconds}s!",
                    pitch = soundPitch,
                    stayAlive = 20
                )
            }

            // Auto Wither Cloak at or below cloak threshold
            if (autoCloak && seconds <= cloakSeconds && !cloakedForPillar && !inCloak && pendingAction == Action.IDLE) {
                if (isCloakOnCooldown()) {
                    modMessage("&cCannot cloak: Wither Cloak is on cooldown!")
                } else {
                    cloakedForPillar = true
                    triggerCloak(activate = true)
                }
            }

            // If timer reached 0s, pillar detonated
            if (seconds <= 0) {
                onPillarFinished()
            }
        }

        on<SlayerEvent.State> { this@FirePillar.reset() }
        on<WorldEvent.Change> { this@FirePillar.reset() }
    }

    private fun showAlert(title: String, subtitle: String = "", pitch: Float = soundPitch, stayAlive: Int = 20) {
        if (!warning) return
        PlayerUtils.setTitle(
            title = title,
            subtitle = subtitle,
            playSound = playSound,
            sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
            pitch = pitch,
            stayAlive = stayAlive
        )
    }

    private fun triggerCloak(activate: Boolean) {
        if (pendingAction != Action.IDLE) return
        if (activate && inCloak) return
        if (!activate && !inCloak) return

        val originalSlot = player.inventory.selectedSlot
        val swap = SwapManager.swapById("WITHER_CLOAK")
        if (!swap.success) return

        pendingAction = if (activate) Action.CLOAKING else Action.UNCLOAKING
        swapBackSlot = originalSlot

        scheduleTask(autoDelay) {
            if (!enabled || pendingAction == Action.IDLE) return@scheduleTask
            modMessage(if (activate) "&aCloaking fire pillar!" else "&cUncloaking fire pillar!")
            interact()
        }

        scheduleTask(autoDelay * 2) {
            val slot = swapBackSlot
            swapBackSlot = -1
            pendingAction = Action.IDLE

            if (slot in 0..8 && enabled) {
                SwapManager.swapToSlot(slot)
            }
        }
    }

    private fun onPillarFinished() {
        if (autoCloak && cloakedForPillar) {
            cloakedForPillar = false
            if (autoUncloak) {
                scheduleTask(uncloakDelay) {
                    if (inCloak && pendingAction == Action.IDLE && enabled) {
                        triggerCloak(activate = false)
                    }
                }
            }
        }
    }

    private fun isCloakOnCooldown(): Boolean =
        System.currentTimeMillis() < cloakCooldownEnd

    private fun disableCloak(cooldownMs: Long) {
        inCloak = false
        cloakedForPillar = false
        cloakCooldownEnd = System.currentTimeMillis() + cooldownMs
    }

    override fun onDisable() {
        reset()
    }

    private fun reset() {
        if (swapBackSlot in 0..8 && pendingAction != Action.IDLE) {
            SwapManager.swapToSlot(swapBackSlot)
        }
        activePillarId = null
        currentSeconds = null
        warnedSpawn = false
        warnedDetonation = false
        cloakedForPillar = false
        inCloak = false
        cloakCooldownEnd = 0L
        pendingAction = Action.IDLE
        swapBackSlot = -1
    }
}