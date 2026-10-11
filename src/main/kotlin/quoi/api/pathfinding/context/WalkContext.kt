package quoi.api.pathfinding.context

import net.minecraft.core.BlockPos
import quoi.api.pathfinding.PathNode
import java.util.concurrent.ConcurrentHashMap

class WalkContext(
    goal: BlockPos,
    timeout: Long,
    val hWeight: Double
) : PathContext<PathNode>(goal, timeout) {
    val penaltyCache = ConcurrentHashMap<Long, Double>()
}
