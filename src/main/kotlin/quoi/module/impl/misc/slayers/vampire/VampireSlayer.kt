package quoi.module.impl.misc.slayers.vampire

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import quoi.api.colour.Colour
import quoi.api.events.EntityEvent
import quoi.api.events.SlayerEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.api.skyblock.location.Island
import quoi.module.impl.misc.slayers.ISlayer
import quoi.module.impl.misc.slayers.QuestState
import quoi.module.impl.misc.slayers.Slayers
import quoi.module.impl.misc.slayers.Throttle
import quoi.module.settings.group.SettingGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes

object VampireSlayer : SettingGroup(Slayers, "Bloodfiend", area = Island.Rift), ISlayer {
    override val features = setOf(TwinClaws, ManiaAlert, BloodItems, BloodfiendAssist)

    override val entitiesForRender: List<Pair<LivingEntity, Colour?>>
        get() = emptyList()

    override val debugString: String
        get() {
            val b = boss ?: return "no bloodfiend"
            val dist = "%.1f".format(b.position().distanceTo(player.position()))
            return "quest=${Slayers.questState}, own=$ownBossId, boss=${b.displayName.string.noControlCodes.trim()} @ ${dist}m"
        }

    override val running: Boolean
        get() = super.running && features.any { it.enabled }

    private val scan = Throttle(250)
    private val clawRegex = Regex("TWINCLAWS (\\d+(?:\\.\\d+)?)s")

    // ponytail: ownership = last hit -> owner stand pairing -> nearest Bloodfiend;
    // misattribution only possible if someone else's boss is nearest before you land a hit
    private var ownBossId: Int? = null

    var boss: Player? = null
        private set
    var standNames: List<String> = emptyList()
        private set
    var twinclawsSeconds: Double? = null
        private set
    var steakable = false
        private set

    init {
        on<EntityEvent.Attack> {
            val entity = entity as? Player ?: return@on
            if (entity.isBloodfiend()) ownBossId = entity.id
        }

        on<TickEvent.End> { update() }

        on<SlayerEvent.State> { reset() }
        on<WorldEvent.Change> { reset() }
    }

    private fun update() {
        if (Slayers.questState != QuestState.KILLING) {
            reset()
            return
        }
        val now = System.currentTimeMillis()
        if (!scan.ready(now)) return
        scan.mark(now)

        val pinned = pinBoss()
        boss = pinned
        standNames = pinned?.let(::standNamesOf).orEmpty()
        twinclawsSeconds = standNames.mapNotNull { clawRegex.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }.minOrNull()
        steakable = pinned != null && (
            standNames.any { it.contains("҉") || it.contains("20%") } ||
                (pinned.maxHealth > 0f && pinned.health / pinned.maxHealth <= 0.20f)
            )
    }

    private fun Player.isBloodfiend() =
        this != player && displayName.string.noControlCodes.contains("Bloodfiend")

    private fun bosses(): List<Player> =
        getEntities<Player>().filter { it.isAlive && it.isBloodfiend() }.toList()

    // Skyblocker-documented signal: the boss name stand carries the boss/tier/HP line,
    // and gains "҉" while stakeworthy; a nearby stand shows the owner's username
    private fun standNamesOf(boss: Player, radius: Double = 6.0): List<String> =
        getEntities<ArmorStand>(boss.position(), radius) { it.isAlive }.map {
            it.customName?.string.noControlCodes.trim()
        }

    private fun pinBoss(candidates: List<Player> = bosses()): Player? =
        ownBossId?.let { id -> candidates.firstOrNull { it.id == id } }
            ?: candidates.firstOrNull { b -> standNamesOf(b, 3.0).any { it.contains(player.gameProfile.name) } }
            ?: candidates.minByOrNull { it.position().distanceTo(player.position()) }

    private fun reset() {
        scan.reset()
        ownBossId = null
        boss = null
        standNames = emptyList()
        twinclawsSeconds = null
        steakable = false
    }
}
