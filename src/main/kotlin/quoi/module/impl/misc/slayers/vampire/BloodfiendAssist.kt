package quoi.module.impl.misc.slayers.vampire

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.EntityHitResult
import quoi.api.events.PacketEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.asyncScope
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.leftClick
import quoi.utils.skyblock.player.PlayerUtils.rightClick
import quoi.utils.skyblock.player.SwapManager

@Suppress("unused_expression")
object BloodfiendAssist : ToggleableGroup(
    VampireSlayer,
    "Bloodfiend assist",
    desc = "Bloodfiend fight automation: melon healing, Holy Ice, Steak Stake swapping and Impel prompt execution.",
) {
    // TODO(auto-heal):
    // Current healing logic is unreliable in SkyBlock, especially in the Rift,
    // Rework health tracking, hotbar item detection, and healing priority before enabling.
    // Add support for both fixed-threshold and dynamic healing.
    // Dynamic healing should adapt its trigger threshold based on recent damage,
    // helping prevent unnecessary healing while reacting faster to heavy damage.
    // private val autoHeal by switch("Auto heal", desc = "Uses the best healing melon in your hotbar when hurt.")
    // private val healAt by slider("Heal at", 60, 10, 90, 5, unit = "%", desc = "Uses a healing melon below this health percentage.").childOf(::autoHeal)

    private val autoHolyIce by switch("Auto Holy Ice", desc = "Uses Holy Ice when the Twinclaws timer drops below the alert delay.")
    private val holyIceAt by slider("Holy Ice at", 0.5, 0.1, 5.0, 0.1, unit = "s", desc = "Uses Holy Ice when the Twinclaws timer drops below this.").childOf(::autoHolyIce)

    private val autoStakeSwap by switch("Auto stake swap", desc = "Swaps to your best Steak Stake whenever the boss becomes stakeworthy.")

    private val autoImpel by switch("Auto impel", desc = "Performs the Impel prompts automatically (jump, sneak, click, look up/down).")
    private val impelDelay by slider("Impel delay", 120L, 0L, 400L, 10L, unit = "ms", desc = "Delay before performing an Impel prompt.").childOf(::autoImpel)

    private val autoTotem by switch("Auto totem", desc = "Attacks the Killer Spring stand you're aiming at, human-paced.")
    private val totemDelay by slider("Attack delay", 180L, 50L, 500L, 10L, unit = "ms", desc = "Delay between attacks on a Killer Spring.").childOf(::autoTotem)

    // TODO(auto-heal): re-enable together with the settings above.
    // private data class Melon(val id: String, val display: String, val hearts: Int)
    //
    // private val melons = listOf(
    //     Melon("HEALING_MELON", "Healing Melon", 7),
    //     Melon("JUICY_HEALING_MELON", "Juicy Healing Melon", 9),
    //     Melon("LUSCIOUS_HEALING_MELON", "Luscious Healing Melon", 12),
    // )
    private val holyIceId = "HOLY_ICE"
    private val stakeIds = listOf(
        "SUPER_SHARP_STEAK_STAKE",
        "SUPER_SHARP_STABBY_STEAK_STAKE",
        "SHARP_STEAK_STAKE",
        "STEAK_STAKE",
    )

    private val impelRegex = Regex("Impel\\s*:?\\s*(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)s\\b", RegexOption.IGNORE_CASE)

    // private var healUntil = 0L
    private var holyIceUntil = 0L
    private var impelUntil = 0L
    private var nextTotemAttack = 0L
    private var itemUseUntil = 0L

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
            if (Slayers.questState != QuestState.KILLING) return@on
            // Holy Ice first: it is the lethal-timing button
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

        on<SlayerEvent.State> { reset() }
        on<WorldEvent.Change> { reset() }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun reset() {
        // healUntil = 0L
        holyIceUntil = 0L
        impelUntil = 0L
        nextTotemAttack = 0L
        itemUseUntil = 0L
        lastImpelKey = null
        lastImpelAt = 0L
        missingIceLogged = false
        missingStakeLogged = false
    }

    // ===== melons (disabled, see TODO above) =====
    // private fun attemptHeal(now: Long) {
    //     if (now < healUntil || now < itemUseUntil) return
    //     // never steal the slot right before Holy Ice has to go out
    //     if (autoHolyIce && VampireSlayer.twinclawsSeconds?.let { it <= holyIceAt } == true) return
    //
    //     val p = player
    //     val max = p.getAttributeValue(Attributes.MAX_HEALTH)
    //     if (max <= 0f) return
    //     val ratio = p.health / max
    //     // hysteresis: heal below the threshold, but don't nibble at the line once topped up
    //     if (ratio >= healAt / 100f) return
    //
    //     // best melon in the hotbar
    //     val (melon, slot) = melons.asReversed().firstNotNullOfOrNull { m ->
    //         hotbarSlotOf(m.id)?.let { m to it }
    //     } ?: return
    //
    //     selectAndUse(slot, "[Rift] Used ${melon.display}.")
    //     healUntil = now + rnd(1_200, 1_800)
    // }
    //

    // ===== Holy Ice =====
    private fun attemptHolyIce(now: Long) {
        if (now < itemUseUntil) return
        val seconds = VampireSlayer.twinclawsSeconds ?: return
        if (seconds > holyIceAt || now < holyIceUntil) return

        val slot = hotbarSlotOf(holyIceId) ?: run {
            if (!missingIceLogged) {
                modMessage("[Rift] Holy Ice not found in hotbar.")
                missingIceLogged = true
            }
            return
        }
        missingIceLogged = false
        selectAndUse(slot, "[Rift] Used Holy Ice.")
        holyIceUntil = now + rnd(1400, 2000)
    }

    // ===== Steak Stake =====
    private fun attemptStakeSwap(now: Long) {
        if (!VampireSlayer.steakable || now < itemUseUntil) return

        val (id, slot) = stakeIds.firstNotNullOfOrNull { id ->
            hotbarSlotOf(id)?.let { id to it }
        } ?: run {
            if (!missingStakeLogged) {
                modMessage("[Rift] Steak Stake not found in hotbar.")
                missingStakeLogged = true
            }
            return
        }
        missingStakeLogged = false
        if (slot == player.inventory.selectedSlot) return

        if (SwapManager.swapToSlot(slot).success) modMessage("[Rift] Swapped to $id.")
    }

    // ===== Impel (jump / sneak / click / look up/down prompts) =====
    private fun runImpel(raw: String) {
        if (!autoImpel || Slayers.questState != QuestState.KILLING) return
        val text = raw.noControlCodes
        val match = impelRegex.find(text) ?: return
        val action = parseAction(match.groupValues[1]) ?: return

        val now = System.currentTimeMillis()
        // the prompt counts down (Jump 4s -> 3s -> 2s), so dedupe on the action + text without numbers
        if (now - lastImpelAt > 1_500) lastImpelKey = null
        val key = action.name + text.replace(Regex("[0-9.]+"), "")
        val repeat = key == lastImpelKey
        lastImpelAt = now
        if (repeat || now < impelUntil) return
        lastImpelKey = key

        impelUntil = now + impelDelay + 120
        asyncScope.launch {
            delay(impelDelay.milliseconds)
            executeImpel(action)
        }
    }

    private suspend fun executeImpel(action: ImpelAction) {
        if (mc.screen != null || Slayers.questState != QuestState.KILLING) return
        modMessage("[Rift] Impel: ${action.name.lowercase().replace('_', ' ')}.")

        when (action) {
            ImpelAction.JUMP -> tapKey(mc.options.keyJump)
            ImpelAction.SNEAK -> tapKey(mc.options.keyShift)
            ImpelAction.LOOK_UP -> lookAndClick(up = true)
            ImpelAction.LOOK_DOWN -> lookAndClick(up = false)
            ImpelAction.CLICK -> {
                player.leftClick()
                delay(rnd(30, 60).milliseconds)
                player.rightClick()
            }
        }
    }

    private suspend fun lookAndClick(up: Boolean) {
        val p = player
        val originalPitch = p.xRot
        try {
            p.xRot = if (up) -rndF(70, 89) else rndF(70, 89)
            delay(rnd(60, 120).milliseconds)
            p.leftClick()
            delay(rnd(60, 100).milliseconds)
        } finally {
            p.xRot = originalPitch
        }
    }

    private suspend fun tapKey(key: KeyMapping) {
        key.isDown = true
        try {
            delay(rnd(80, 140).milliseconds)
        } finally {
            key.isDown = false
        }
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
        if (now < nextTotemAttack) return
        val p = player
        val stand = (mc.hitResult as? EntityHitResult)?.entity as? ArmorStand ?: return
        if (!stand.isAlive || p.position().distanceTo(stand.position()) > 4.0) return
        if (!BloodItems.isKillerSpringStand(stand)) return

        nextTotemAttack = now + totemDelay + rnd(-40, 40)
        p.leftClick()
    }

    // ===== item helpers =====
    private fun selectAndUse(slot: Int, message: String) {
        val p = player
        val originalSlot = p.inventory.selectedSlot
        val reaction = rnd(100, 250)
        itemUseUntil = System.currentTimeMillis() + reaction + 150

        if (originalSlot == slot) {
            if (mc.screen == null) {
                p.rightClick()
                modMessage(message)
            }
            itemUseUntil = 0L
            return
        }

        if (!SwapManager.swapToSlot(slot).success) {
            itemUseUntil = 0L
            return
        }

        asyncScope.launch {
            try {
                delay(reaction.milliseconds)
                if (mc.screen != null) return@launch
                player.rightClick()
                modMessage(message)
                delay(rnd(60, 120).milliseconds)
            } finally {
                itemUseUntil = 0L
                // only swap back if the slot we took is still selected
                if (mc.player?.inventory?.selectedSlot == slot) SwapManager.swapToSlot(originalSlot)
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
}
