package quoi.module.impl.misc.slayers.blaze

import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import net.minecraft.world.phys.EntityHitResult
import quoi.api.events.ChatEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.item.ItemUtils.lore
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.SwapManager
import java.util.concurrent.ConcurrentHashMap

object AutoAttune : ToggleableGroup(BlazeSlayer, "Auto attune") {
    private val shieldRegex = Regex("\\b(ASHEN|AURIC|SPIRIT|CRYSTAL|IMMUNE)\\b")
    private val strikeRegex = Regex("^Strike using the (\\w+) attunement")
    private val daggerIds = Attunement.entries.flatMap { it.daggers.toList() }.toSet()

    private val expected = ConcurrentHashMap<String, Attunement>()
    private val clickedAt = ConcurrentHashMap<String, Long>()

    init {
        on<TickEvent.Start> {
            if (mc.screen != null) return@on
            val attunement = target() ?: return@on
            if (!hasDagger(attunement)) return@on

            val result = SwapManager.swapById(*attunement.daggers)
            if (!result.success) return@on

            val family = attunement.family
            val now = System.currentTimeMillis()
            clickedAt[family]?.let { clicked ->
                if (now - clicked < 1000L) return@on
                clickedAt.remove(family)
                expected.remove(family)
            }

            val held = heldAttunement()
            if (held != null && held == expected[family]) expected.remove(family)

            val current = expected[family] ?: held ?: return@on
            if (current == attunement) return@on

            PlayerUtils.interact()
            expected[family] = attunement
            clickedAt[family] = now
        }

        on<PacketEvent.ReceivedPost, ClientboundSetSubtitleTextPacket> { confirm(packet.text.string) }

        on<PacketEvent.ReceivedPost, ClientboundSetTitleTextPacket> { confirm(packet.text.string) }

        on<ChatEvent.Packet> {
            strikeRegex.find(unformatted)?.let { match ->
                val needed = attunementOf(match.groupValues[1].uppercase())
                if (needed != null && !clickedAt.containsKey(needed.family)) expected.remove(needed.family)
            }

            if (unformatted.startsWith("Strike using the") ||
                unformatted.startsWith("Your hit was reduced by Hellion Shield!")) cancel()
        }

        on<WorldEvent.Change> { reset() }
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun reset() {
        expected.clear()
        clickedAt.clear()
    }

    private val Attunement.family: String
        get() = daggers.first()

    private fun attunementOf(word: String?) = Attunement.entries.firstOrNull { it.name == word }

    private fun confirm(text: String) {
        val attunement = attunementOf(shieldRegex.findAll(text.noControlCodes).lastOrNull()?.value) ?: return
        expected[attunement.family] = attunement
        clickedAt.remove(attunement.family)
    }

    private fun target(): Attunement? {
        val looking = (mc.hitResult as? EntityHitResult)?.entity
        if (looking != null && (looking is ArmorStand || !looking.isInvisible)) {
            attunementOf(shieldOf(looking))?.let { return it }
        }
        if (player.mainHandItem.skyblockId !in daggerIds) return null
        return activeDemon()
    }

    private fun shieldOf(entity: Entity) =
        getEntities<ArmorStand>(entity.boundingBox.inflate(0.3, 3.0, 0.3)).mapNotNull { stand ->
            val word = shieldRegex.find(stand.customName?.string?.noControlCodes ?: return@mapNotNull null)?.value
            word?.let { stand to it }
        }.minByOrNull { (stand, _) -> stand.horizontalDistanceSqr(entity) }?.second

    private fun activeDemon() =
        getEntities<LivingEntity>(8.0) { it.isPassenger && !it.isDeadOrDying && (it is WitherSkeleton || it is ZombifiedPiglin) }
            .mapNotNull { demon -> attunementOf(shieldOf(demon))?.let { demon to it } }
            .minByOrNull { (demon, _) -> player.distanceToSqr(demon) }
            ?.second

    private fun ArmorStand.horizontalDistanceSqr(other: Entity): Double {
        val dx = x - other.x
        val dz = z - other.z
        return dx * dx + dz * dz
    }

    private fun hasDagger(attunement: Attunement) =
        (0..8).any { player.inventory.getItem(it).skyblockId in attunement.daggers }

    private fun heldAttunement() = attunementOf(
        player.mainHandItem.lore
            ?.map { it.noControlCodes }
            ?.firstOrNull { it.startsWith("Attuned: ") }
            ?.removePrefix("Attuned: ")
            ?.trim()
            ?.uppercase()
    )
}
