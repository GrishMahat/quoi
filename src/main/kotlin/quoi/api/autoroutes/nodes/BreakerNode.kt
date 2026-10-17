package quoi.api.autoroutes.nodes

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.core.BlockPos
import quoi.api.autoroutes.RouteNode
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.vec.MutableVec3
import quoi.config.TypeName
import quoi.module.impl.dungeon.autoclear.impl.AutoRoutes
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils.state
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.item.ItemUtils.getBreakerCharges
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.skyblock.player.interact.AuraManager

@TypeName("dungeon_breaker")
class BreakerNode : RouteNode() {
    var blocks = emptyList<BlockPos>()

    @Transient
    private var active = false

    @Transient
    private var retryAt = 0

    @Transient
    private var realBlocks = emptyList<BlockPos>()

    override val colour: Colour
        get() = Colour.ORANGE

    override val priority: Int
        get() = 150

    val hasRemovedSupport: Boolean
        get() {
            val support = BlockPos.containing(pos.x, pos.y - 0.001, pos.z)
            return support in realBlocks && level.isLoaded(support) && support.state.isAir
        }

    override fun update(room: OdonRoom) {
        super.update(room)
        retryAt = 0
        realBlocks = blocks.map { room.getRealCoords(it) }
    }

    override fun checkAwaits(): Boolean {
        if (!inside()) return false
        return super.checkAwaits()
    }

    override fun execute(pos: MutableVec3): Boolean {
        if (active) return false
        if (blocks.isEmpty()) return true
        val toBreak = realBlocks.filter { pos -> level.isLoaded(pos) && !pos.state.isAir }

        if (toBreak.isEmpty()) {
            retryAt = 0
            return true
        }

        val breakerSlot = PlayerUtils.breakerSlot ?: run {
            val hasBreaker = (0..8).any { player.inventory.getItem(it).skyblockId == "DUNGEONBREAKER" }
            return !hasBreaker
        }

        if (player.tickCount < retryAt) return false

        if (player.inventory.selectedSlot != breakerSlot) {
            SwapManager.swapToSlot(breakerSlot)
            return false
        }

        val initialCharges = getBreakerCharges(player.inventory.getItem(breakerSlot))
        if (initialCharges == 0) return false

        val batch = toBreak.take(initialCharges)

        if (AutoRoutes.zeroTickDb) {
            batch.forEach { AuraManager.breakBlock(it, immediate = true) }
            retryAt = player.tickCount + 10
        } else {
            active = true
            batch.forEachIndexed { i, pos ->
                scheduleTask(i + 1) { AuraManager.breakBlock(pos, immediate = true) }
            }
            scheduleTask(realBlocks.size) { active = false }
        }
        return false
    }

    override fun render(ctx: LevelRenderContext, style: String, colour: Colour, fillColour: Colour, activeColour: Colour, thickness: Float) {
        super.render(ctx, style, colour, fillColour, activeColour, thickness)
        if (!inside() && AutoRoutes.breakerRing == null) return
        for (pos in realBlocks) {
            val aabb = pos.aabb
            if (pos.state.isAir) {
                ctx.drawWireFrameBox(aabb, Colour.RED.withAlpha(125), depth = true)
            } else {
                ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(125), depth = true)
            }
        }
    }
}