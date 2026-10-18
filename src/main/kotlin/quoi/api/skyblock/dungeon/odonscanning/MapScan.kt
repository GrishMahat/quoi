package quoi.api.skyblock.dungeon.odonscanning

import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import quoi.QuoiMod.mc
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.vec.Vec2i
import kotlin.jvm.optionals.getOrNull

/**
 * modified Odin (BSD 3-Clause)
 * copyright (c) 2025 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/map/MapScan.kt
 */
object MapScan {
    var startCoords: Vec2i? = null
        private set
    var roomSize = 16
        private set
    var mapSize = Vec2i(6, 6)
        private set

    private val gap get() = roomSize + 4
    private val directions = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private var layoutDetected = false

    internal fun worldPosition(x: Double, z: Double): Pair<Float, Float> =
        ((x - ScanUtils.START) * 20 / 32 + 8).toFloat() to ((z - ScanUtils.START) * 20 / 32 + 8).toFloat()

    internal fun mapPosition(position: Vec2i): Pair<Float, Float>? {
        val start = startCoords ?: return null
        val x = (position.x + 128) / 2f - start.x - roomSize / 2f
        val z = (position.z + 128) / 2f - start.z - roomSize / 2f
        return (x * 20 / gap + 8) to (z * 20 / gap + 8)
    }

    fun initFloor(floor: Int? = Dungeon.floor?.floorNumber) {
        if (startCoords != null) return
        floor ?: return
        roomSize = if (floor <= 3) 18 else 16
        startCoords = Vec2i(
            when (floor) {
                0, 1 -> 22
                2, 3 -> 11
                else -> 5
            },
            when (floor) {
                0 -> 22
                1, 2, 3 -> 11
                4 -> 16
                else -> 5
            }
        )
        val size = when (floor) {
            0 -> Vec2i(4, 4)
            1 -> Vec2i(4, 5)
            2, 3 -> Vec2i(5, 5)
            4 -> Vec2i(6, 5)
            else -> Vec2i(6, 6)
        }
        mapSize = size
        MapRenderer.refresh()
    }

    fun update(packet: ClientboundMapItemDataPacket) {
        val hotbarMap = mc.player?.inventory?.getItem(8)?.get(DataComponents.MAP_ID)
        if (hotbarMap != null && packet.mapId != hotbarMap) return
        if (hotbarMap == null && packet.mapId.id >= 1000) return
        packet.decorations.getOrNull()?.let { decorations ->
            val players = Dungeon.dungeonTeammatesNoSelf.filter { !it.isDead }.iterator()
            for (decoration in decorations) {
                if (decoration.type.value() == MapDecorationTypes.FRAME.value()) continue
                if (!players.hasNext()) break
                players.next().apply {
                    mapPos = Vec2i(decoration.x.toInt(), decoration.y.toInt())
                    yaw = decoration.rot() * 360 / 16f
                }
            }
        }
        val colours = mc.level?.getMapData(packet.mapId)?.colors ?: return
        updateColours(colours, Dungeon.floor?.floorNumber)
    }

    internal fun updateColours(colours: ByteArray, floor: Int?) {
        if (colours.size != 128 * 128 || colours[0].toInt() != 0) return
        if (!layoutDetected && !initLayout(colours, floor)) {
            if (startCoords == null) initFloor(floor)
            if (startCoords == null) return
        }
        val start = startCoords ?: return
        fun pixel(x: Int, z: Int): Int = if (x in 0..127 && z in 0..127) colours[z * 128 + x].toInt() and 255 else 0

        val types = arrayOfNulls<RoomType>(36)
        val roomColours = IntArray(36)
        val centreColours = IntArray(36)
        val half = roomSize / 2

        for (z in 0 until mapSize.z) for (x in 0 until mapSize.x) {
            val index = z * 6 + x
            val px = start.x + x * gap
            val pz = start.z + z * gap
            roomColours[index] = pixel(px, pz)
            types[index] = RoomType.fromMapColour(roomColours[index])
            centreColours[index] = pixel(px + half, pz + half)

            if (x + 1 < mapSize.x) {
                val door = pixel(px + roomSize + 2, pz + half)
                if (door != 0 && pixel(px + roomSize + 2, pz + half - 4) == 0)
                    ScanUtils.addMapDoor(z * 22 + x * 2 + 1, door)
            }
            if (z + 1 < mapSize.z) {
                val door = pixel(px + half, pz + roomSize + 2)
                if (door != 0 && pixel(px + half - 4, pz + roomSize + 2) == 0)
                    ScanUtils.addMapDoor(z * 22 + x * 2 + 11, door)
            }
        }

        val visited = BooleanArray(36)
        for (index in 0 until 36) {
            val type = types[index] ?: continue
            if (visited[index]) continue
            val segments = mutableListOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(index)
            visited[index] = true
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                segments.add(current)
                val x = current % 6
                val z = current / 6
                for ((dx, dz) in directions) {
                    val nx = x + dx
                    val nz = z + dz
                    if (nx !in 0 until mapSize.x || nz !in 0 until mapSize.z) continue
                    val next = nz * 6 + nx
                    if (visited[next] || types[next] != type) continue
                    val bridgeX = start.x + minOf(x, nx) * gap + if (dx == 0) 0 else roomSize + 2
                    val bridgeZ = start.z + minOf(z, nz) * gap + if (dz == 0) 0 else roomSize + 2
                    if (pixel(bridgeX, bridgeZ) == 0) continue
                    visited[next] = true
                    queue.add(next)
                }
            }

            val room = ScanUtils.addMapRoom(segments.map { it / 6 * 22 + it % 6 * 2 }, type)
            val marked = segments.minBy { segment ->
                when (centreColours[segment]) {
                    30 if (type != RoomType.ENTRANCE) -> 0
                    18 if (type == RoomType.PUZZLE) -> 1
                    34 -> 2
                    else -> 3
                }
            }
            room.updateState(centreColours[marked], roomColours[marked])
        }

        for (z in 0..10) for (x in 0..10) {
            if (x % 2 == z % 2) continue
            val left = ScanUtils.grid[z * 11 + x - if (x % 2 != 0) 1 else 11] as? OdonRoom ?: continue
            val right = ScanUtils.grid[z * 11 + x + if (x % 2 != 0) 1 else 11] as? OdonRoom ?: continue
            if (left === right) ScanUtils.grid[z * 11 + x] = left
        }
        for (z in 1..9 step 2) for (x in 1..9 step 2) {
            val room = ScanUtils.grid[(z - 1) * 11 + x - 1] as? OdonRoom ?: continue
            if (ScanUtils.grid[(z - 1) * 11 + x + 1] === room &&
                ScanUtils.grid[(z + 1) * 11 + x - 1] === room &&
                ScanUtils.grid[(z + 1) * 11 + x + 1] === room
            ) ScanUtils.grid[z * 11 + x] = room
        }
    }

    private fun initLayout(colours: ByteArray, floor: Int?): Boolean {
        for (z in 0..127) {
            var x = 0
            while (x < 128) {
                if (colours[z * 128 + x].toInt() != 30) {
                    x++
                    continue
                }
                val start = x
                while (x < 128 && colours[z * 128 + x].toInt() == 30) x++
                val length = x - start
                if (length != 16 && length != 18) continue
                roomSize = length
                val sx = (start % gap).let { if (it == 0) 22 else it }
                val sz = (z % gap).let { if (it == 0) 22 else it }
                startCoords = Vec2i(sx, sz)
                layoutDetected = true
                mapSize = when (floor) {
                    0 -> Vec2i(4, 4)
                    1 -> Vec2i(4, 5)
                    2, 3 -> Vec2i(5, 5)
                    else -> Vec2i(if (sx == 5) 6 else 5, if (sz == 5) 6 else 5)
                }
                MapRenderer.refresh()
                return true
            }
        }
        return false
    }

    fun reset() {
        startCoords = null
        roomSize = 16
        mapSize = Vec2i(6, 6)
        layoutDetected = false
    }
}
