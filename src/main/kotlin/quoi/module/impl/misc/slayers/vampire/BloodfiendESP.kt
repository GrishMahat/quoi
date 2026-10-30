package quoi.module.impl.misc.slayers.vampire

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import quoi.api.colour.Colour
import quoi.api.events.EntityEvent
import quoi.api.events.SlayerEvent
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
object BloodfiendESP : ToggleableGroup(
    VampireSlayer,
    "Boss ESP",
    desc = "Highlights the Bloodfiend boss while your own quest fight is active, and alerts when it can be weakened with a Steak.",
) {
    private val steakAlert by switch(
        "Steak alert",
        desc = "Shows a title when the Bloodfiend becomes stakeworthy."
    )

    private var nextSteakScan = 0L
    private var nextSteakAlert = 0L

    // ponytail: ownership = last hit -> owner stand pairing -> nearest Bloodfiend;
    // misattribution only possible if someone else's boss is nearest before you land a hit
    private var ownBossId: Int? = null

    private fun Player.isBloodfiend() =
        this != player && displayName.string.noControlCodes.contains("Bloodfiend")

    private fun bosses(): List<Player> =
        getEntities<Player>().filter { it.isAlive && it.isBloodfiend() }.toList()

    // Skyblocker-documented signal: the boss name stand carries the boss/tier/HP line,
    // and gains "҉" while stakeworthy; a nearby stand shows the owner's username
    private fun Player.standNames(): List<String> =
        getEntities<ArmorStand>(position(), 3.0) { it.isAlive }.map {
            it.customName?.string.noControlCodes.trim()
        }

    fun pinBoss(candidates: List<Player> = bosses()): Player? =
        ownBossId?.let { id -> candidates.firstOrNull { it.id == id } }
            ?: candidates.firstOrNull { boss -> boss.standNames().any { it.contains(player.gameProfile.name) } }
            ?: candidates.minByOrNull { it.position().distanceTo(player.position()) }

    val debug: String
        get() {
            val closest = bosses().minByOrNull { it.position().distanceTo(player.position()) }
                ?: return "no bloodfiend"
            val dist = "%.1f".format(closest.position().distanceTo(player.position()))
            return "quest=${Slayers.questState}, own=$ownBossId, closest=${closest.displayName.string.noControlCodes.trim()} @ ${dist}m"
        }

    val renderBosses: List<Pair<LivingEntity, Colour?>>
        get() {
            if (Slayers.questState != QuestState.KILLING) return emptyList()
            return pinBoss()?.let { listOf(it to Colour.RED) } ?: emptyList()
        }

    // live state for Bloodfiend Assists
    var steakable = false
        private set

    init {
        on<EntityEvent.Attack> {
            val entity = entity as? Player ?: return@on
            if (entity.isBloodfiend()) ownBossId = entity.id
        }

        on<SlayerEvent.State> {
            if (new != QuestState.KILLING) ownBossId = null
        }

        on<TickEvent.End> {
            if (Slayers.questState != QuestState.KILLING) {
                steakable = false
                return@on
            }
            val now = System.currentTimeMillis()
            if (now < nextSteakScan) return@on
            nextSteakScan = now + 250

            val boss = pinBoss()
            if (boss == null) {
                steakable = false
                return@on
            }
            steakable = boss.standNames().any { it.contains("҉") || it.contains("20%") } ||
                (boss.maxHealth > 0f && (boss.health / boss.maxHealth) <= 0.20f)

            if (!steakAlert || !steakable || now < nextSteakAlert) return@on
            nextSteakAlert = now + 5_000
            PlayerUtils.setTitle(
                title = "§c§lSTEAK!",
                playSound = true,
                sound = SoundEvents.EXPERIENCE_ORB_PICKUP,
                pitch = 0f,
                stayAlive = 15,
                fadeOut = 5,
            )
        }

        on<WorldEvent.Change> { ownBossId = null }
    }

    override fun onDisable() {
        ownBossId = null
        nextSteakScan = 0L
        nextSteakAlert = 0L
        steakable = false
        super.onDisable()
    }
}
