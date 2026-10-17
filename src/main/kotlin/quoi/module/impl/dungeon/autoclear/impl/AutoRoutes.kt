package quoi.module.impl.dungeon.autoclear.impl

import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import quoi.api.autoroutes.AutoRoutesCommand
import quoi.api.autoroutes.RouteNode
import quoi.api.autoroutes.RouteRegistry
import quoi.api.autoroutes.awaits.SecretAwait
import quoi.api.autoroutes.nodes.BreakerNode
import quoi.api.autoroutes.nodes.EtherwarpNode
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.api.vec.MutableVec3
import quoi.api.world.Direction
import quoi.config.ConfigMap
import quoi.config.configMap
import quoi.config.typeName
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.*
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.useItem

object AutoRoutes : Module(
    "Auto Routes",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    val zeroTickDb by switch("Zero tick dungeon breaker")
    private val pauseOnMimic by switch("Pause on mimic", true, desc = "Pauses the route and shows a title when opening a Mimic chest.")
    private val style by selector("Style", "Box", listOf("Box", "Filled box", "Cylinder"))
    private val thickness by slider("Thickness", 4f, 1f, 8f, 0.5f)

    private val multicolour by switch("Multicolour")
    private val colour by colourPicker("Colour", Colour.CYAN).visibleIf { !multicolour }
    private val fillColour by colourPicker("Fill colour", Colour.CYAN.withAlpha(0.5f), allowAlpha = true).visibleIf { style.selected == "Filled box" && !multicolour }
    private val activeCol by colourPicker("Active colour", Colour.WHITE)

    private val colourDropdown by text("Colours").visibleIf { multicolour }
    private val colours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour).childOf(::colourDropdown)
        this.register(set)
        type to set
    }

    private val fillColourDropdown by text("Fill colours").visibleIf { style.selected == "Filled box" && multicolour }
    private val fillColours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour.withAlpha(0.5f), allowAlpha = true).json("$type fill").childOf(::fillColourDropdown)
        this.register(set)
        type to set
    }

    var executing = false
        private set
    val routing: Boolean
        get() = !editMode && (executing || pendingInteract != null) && active
    var editMode = false
    var breakerRing: BreakerNode? = null
    var currentChain: String? = null

    val routeNodes: ConfigMap<String, MutableList<RouteNode>> by configMap("route_nodes.json")
    private var roomNodes: List<RouteNode> = emptyList()
    private var pendingInteract: Direction? = null
    private var pendingEtherwarp: PendingEtherwarp? = null
    private var position: MutableVec3? = null
    private val batIds = hashSetOf<Int>()
    private var interactDelay = 0

    private var activeNode: RouteNode? = null
    private var floorSource: BreakerNode? = null
    private var forcedNode: RouteNode? = null
    private var mimicNode: RouteNode? = null

    private var lastChainName: String? = null
    private var lastChainIndex: Int = -1

    private data class PendingEtherwarp(val node: EtherwarpNode, val origin: Vec3, val floorSource: BreakerNode?, val chainName: String?, val chainIndex: Int)

    init {
        AutoRoutesCommand.register()

        on<TickEvent.Start> {
            if (interactDelay > 0) interactDelay--
            if (mimicNode != null) return@on

            pendingInteract?.let { queued ->
                var direction = queued
                val etherwarp = pendingEtherwarp
                if (etherwarp != null && player.position() != etherwarp.origin) {
                    pendingInteract = null
                    pendingEtherwarp = null
                    etherwarp.node.triggered = false
                    lastChainName = etherwarp.chainName
                    lastChainIndex = etherwarp.chainIndex
                    floorSource = etherwarp.floorSource
                    val actual = player.position().mutable()
                    position = actual
                    executing = true
                    if (!etherwarp.node.inside(actual) && continuationFromBrokenFloor(actual)?.pos != etherwarp.node.pos) {
                        floorSource = null
                        position = null
                        executing = false
                        return@on
                    }
                    handleQueue(actual)
                    direction = pendingInteract ?: return@on
                }
                player.useItem(direction)
                pendingInteract = null
                pendingEtherwarp = null
            }

            if (editMode || roomNodes.isEmpty() || ClearExecutor.active) {
                position = null
                executing = false
                floorSource = null
                return@on
            }

            val batListener = secretNode()
            if (batListener != null) {
                val listenerArea = if (batListener.inside(player.position().mutable())) null else batListener.pos.aabb(10.0)
                EntityUtils.getEntities<Bat>(radius = 10.0) {
                    it.maxHealth.equalsOneOf(100f, 200f, 400f, 800f) && it.id !in batIds && (listenerArea == null || it.boundingBox.intersects(listenerArea))
                }
                    .forEach { bat ->
                        batIds.add(bat.id)
                        onSecret()
                    }
            }

            if (position == null) {
                position = player.position().mutable()
            }

            val continuation = continuationFromBrokenFloor(player.position().mutable())
            val desynced = roomNodes.any { it.inside() && it.triggered }

            if (!desynced) {
                roomNodes.forEach {
                    if (!it.inside() && it != continuation) {
                        it.triggered = false
                    }
                }
                position = player.position().mutable()
            }

            if (continuation != null) position = player.position().mutable()
            handleQueue(position!!)
        }

        on<KeyEvent.Input> {
            if (editMode || activeNode == null) return@on
            input.shift = activeNode!!.unsneak != true
        }

        on<MouseEvent.Click> {
            if (editMode || button != 0 || !state || executing) return@on
            val pausedNode = mimicNode
            if (pausedNode != null && mc.hitResult is EntityHitResult) return@on
            val pos = if (pausedNode != null) player.position().mutable() else position ?: return@on

            val nodes = (if (pausedNode != null) listOf(pausedNode) else roomNodes).filter { it.inside(pos) }.sortedByDescending { it.priority }
            if (nodes.isEmpty()) return@on

            val next = (nodes.indexOf(forcedNode) + 1) % nodes.size
            val node = nodes[next]
            mimicNode = null
            forcedNode = node
            floorSource = null
            activeNode = node

            executing = true
            if (node.execute(pos)) {
                node.triggered = true
                node.chain?.let {
                    lastChainName = it.name
                    lastChainIndex = it.index
                }
                position = pos
                executing = false
            } else {
                node.triggered = false
                executing = true
            }
        }

        on<DungeonEvent.Secret.Interact> {
            onSecret()
            interactDelay = 2

            if (!pauseOnMimic || editMode || !blockState.`is`(Blocks.TRAPPED_CHEST)) return@on
            if (ScanUtils.getRoomFromPos(blockPos.x, blockPos.z)?.data?.type == RoomType.TRAP) return@on
            mimicNode = activeNode ?: return@on
            pendingInteract = null
            pendingEtherwarp = null
            activeNode = null
            floorSource = null
            forcedNode = null
            position = null
            executing = false
            PlayerUtils.setTitle("§cMimic!", stayAlive = 60)
        }
        on<DungeonEvent.Secret.Item> { onSecret() }
        on<DungeonEvent.Secret.Bat> {
            val node = secretNode()
            if (node != null && !node.inside(player.position().mutable()) && !node.pos.aabb(10.0).contains(packet.x, packet.y, packet.z)) return@on
            onSecret()
        }

        on<DungeonEvent.Room.Enter> {
            mimicNode = null
            batIds.clear()
            updateCache(room)
        }

        on<RenderEvent.World> {
            roomNodes.forEach { it.render(ctx, style.selected, it.colour(), it.fillColour(), activeCol, thickness) }
        }

        on<RenderEvent.Overlay> {
            val lines = mutableListOf<String>()
            if (editMode) lines.add("Edit mode")
            if (breakerRing != null) lines.add("&6DB Editor")
            if (currentChain != null) lines.add("&bChain: $currentChain")
            lines.forEachIndexed { i, string ->
                val x = (scaledWidth - string.noControlCodes.width()) / 2f
                val y = scaledHeight / 2f + 10 + i * 10
                ctx.drawText(string, x, y)
            }
        }

        on<WorldEvent.Change> {
            roomNodes = emptyList()
            activeNode = null
            floorSource = null
            executing = false
            forcedNode = null
            mimicNode = null
            position = null
            pendingInteract = null
            pendingEtherwarp = null
            batIds.clear()

            lastChainName = null
            lastChainIndex = -1
        }
    }

    private fun nextNode(playerPos: MutableVec3, secretOnly: Boolean = false): RouteNode? =
        roomNodes
            .filter { it.inside(playerPos) && !it.triggered && (!secretOnly || it.hasSecretAwait) }
            .filter { n ->
                val c = n.chain ?: return@filter true
                if (c.index == 0) return@filter true
                c.name == lastChainName && c.index == lastChainIndex + 1
            }
            .maxByOrNull { it.priority }

    fun handleQueue(playerPos: MutableVec3) {
        if (mimicNode != null) return

        val selectionPos = continuationFromBrokenFloor(playerPos)?.pos?.mutable() ?: playerPos
        val node = nextNode(selectionPos) ?: run {

            activeNode = null
            floorSource = null
            executing = false

            if (roomNodes.none { it.inside() && it.triggered }) {
                position = null
            }
            return
        }

        if (activeNode != node) {
            activeNode = node
            executing = false
        }

        if (interactDelay > 0) return

        if (!executing && !node.checkAwaits()) return

        executing = true
        if (node.execute(playerPos)) {
            node.triggered = true
            if (node != floorSource) floorSource = null
            node.chain?.let {
                lastChainName = it.name
                lastChainIndex = it.index
            }
            executing = false
        } else {
            position = null
            executing = true
        }
    }

    fun queueInteract(dir: Direction) {
        pendingInteract = dir
        pendingEtherwarp = null
    }

    fun queueEtherwarp(dir: Direction, node: EtherwarpNode, origin: Vec3) {
        queueInteract(dir)
        if (origin == player.position()) pendingEtherwarp = PendingEtherwarp(node, origin, floorSource, lastChainName, lastChainIndex)
    }

    override fun onDisable() {
        pendingInteract = null
        pendingEtherwarp = null
        activeNode = null
        floorSource = null
        forcedNode = null
        mimicNode = null
        position = null
        executing = false
    }

    fun updateCache(room: OdonRoom?) {
        activeNode = null
        floorSource = null
        forcedNode = null
        mimicNode = null
        pendingInteract = null
        pendingEtherwarp = null
        position = null
        executing = false
        interactDelay = 0
        lastChainName = null
        lastChainIndex = -1

        val nodes = room?.let { routeNodes[it.name] }.orEmpty()
        if (room != null) nodes.forEach { it.update(room) }
        roomNodes = nodes
    }

    private fun continuationFromBrokenFloor(playerPos: MutableVec3): BreakerNode? {
        val source = floorSource ?: (activeNode as? BreakerNode)?.takeIf { executing || it.triggered } ?: return null
        val inColumn = playerPos.y < source.pos.y && MutableVec3(playerPos.x, source.pos.y, playerPos.z).inside(source.aabb)
        if (!inColumn || !source.hasRemovedSupport) {
            floorSource = null
            return null
        }
        floorSource = source
        return source
    }

    private fun secretNode(): RouteNode? =
        if (editMode || mimicNode != null || ClearExecutor.active) null
        else nextNode(player.position().mutable(), secretOnly = true)
            ?: activeNode?.takeIf { !it.triggered && it.hasSecretAwait }
            ?: position?.takeIf { pendingInteract != null && activeNode is EtherwarpNode && activeNode?.triggered == true }
                ?.let { nextNode(it, secretOnly = true) }

    private fun onSecret() {
        secretNode()?.onSecret()
    }

    private val RouteNode.hasSecretAwait: Boolean
        get() = this.awaits?.any { it is SecretAwait } == true

    private fun RouteNode.colour() = if (multicolour) colours[this.typeName]?.value ?: Colour.WHITE else AutoRoutes.colour
    private fun RouteNode.fillColour() = if (multicolour) fillColours[this.typeName]?.value ?: Colour.WHITE else fillColour
}
