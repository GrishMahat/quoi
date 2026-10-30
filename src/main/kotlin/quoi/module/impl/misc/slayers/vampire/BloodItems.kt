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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.PacketEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.on
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
    const val SPRING_TEXTURE = "77f7a7bc8ac86f23ca7bf98afeb76960227e1832fe209a3026f6ceb8bde74f54"
    private val textures = setOf(ICHOR_TEXTURE, SPRING_TEXTURE)

    private val ichor by switch("Highlight ichor", desc = "Highlights Blood Ichor.")
    private val spring by switch("Highlight spring", desc = "Highlights Killer Springs.")
    private val showLabels by switch("Labels", desc = "Draws 'Ichor' or 'Spring' above each item.")
    private val showLines by switch("Boss lines", desc = "Lines from your Bloodfiend to the items hit by its ENCHANT ability.")
    private val soundFix by switch("Spring sound dedupe", desc = "Cancels duplicate Killer Spring 'wither spawn' sounds sent in the same tick.")

    private val highlight = highlight(colour = Colour.RED, fillColour = Colour.RED.withAlpha(100))
    private val tracer = tracer(colour = Colour.RED, distance = null)

    private enum class Kind { ICHOR, SPRING }

    private val stands = mutableMapOf<AABB, Kind>()

    // ENCHANT-particle pairing, kept between particle bursts like SkyHanni does
    private val paired = mutableMapOf<ArmorStand, Player>()
    private var nextScan = 0L
    private var nextPair = 0L
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

    init {
        on<TickEvent.End> {
            paired.keys.removeAll { it.isRemoved }
            paired.values.removeAll { it.isRemoved }

            if (!ichor && !spring) return@on
            val now = System.currentTimeMillis()
            if (now < nextScan) return@on
            nextScan = now + 250

            stands.clear()
            getEntities<ArmorStand>(player.position(), 15.0).forEach { stand ->
                when (stand.headTexture) {
                    ICHOR_TEXTURE -> stands[stand.interpolatedBox] = Kind.ICHOR
                    SPRING_TEXTURE -> stands[stand.interpolatedBox] = Kind.SPRING
                    else -> {}
                }
            }
        }

        on<PacketEvent.Received, ClientboundLevelParticlesPacket> {
            if (!showLines) return@on
            if (packet.particle.type != ParticleTypes.ENCHANT) return@on
            if (stands.isEmpty()) return@on

            val now = System.currentTimeMillis()
            if (now < nextPair) return@on
            nextPair = now + 200

            // own quest only: never pairs against other players' bosses
            val boss = BloodfiendESP.pinBoss() ?: return@on
            val pos = Vec3(packet.x, packet.y, packet.z)
            if (boss.position().distanceTo(pos) > 3.0) return@on

            getEntities<ArmorStand>(pos, 3.0) { it.isAlive }.forEach { stand ->
                if (stand.headTexture in textures) paired[stand] = boss
            }
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!soundFix || packet.sound.value() != SoundEvents.WITHER_SPAWN) return@on
            val now = System.currentTimeMillis()
            if (now - lastWitherSound <= 50) cancel()
            else lastWitherSound = now
        }

        on<RenderEvent.World> {
            stands.forEach { (box, kind) ->
                val active = if (kind == Kind.ICHOR) ichor else spring
                if (!active) return@forEach

                val colour = if (kind == Kind.ICHOR) Colour.RED else Colour.YELLOW
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
                val colour = when (stand.headTexture) { ICHOR_TEXTURE -> Colour.RED; else -> Colour.YELLOW }
                ctx.drawLine(
                    listOf(boss.position().add(0.0, 1.6, 0.0), stand.position().add(0.0, 1.5, 0.0)),
                    colour,
                )
            }
        }

        on<WorldEvent.Change> {
            stands.clear()
            paired.clear()
        }
    }

    fun isKillerSpringStand(stand: ArmorStand): Boolean =
        stand.headTexture == SPRING_TEXTURE

    override fun onDisable() {
        stands.clear()
        paired.clear()
        nextScan = 0L
        nextPair = 0L
        lastWitherSound = 0L
        super.onDisable()
    }
}
