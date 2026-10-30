package quoi.module.impl.misc.slayers.vampire

import com.google.gson.JsonParser
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.slayers.Throttle
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.render.drawLine
import quoi.utils.render.drawText
import quoi.utils.skyblock.item.ItemUtils.texture
import java.util.Base64

/**
 * modified SkyHanni (LGPL-2.1) (c) hannibal002
 * original: https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/slayer/VampireSlayerFeatures.kt
 * skull textures from SkyHanni-REPO (constants/Skulls.json)
 */
@Suppress("unused_expression")
object BloodItems : ToggleableGroup(
    VampireSlayer,
    "Blood items",
    desc = "Highlights Blood Ichor and Killer Springs in Stillgore Château, with tracers, labels, boss lines and sound dedupe.",
) {
    private const val ICHOR_TEXTURE = "c0340923a6de4825a176813d133503eff186db0896e32b6704928c2a2bf68422"
    private const val SPRING_TEXTURE = "77f7a7bc8ac86f23ca7bf98afeb76960227e1832fe209a3026f6ceb8bde74f54"
    private val textures = setOf(ICHOR_TEXTURE, SPRING_TEXTURE)

    private val ichor by switch("Highlight ichor", desc = "Highlights Blood Ichor.")
    private val spring by switch("Highlight spring", desc = "Highlights Killer Springs.")
    private val showLabels by switch("Labels", desc = "Draws 'Ichor' or 'Spring' above each item.")
    private val showLines by switch("Boss lines", desc = "Lines from your Bloodfiend to the items hit by its ENCHANT ability.")
    private val soundFix by switch("Spring sound dedupe", desc = "Cancels duplicate Killer Spring 'wither spawn' sounds sent in the same tick.")

    private val highlight = highlight(colour = Colour.RED, fillColour = Colour.RED.withAlpha(100))
    private val tracer = tracer(colour = Colour.RED, distance = null)

    private enum class Kind { ICHOR, SPRING }

    private val stands = mutableMapOf<ArmorStand, Kind>()
    private val textureCache = mutableMapOf<Int, String?>()

    // ENCHANT-particle pairing, kept between particle bursts like SkyHanni does
    private val paired = mutableMapOf<ArmorStand, Player>()
    private val scan = Throttle(250)
    private val pair = Throttle(200)
    private var lastWitherSound = 0L

    val ArmorStand.headTexture: String?
        get() = getItemBySlot(EquipmentSlot.HEAD).texture?.let { encoded ->
            runCatching {
                JsonParser.parseString(Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8))
                    .asJsonObject
                    .get("textures").asJsonObject
                    .get("SKIN").asJsonObject
                    .get("url").asString
                    .substringAfter("/texture/")
            }.getOrNull()
        }

    private fun ArmorStand.cachedTexture(): String? {
        if (textureCache.containsKey(id)) return textureCache[id]
        return headTexture.also { textureCache[id] = it }
    }

    init {
        on<TickEvent.End> {
            paired.keys.removeAll { it.isRemoved }
            paired.values.removeAll { it.isRemoved }

            if (!ichor && !spring) return@on
            val now = System.currentTimeMillis()
            if (!scan.ready(now)) return@on
            scan.mark(now)

            stands.clear()
            getEntities<ArmorStand>(player.position(), 15.0).forEach { stand ->
                when (stand.cachedTexture()) {
                    ICHOR_TEXTURE -> stands[stand] = Kind.ICHOR
                    SPRING_TEXTURE -> stands[stand] = Kind.SPRING
                }
            }
        }

        // head items arrive after the stand itself, so drop the cached (possibly empty) texture
        on<EntityEvent.ArmorStandHeadEquipmentUpdate> { textureCache.remove(entity.id) }

        on<PacketEvent.Received, ClientboundLevelParticlesPacket> {
            if (!showLines || packet.particle.type != ParticleTypes.ENCHANT) return@on

            val now = System.currentTimeMillis()
            if (!pair.ready(now)) return@on
            pair.mark(now)

            // own quest only: never pairs against other players' bosses
            val boss = VampireSlayer.boss ?: return@on
            val pos = Vec3(packet.x, packet.y, packet.z)
            if (boss.position().distanceTo(pos) > 3.0) return@on

            getEntities<ArmorStand>(pos, 3.0) { it.isAlive }.forEach { stand ->
                if (stand.cachedTexture() in textures) paired[stand] = boss
            }
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!soundFix || packet.sound.value() != SoundEvents.WITHER_SPAWN) return@on
            val now = System.currentTimeMillis()
            if (now - lastWitherSound <= 50) cancel()
            else lastWitherSound = now
        }

        on<RenderEvent.World> {
            stands.forEach { (stand, kind) ->
                if (!stand.isAlive) return@forEach
                val active = if (kind == Kind.ICHOR) ichor else spring
                if (!active) return@forEach

                val colour = if (kind == Kind.ICHOR) Colour.RED else Colour.YELLOW
                val box = stand.interpolatedBox
                highlight.draw(ctx, box, overrideColour = colour, overrideFillColour = colour.withAlpha(100))
                tracer.draw(ctx, box.center, colour = colour)
                if (showLabels) ctx.drawText(
                    Component.literal(if (kind == Kind.ICHOR) "§4Ichor" else "§eSpring"),
                    Vec3(box.minX + 0.5, box.maxY + 0.5, box.minZ + 0.5),
                    scale = 1.5f,
                )
            }
            if (showLines) paired.forEach { (stand, boss) ->
                if (!stand.isAlive || !boss.isAlive) return@forEach
                val colour = when (stand.cachedTexture()) { ICHOR_TEXTURE -> Colour.RED; else -> Colour.YELLOW }
                ctx.drawLine(
                    listOf(boss.position().add(0.0, 1.6, 0.0), stand.position().add(0.0, 1.5, 0.0)),
                    colour,
                )
            }
        }

        on<WorldEvent.Change> { reset() }
    }

    fun isKillerSpringStand(stand: ArmorStand): Boolean =
        // while disabled no events maintain the cache, so decode fresh instead of
        // trusting a stale entry (equipment change / id reuse)
        (if (enabled) stand.cachedTexture() else stand.headTexture) == SPRING_TEXTURE

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun reset() {
        stands.clear()
        textureCache.clear()
        paired.clear()
        scan.reset()
        pair.reset()
        lastWitherSound = 0L
    }
}
