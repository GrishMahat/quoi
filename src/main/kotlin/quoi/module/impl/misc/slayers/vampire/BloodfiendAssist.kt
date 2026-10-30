package quoi.module.impl.misc.slayers.vampire

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.EntityHitResult
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.asyncScope
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.leftClick
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager
import kotlin.time.Duration.Companion.milliseconds

@Suppress("unused_expression")
object BloodfiendAssist : ToggleableGroup(
    VampireSlayer,
    "Bloodfiend assist",
    desc = "Bloodfiend fight automation: melon healing, Holy Ice, Steak Stake swapping and Impel prompt execution.",
) {
    private val autoHeal by switch("Auto heal", desc = "Uses bigger melons the more hurt you are (Healing 7 / Juicy 9 / Luscious 12 hearts).")
    private val autoHolyIce by switch("Auto Holy Ice", desc = "Uses Holy Ice when the Twinclaws timer drops below 0.5s.")
    private val autoStakeSwap by switch("Auto stake swap", desc = "Swaps to your best Steak Stake whenever the boss becomes stakeworthy.")
    private val autoImpel by switch("Auto impel", desc = "Performs the Impel prompts automatically (jump, sneak, click, look up/down).")
    private val autoTotem by switch("Auto totem", desc = "Attacks the Killer Spring stand you're aiming at, human-paced.")

    private data class Melon(val id: String, val display: String, val hearts: Int)

    private val melons = listOf(
        Melon("HEALING_MELON", "Healing Melon", 7),
        Melon("JUICY_HEALING_MELON", "Juicy Healing Melon", 9),
        Melon("LUSCIOUS_HEALING_MELON", "Luscious Healing Melon", 12),
    )
    private val holyIceId = "HOLY_ICE"
    private val stakeIds = listOf(
        "SUPER_SHARP_STEAK_STAKE",
        "SUPER_SHARP_STABBY_STEAK_STAKE",
        "SHARP_STEAK_STAKE",
        "STEAK_STAKE",
    )

    private const val PANIC_HEARTS = 2.0f
    private const val HOLY_ICE_TRIGGER_SECONDS = 0.5

    private val impelRegex = Regex("Impel\\s*:?\\s*(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)s\\b", RegexOption.IGNORE_CASE)

    private var healUntil = 0L
    private var holyIceUntil = 0L
    private var swapUntil = 0L
    private var impelUntil = 0L
    private var nextTotemAttack = 0L

    private var lastImpelKey: String? = null
    private var lastImpelAt = 0L
    private var missingIceLogged = false
    private var missingStakeLogged = false

    private fun rnd(min: Long, max: Long) = kotlin.random.Random.nextLong(min, max + 1)
    private fun rndF(min: Int, max: Int) = kotlin.random.Random.nextDouble(min.toDouble(), max.toDouble()).toFloat()

    init {
        on<TickEvent.End> {
            if (mc.screen != null) return@on
            if (player.isDeadOrDying == true) return@on
            val now = System.currentTimeMillis()
            if (autoHeal) attemptHeal(now)
            if (Slayers.questState != QuestState.KILLING) return@on
            if (autoHolyIce) attemptHolyIce(now)
            if (autoStakeSwap) attemptStakeSwap(now)
            if (autoTotem) attemptTotem(now)
        }

        // Impel prompts arrive on title, subtitle, action bar, or system chat overlay
        on<PacketEvent.Received, ClientboundSetTitleTextPacket> { runImpel(packet.text().string) }
        on<PacketEvent.Received, ClientboundSetSubtitleTextPacket> { runImpel(packet.text().string) }
        on<PacketEvent.Received, ClientboundSetActionBarTextPacket> { runImpel(packet.text().string) }
        on<PacketEvent.Received, ClientboundSystemChatPacket> {
            if (packet.overlay) runImpel(packet.content.string)
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun reset() {
        healUntil = 0L
        holyIceUntil = 0L
        swapUntil = 0L
        impelUntil = 0L
        nextTotemAttack = 0L
        lastImpelKey = null
        lastImpelAt = 0L
        missingIceLogged = false
        missingStakeLogged = false
    }

    // ===== melons =====
    private fun attemptHeal(now: Long) {
        if (now < healUntil) return
        val p = player
        val max = p.getAttributeValue(Attributes.MAX_HEALTH)
        val hp = p.health
        if (hp >= max) return
        val missingHearts = (max - hp) / 2f
        val maxHearts = max / 2f
        val panic = maxHearts >= PANIC_HEARTS * 2 && hp <= max * 0.2f
        val needHeal = panic || missingHearts >= 3.0f || hp <= max * 0.6f
        if (!needHeal) return

        // Pick highest tier melon available that fits needs
        val chosen = melons.asReversed().firstOrNull { hasInInventory(it.id) } ?: return
        val slot = hotbarSlotOf(chosen.id) ?: return

        selectAndUse(slot, "[Rift] Used ${chosen.display}.", restoreSlot = true)
        healUntil = now + rnd(600, 1000)
    }

    // ===== Holy Ice =====
    private fun attemptHolyIce(now: Long) {
        if (now - TwinClaws.lastScanAt > 800) return
        if (!TwinClaws.active) return
        val s = TwinClaws.seconds
        if (s < 0 || s > HOLY_ICE_TRIGGER_SECONDS) return
        if (now < holyIceUntil) return

        val slot = hotbarSlotOf(holyIceId)
        if (slot == null) {
            if (!missingIceLogged) {
                modMessage("[Rift] Holy Ice not found in hotbar.")
                missingIceLogged = true
            }
            return
        }
        missingIceLogged = false
        selectAndUse(slot, "[Rift] Used Holy Ice.", restoreSlot = true)
        holyIceUntil = now + rnd(1400, 2000)
    }

    // ===== Steak Stake =====
    private fun attemptStakeSwap(now: Long) {
        if (!BloodfiendESP.steakable) return
        if (now < swapUntil) return
        val id = stakeIds.firstOrNull { hasInInventory(it) } ?: return
        val slot = hotbarSlotOf(id)
        if (slot == null) {
            if (!missingStakeLogged) {
                modMessage("[Rift] Steak Stake not found in hotbar.")
                missingStakeLogged = true
            }
            return
        }
        missingStakeLogged = false
        val p = player
        if (slot == p.inventory.selectedSlot) return

        if (SwapManager.swapToSlot(slot).success) {
            modMessage("[Rift] Swapped to $id.")
            swapUntil = now + 250
        }
    }

    // ===== Impel (jump / sneak / click / look up/down prompts) =====
    private fun runImpel(raw: String) {
        if (!autoImpel || Slayers.questState != QuestState.KILLING) return
        val match = impelRegex.find(raw.noControlCodes) ?: return
        val action = parseAction(match.groupValues[1]) ?: return

        val now = System.currentTimeMillis()
        if (action.name == lastImpelKey && now - lastImpelAt < 400) return
        if (now < impelUntil) return
        lastImpelKey = action.name
        lastImpelAt = now

        val delayMs = rnd(90, 180)
        impelUntil = now + delayMs + 120
        asyncScope.launch {
            delay(delayMs.milliseconds)
            executeImpel(action)
        }
    }

    private suspend fun executeImpel(action: ImpelAction) {
        val p = player
        if (mc.screen != null || Slayers.questState != QuestState.KILLING) return
        modMessage("[Rift] Impel: ${action.name.lowercase().replace('_', ' ')}.")

        when (action) {
            ImpelAction.JUMP -> tapKey(mc.options.keyJump)
            ImpelAction.SNEAK -> tapKey(mc.options.keyShift)
            ImpelAction.LOOK_UP -> {
                val originalPitch = p.xRot
                p.xRot = -rndF(70, 89)
                delay(rnd(60, 120).milliseconds)
                p.leftClick()
                delay(rnd(60, 100).milliseconds)
                p.xRot = originalPitch
            }
            ImpelAction.LOOK_DOWN -> {
                val originalPitch = p.xRot
                p.xRot = rndF(70, 89)
                delay(rnd(60, 120).milliseconds)
                p.leftClick()
                delay(rnd(60, 100).milliseconds)
                p.xRot = originalPitch
            }
            ImpelAction.CLICK -> {
                p.leftClick()
                delay(rnd(30, 60).milliseconds)
                p.rightClick()
            }
        }
    }

    private suspend fun tapKey(key: KeyMapping) {
        key.isDown = true
        delay(rnd(80, 140).milliseconds)
        key.isDown = false
    }

    private fun parseAction(raw: String): ImpelAction? {
        val text = raw.uppercase().replace(Regex("[^A-Z ]"), " ").replace(Regex("\\s+"), " ").trim()
        return when {
            text.contains("JUMP") -> ImpelAction.JUMP
            text.contains("SNEAK") || text.contains("CROUCH") -> ImpelAction.SNEAK
            (text.contains("LOOK") || text.contains("CLICK")) && text.contains("UP") -> ImpelAction.LOOK_UP
            (text.contains("LOOK") || text.contains("CLICK")) && text.contains("DOWN") -> ImpelAction.LOOK_DOWN
            text.contains("CLICK") -> ImpelAction.CLICK
            else -> null
        }
    }

    private enum class ImpelAction { JUMP, SNEAK, LOOK_UP, LOOK_DOWN, CLICK }

    // ===== breaker =====
    private fun attemptTotem(now: Long) {
        val p = player
        if (now < nextTotemAttack) return
        val stand = (mc.hitResult as? EntityHitResult)?.entity as? ArmorStand ?: return
        if (!stand.isAlive || p.position().distanceTo(stand.position()) > 4.0) return
        if (!BloodItems.isKillerSpringStand(stand)) return

        nextTotemAttack = now + rnd(120, 250)
        p.leftClick()
    }

    // ===== item helpers =====
    private fun selectAndUse(slot: Int, message: String, reaction: LongRange = 100L..250L, restoreSlot: Boolean = false) {
        val p = player
        val originalSlot = p.inventory.selectedSlot
        if (originalSlot == slot) {
            if (mc.screen == null) {
                p.rightClick()
                modMessage(message)
            }
            return
        }

        if (!SwapManager.swapToSlot(slot).success) return

        asyncScope.launch {
            delay(rnd(reaction.first, reaction.last).milliseconds)
            val q = player
            if (mc.screen != null) return@launch
            q.rightClick()
            modMessage(message)

            if (restoreSlot && originalSlot in 0..8) {
                delay(rnd(60, 120).milliseconds)
                SwapManager.swapToSlot(originalSlot)
            }
        }
    }

    private fun hotbarSlotOf(id: String): Int? {
        val inv = player.inventory
        return (0..8).firstOrNull { i ->
            val stack = inv.getItem(i)
            !stack.isEmpty && stack.skyblockId == id
        }
    }

    private fun hasInInventory(id: String): Boolean =
        player.inventory.let { inv -> (0..35).any { inv.getItem(it).skyblockId == id } }
}
