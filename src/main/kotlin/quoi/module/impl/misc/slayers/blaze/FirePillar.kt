package quoi.module.impl.misc.slayers.blaze

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.colour.Colour
import quoi.api.events.ChatEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.asyncScope
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.impl.misc.slayers.Throttle
import quoi.module.impl.misc.slayers.slayerAlert
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.interact
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ui.textPair

object FirePillar : ToggleableGroup(BlazeSlayer, "Fire pillar", desc = "Warns about and optionally auto-cloaks your fire pillar.") {
    private const val CLOAK_ID = "WITHER_CLOAK"
    private const val PILLAR_GONE_GRACE_MS = 1_000L
    private const val MIN_CLOAK_MS = 2_000L
    private const val CLOAK_TIMEOUT_MS = 8_000L

    private val warning by switch("Warning", true, desc = "Show warning alerts for the fire pillar.")
    private val warnSeconds by slider("Warning time", 2, 1, 8, 1, unit = "s", desc = "Seconds remaining before detonation to trigger the warning.").childOf(::warning)
    private val spawnAlert by switch("Spawn alert", true, desc = "Show an alert immediately when a fire pillar appears.").childOf(::warning)
    private val playSound by switch("Play sound", true, desc = "Play a sound alert when warnings trigger.").childOf(::warning)
    private val soundPitch by slider("Pitch", 1.0f, 0.5f, 2.0f, 0.1f).childOf(::playSound)

    private val autoCloak by switch("Auto cloak", false, desc = "Automatically toggles Wither Cloak before detonation.")
    private val cloakSeconds by slider("Cloak at", 1, 1, 3, 1, unit = "s", desc = "Seconds remaining before detonation to activate Wither Cloak.").childOf(::autoCloak)
    private val autoUncloak by switch("Auto uncloak", false, desc = "Automatically deactivates Wither Cloak after the pillar detonates. Off by default: the veil drops on its own when it runs out.").childOf(::autoCloak)
    private val uncloakDelay by slider("Uncloak delay", 750L, 500L, 4_000L, 50L, unit = "ms", desc = "Delay after detonation before uncloaking.").childOf(::autoUncloak)
    private val autoDelay by slider("Swap delay", 150L, 50L, 1_000L, 50L, unit = "ms", desc = "Delay between swapping, clicking, and restoring slot.").childOf(::autoCloak)
    private val bossRadius by slider("Detection radius", 20.0, 5.0, 40.0, 1.0, unit = "blocks", desc = "Radius around your boss to detect active fire pillar stands.")

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

    private class PillarState {
        var warnedSpawn = false
        var warnedDetonation = false
    }

    private val scan = Throttle(250)
    private val pillarStates = mutableMapOf<Int, PillarState>()
    private var currentSeconds: Int? = null
    private var hadPillar = false
    private var goneSince = 0L
    private var cloakedForPillar = false
    private var cloakedAt = 0L
    private var closeToDetonation = false
    private var cooldownWarned = false

    private var inCloak = false
    private var cloakCooldownEnd = 0L
    private var pendingAction = Action.IDLE
    private var swapBackSlot = -1
    private var cloakGeneration = 0

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

            val now = System.currentTimeMillis()
            if (!scan.ready(now)) return@on
            scan.mark(now)

            val boss = BlazeSlayer.blazeBoss
            val pillars = boss?.let { b ->
                getEntities<ArmorStand>(b.position(), radius = bossRadius).mapNotNull { stand ->
                    val name = stand.customName?.string?.noControlCodes?.trim() ?: return@mapNotNull null
                    val seconds = firePillarRegex.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                    stand.id to seconds
                }.sortedBy { it.second }
            }.orEmpty()

            pillarStates.keys.retainAll(pillars.mapTo(HashSet()) { it.first })
            currentSeconds = pillars.firstOrNull()?.second

            if (pillars.isEmpty()) {
                if (!hadPillar) return@on
                if (goneSince == 0L) goneSince = now
                // stands can be re-created between scans, so wait before calling it finished
                if (now - goneSince >= PILLAR_GONE_GRACE_MS) finishPillars(now)
                return@on
            }
            hadPillar = true
            goneSince = 0L

            for ((id, seconds) in pillars) {
                val state = pillarStates.getOrPut(id) { PillarState() }
                if (state.warnedSpawn) continue
                state.warnedSpawn = true
                if (warning && spawnAlert) {
                    slayerAlert("§c§lFIRE PILLAR!", "§e${seconds}s §7remaining", playSound, soundPitch, 25)
                }
            }

            // only the soonest pillar is worth warning about / cloaking for
            val (soonestId, seconds) = pillars.first()
            val state = pillarStates.getValue(soonestId)
            if (seconds <= 3) closeToDetonation = true
            if (seconds <= warnSeconds && !state.warnedDetonation) {
                state.warnedDetonation = true
                if (warning) {
                    slayerAlert("§4§lDETONATING IN ${seconds}s!", playSound = playSound, pitch = soundPitch, stayAlive = 20)
                }
            }

            if (autoCloak && !cloakedForPillar && !inCloak && seconds <= cloakSeconds) {
                if (triggerCloak(activate = true)) {
                    cloakedForPillar = true
                    cloakedAt = now
                }
            }
        }

        on<SlayerEvent.State> { this@FirePillar.reset() }
        on<WorldEvent.Change> { this@FirePillar.reset() }
    }

    /** Returns true when the cloak swap actually succeeded. */
    private fun triggerCloak(activate: Boolean): Boolean {
        if (pendingAction != Action.IDLE) return false
        if (activate == inCloak) return false
        if (activate && isCloakOnCooldown()) {
            if (!cooldownWarned) {
                modMessage("&cCannot cloak: Wither Cloak is on cooldown!")
                cooldownWarned = true
            }
            return false
        }

        val originalSlot = player.inventory.selectedSlot
        if (!SwapManager.swapById(CLOAK_ID).success) return false

        pendingAction = if (activate) Action.CLOAKING else Action.UNCLOAKING
        swapBackSlot = originalSlot
        val gen = ++cloakGeneration

        asyncScope.launch {
            try {
                delay(jitter(autoDelay, autoDelay / 4).milliseconds)
                if (!enabled || gen != cloakGeneration || pendingAction == Action.IDLE) return@launch
                // another feature may have moved the slot while we waited
                if (player.mainHandItem.skyblockId != CLOAK_ID) return@launch

                modMessage(if (activate) "&aCloaking fire pillar!" else "&cUncloaking fire pillar!")
                interact()
                delay(jitter(autoDelay, autoDelay / 4).milliseconds)
            } finally {
                if (gen == cloakGeneration) {
                    pendingAction = Action.IDLE
                    val slot = swapBackSlot
                    swapBackSlot = -1
                    if (slot in 0..8 && player.inventory.selectedSlot != slot && player.mainHandItem.skyblockId == CLOAK_ID) {
                        SwapManager.swapToSlot(slot)
                    }
                }
            }
        }
        return true
    }

    /**
     * Called on every scan while the pillar stands are gone. Only uncloaks once the
     * countdown actually ran down and the cloak has been held for a moment, so a
     * stand that blinks out between scans can't cut the cloak short.
     */
    private fun finishPillars(now: Long) {
        if (!cloakedForPillar) return
        // pillar vanished before the countdown got low: not a detonation, wait it out
        if (!closeToDetonation && now - cloakedAt < CLOAK_TIMEOUT_MS) return
        if (now - cloakedAt < MIN_CLOAK_MS) return

        cloakedForPillar = false
        if (!autoUncloak) return

        val gen = cloakGeneration
        val wait = jitter(uncloakDelay, 200L)
        asyncScope.launch {
            delay(wait.milliseconds)
            if (!enabled || gen != cloakGeneration || !inCloak) return@launch
            triggerCloak(activate = false)
        }
    }

    private fun jitter(baseMs: Long, spreadMs: Long): Long =
        (baseMs + kotlin.random.Random.nextLong(-spreadMs, spreadMs + 1)).coerceAtLeast(50L)

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
        cloakGeneration++
        if (swapBackSlot in 0..8) SwapManager.swapToSlot(swapBackSlot)
        scan.reset()
        pillarStates.clear()
        currentSeconds = null
        hadPillar = false
        goneSince = 0L
        cloakedForPillar = false
        cloakedAt = 0L
        closeToDetonation = false
        cooldownWarned = false
        inCloak = false
        cloakCooldownEnd = 0L
        pendingAction = Action.IDLE
        swapBackSlot = -1
    }
}
