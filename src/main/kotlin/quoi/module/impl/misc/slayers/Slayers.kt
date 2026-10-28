package quoi.module.impl.misc.slayers

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.api.colour.Colour
import quoi.api.colour.alpha
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.events.core.trackedBy
import quoi.api.skyblock.location.Island
import quoi.module.Module
import quoi.module.impl.misc.Test
import quoi.module.impl.misc.slayers.blaze.BlazeSlayer
import quoi.module.impl.misc.slayers.enderman.BeaconESP
import quoi.module.impl.misc.slayers.enderman.EndermanSlayer
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup.Companion.childOf
import quoi.module.settings.group.SettingGroup.Companion.json
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.romanToInt
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.ui.textPair

@Suppress("unnecessary_safe_call")
object Slayers : Module(
    "Slayers",
    desc = "Various slayer features.",
    area = Island.Skyblock
) {
    private val slayers: Set<ISlayer> = setOf(BlazeSlayer, EndermanSlayer)

    internal val beaconHighlight = highlight(colour = Colour.RED, fillColour = Colour.RED, glow = false)
        .json("Enderman Beacon ESP Style").childOf(BeaconESP.component)
    internal val beaconTracer = tracer(colour = Colour.RED, distance = null)
        .json("Enderman Beacon ESP Tracer").childOf(BeaconESP.component)

    private val esp by switch("Boss ESP")
    private val highlight = highlight(aabbOffset = true).childOf(::esp)
    private val tracer = tracer(distance = null).childOf(::esp)

    private val spawnAlert by switch("Spawn Alert", desc = "Shows an alert when your Slayer boss spawns.")
    private val spawnTime by switch("Spawn Time", desc = "Shows how long your Slayer boss took to spawn in chat.")
    private val killTime by switch("Kill Time", desc = "Shows how long your Slayer boss took to kill in chat.")

    private var spawnStartedAt = 0L
    private var killStartedAt = 0L
    private var finishedBossId: Int? = null

    @Suppress("unused")
    private val debug by textHud("Debug") {
        column {
            debugStrings.forEach { (name, str) ->
                textPair(
                    string = "$name:",
                    supplier = str,
                    labelColour = colour,
                    shadow = shadow,
                    font = font
                )
            }
        }
    }.setting().visibleIf { Test.enabled }

    init {
        on<PacketEvent.ReceivedPost> { handlePacket(this) }

        on<WorldEvent.Change> { reset() }

        on<SlayerEvent.State> {
            val now = System.currentTimeMillis()

            when (new) {
                QuestState.SPAWNING -> {
                    spawnStartedAt = now
                    killStartedAt = 0L
                }

                QuestState.KILLING -> {
                    if (old == QuestState.SPAWNING && spawnStartedAt != 0L && spawnTime) {
                        modMessage("Spawn Time: §a${formatTime(now - spawnStartedAt)}")
                    }
                    killStartedAt = now

                    if (spawnAlert) PlayerUtils.setTitle(
                        title = "§c§lBOSS SPAWNED!",
                        playSound = true,
                        sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
                        pitch = 0f,
                        stayAlive = 30,
                        fadeOut = 10,
                    )
                }

                QuestState.SLAIN -> {
                    if (old == QuestState.KILLING && killStartedAt != 0L && killTime) {
                        modMessage("Kill Time: §a${formatTime(now - killStartedAt)}")
                    }
                    killStartedAt = 0L
                }

                QuestState.FAILED, QuestState.NONE -> {
                    spawnStartedAt = 0L
                    killStartedAt = 0L
                }
            }
        }

        on<RenderEvent.World> {
            if (!esp || questState != QuestState.KILLING) return@on

            ctx.drawSlayer(currentBoss)

            slayers.forEach { slayer ->
                slayer.entitiesForRender.forEach { (it, col) ->
                    ctx.drawSlayer(it, col, false)
                }
            }
        }
    }

    override fun onDisable() {
        reset()
        slayers.forEach { s -> s.features.forEach { it.onDisable() } }
        super.onDisable()
    }

    override fun shouldHandle(event: Event): Boolean = event is WorldEvent.Change || super.shouldHandle(event)

    var questTier = 0
        private set

    var questState = QuestState.NONE
        private set

    private fun handlePacket(event: PacketEvent.ReceivedPost) {
        when (val packet = event.packet) {
            is ClientboundSystemChatPacket -> {
                if (packet.overlay) return
                val text = packet.content.string.noControlCodes.trim()
                when (text) {
                    "YOU COCOONED YOUR SLAYER BOSS", "SLAYER QUEST COMPLETE!" -> updateQuestState(QuestState.SLAIN)
                    "SLAYER QUEST STARTED!" -> {
                        if (currentBoss?.isAlive != true) updateQuestState(QuestState.SPAWNING)
                    }
                    "SLAYER QUEST FAILED!" -> updateQuestState(QuestState.FAILED)
                }
            }

            is ClientboundSetPlayerTeamPacket -> {
                if (currentBoss?.isAlive == true) return
                val params = packet.parameters.orElse(null) ?: return
                val text = (params.playerPrefix.string + params.playerSuffix.string).noControlCodes.trim()
                when {
                    text.contains("Combat") || text.contains("Kills") -> updateQuestState(QuestState.SPAWNING)
                    text == "Boss slain!" -> updateQuestState(QuestState.SLAIN)
                }
            }
        }
    }

    private fun updateQuestState(new: QuestState) {
        val old = questState
        if (old == new) return
        questState = new
        if (new != QuestState.KILLING) {
            currentBoss?.let { finishedBossId = it.id }
            resetBoss()
        }
        SlayerEvent.State(old, new).post()
    }

    private fun resetBoss() {
        currentBoss = null
        questTier = 0
    }

    private fun reset() {
        updateQuestState(QuestState.NONE)
        resetBoss()
        finishedBossId = null
        spawnStartedAt = 0L
        killStartedAt = 0L
    }

    var currentBoss by trackedBy<TickEvent.End, LivingEntity?>(null) { boss ->
        if (boss?.isAlive == true && !boss.isRemoved) return@trackedBy boss
        if (boss?.isDeadOrDying == true) updateQuestState(QuestState.SLAIN)
        else resetBoss()
        if (questState == QuestState.FAILED) return@trackedBy null

        for (owner in getEntities<ArmorStand>()) {
            if (owner.customName?.string.noControlCodes.trim() != "${player.gameProfile.name}'s SLAYER BOSS") continue
            val name = (getEntity(owner.id - 2) as? ArmorStand)?.customName?.string.noControlCodes
            val tier = tierRegex.find(name)?.groupValues?.get(1)?.let(::romanToInt) ?: continue
            val entity = getEntity(owner.id - 3) as? Mob ?: continue
            if (!entity.isAlive || entity.id == finishedBossId) continue

            questTier = tier
            updateQuestState(QuestState.KILLING)
            return@trackedBy entity
        }
        null
    }
        private set

    private fun LevelRenderContext.drawSlayer(entity: LivingEntity?, colour: Colour? = null, main: Boolean = true) {
        if (entity == null || entity.isInvisible || entity.isDeadOrDying) return
        if (!main && colour == null) return
        val box = entity.interpolatedBox
        highlight.draw(
            this,
            box,
            colour = colour ?: Colour.WHITE,
            fillColour = colour?.withAlpha(highlight.fill.alpha) ?: Colour.WHITE.withAlpha(67)
        )

        tracer.draw(this, box.center, colour = colour ?: Colour.WHITE)
    }

    private val debugStrings = buildList {
        add("Boss" to { currentBoss?.displayName?.string })
        add("Tier" to { questTier })
        addAll(slayers.map {
            (it::class.simpleName ?: "Slayer") to { it.debugString }
        })
    }

    private val tierRegex = Regex(" (IV|V|I{1,3}) ")
}
