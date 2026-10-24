package quoi.module.impl.misc.slayers

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.LivingEntity
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
import quoi.utils.EntityUtils.getEntity
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.romanToInt
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.ui.textPair

//? if <26.2 {
import net.minecraft.world.entity.EntityType as EntityTypes
//?} else {
/*import net.minecraft.world.entity.EntityTypes
*///?}

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
    private var lastBossSpawn: ClientboundAddEntityPacket? = null

    private data class BossInfo(val entity: LivingEntity, val tier: Int)

    private var lastBoss: BossInfo? = null
    private var cocoonTicks = 0
    private var hatchedBoss = false

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

        on<TickEvent.Server> {
            if (cocoonTicks > 0) cocoonTicks--
        }

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
            is ClientboundAddEntityPacket -> {
                if (packet.type !in bossTypes.values) return
                if (cocoonTicks > 0 && packet.type != lastBoss?.entity?.type) return
                lastBossSpawn = packet
            }

            is ClientboundSystemChatPacket -> {
                if (packet.overlay) return
                val text = packet.content.string.noControlCodes.trim()
                val match = spawnRegex.matchEntire(text)
                if (match != null) {
                    val type = bossTypes[match.groupValues[1]] ?: return
                    val spawn = lastBossSpawn
                    lastBossSpawn = null
                    if (spawn?.type != type) return

                    trackBoss(spawn, romanToInt(match.groupValues[2]))
                }

                when (text) {
                    "YOU COCOONED YOUR SLAYER BOSS" -> {
                        if (lastBoss == null) return
                        cocoonTicks = 140
                        lastBossSpawn = null
                    }
                    "SLAYER QUEST STARTED!" -> {
                        val boss = lastBoss
                        val spawn = lastBossSpawn
                        if (cocoonTicks > 0 && boss != null && spawn?.type == boss.entity.type &&
                            spawn.id != boss.entity.id && trackBoss(spawn, boss.tier, hatched = true)
                        ) return

                        updateQuestState(QuestState.SPAWNING)
                    }
                    "SLAYER QUEST COMPLETE!" -> updateQuestState(QuestState.SLAIN)
                    "SLAYER QUEST FAILED!" -> updateQuestState(QuestState.FAILED)
                }
            }

            is ClientboundSetPlayerTeamPacket -> {
                if (cocoonTicks > 0 || (hatchedBoss && currentBoss?.isAlive == true)) return
                val params = packet.parameters.orElse(null) ?: return
                val text = (params.playerPrefix.string + params.playerSuffix.string).noControlCodes.trim()
                when {
                    text.contains("Combat") || text.contains("Kills") -> updateQuestState(QuestState.SPAWNING)
                    text == "Slay the boss!" -> updateQuestState(QuestState.KILLING)
                    text == "Boss slain!" -> updateQuestState(QuestState.SLAIN)
                }
            }
        }
    }

    private fun trackBoss(spawn: ClientboundAddEntityPacket, tier: Int, hatched: Boolean = false): Boolean {
        val boss = (getEntity(spawn.id) as? LivingEntity)?.takeIf { it.isAlive } ?: return false
        currentBoss = boss
        questTier = tier
        lastBoss = BossInfo(boss, tier)
        lastBossSpawn = null
        cocoonTicks = 0
        hatchedBoss = hatched
        updateQuestState(QuestState.KILLING)
        return true
    }

    private fun updateQuestState(new: QuestState) {
        val old = questState
        if (old == new) return
        questState = new
        if (new != QuestState.KILLING) resetBoss()
        if (new == QuestState.SPAWNING || new == QuestState.FAILED || new == QuestState.NONE) {
            lastBoss = null
            cocoonTicks = 0
        }
        SlayerEvent.State(old, new).post()
    }

    private fun resetBoss() {
        lastBossSpawn = null
        currentBoss = null
        questTier = 0
        hatchedBoss = false
    }

    private fun reset() {
        updateQuestState(QuestState.NONE)
        resetBoss()
        lastBoss = null
        cocoonTicks = 0
        spawnStartedAt = 0L
        killStartedAt = 0L
    }

    var currentBoss by trackedBy<TickEvent.End, LivingEntity?>(null) { boss ->
        if (questState != QuestState.KILLING) return@trackedBy null

        boss?.takeUnless { it.isDeadOrDying || it.isRemoved }
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

    private val spawnRegex = Regex("SLAYER BOSS! The (.+) (IV|V|I{1,3}) spawned!")
    private val bossTypes = mapOf(
        "Revenant Horror" to EntityTypes.ZOMBIE,
        "Atoned Horror" to EntityTypes.ZOMBIE,
        "Tarantula Broodfather" to EntityTypes.SPIDER,
        "Conjoined Brood" to EntityTypes.SPIDER,
        "Sven Packmaster" to EntityTypes.WOLF,
        "Voidgloom Seraph" to EntityTypes.ENDERMAN,
        "Inferno Demonlord" to EntityTypes.BLAZE,
    )
}
