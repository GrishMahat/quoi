package quoi.api.skyblock.dungeon.odonscanning

import quoi.QuoiMod.mc
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.Element
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.animations.Animation
import quoi.api.colour.*
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonDoor
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomTile
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.vec.Vec2i
import quoi.module.impl.dungeon.DungeonMap
import quoi.module.impl.dungeon.autoclear.impl.InteractiveMap
import quoi.module.impl.dungeon.autoclear.pathToDoor
import quoi.module.impl.dungeon.autoclear.pathToRoom
import quoi.utils.StringUtils.width
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.watch

object MapRenderer {

    val mapSize: Vec2i get() = MapScan.mapSize
    var layoutRevision = 0
        private set


    fun refresh() {
        layoutRevision++
    }

    fun ElementScope<*>.renderMap(config: MapConfig): ElementScope<*> {
        val (scale, radius) = config

        val thickness = (12 * scale).px
        val size = 116f * scale

        return block(
            size(size.px + thickness, size.px + thickness),
            colour = DungeonMap.bgColour,
            (radius * 1.5f).radius()
        ) {
            if (DungeonMap.border) outline(DungeonMap.borderColour, thickness = DungeonMap.borderThickness.px)

            val content = group(constrain(Centre, Centre, 0.px, 0.px))
            val doors = content.group(size(Copying, Copying))
            val rooms = content.group(size(Copying, Copying))
            val markers = PlayerMarkers(config)
            content.apply { markers.scope {} }
            val roomViews = mutableMapOf<OdonRoom, RoomView>()
            val doorViews = mutableMapOf<OdonDoor, Element>()
            var previousScale = Float.NaN

            watch({ layoutRevision }, immediate = true) {
                val baseW = (mapSize.x * 16 + (mapSize.x - 1) * 4).toFloat().coerceAtLeast(1f)
                val baseH = (mapSize.z * 16 + (mapSize.z - 1) * 4).toFloat().coerceAtLeast(1f)
                val newScale = size / maxOf(baseW, baseH)
                val resized = previousScale != newScale
                previousScale = newScale
                val new = config.copy(
                    scale = newScale,
                    radius = config.radius * (newScale / scale),
                )
                content.element.constraints.width = (baseW * newScale).px
                content.element.constraints.height = (baseH * newScale).px
                markers.config = new

                doorViews.entries.removeIf { (door, element) ->
                    (resized || door !in ScanUtils.scannedDoors).also { remove ->
                        if (remove) doors.element.removeElement(element)
                    }
                }
                for (door in ScanUtils.scannedDoors) {
                    doorViews.getOrPut(door) { doors.renderDoor(new, door) }
                }
                roomViews.entries.removeIf { (room, view) ->
                    (room !in ScanUtils.scannedRooms).also { remove ->
                        if (remove) rooms.element.removeElement(view.element)
                    }
                }
                for (room in ScanUtils.scannedRooms) {
                    val view = roomViews.getOrPut(room) { RoomView(rooms.group(size(Copying, Copying)).element) }
                    val label = if (room.identified) room.name else "?"
                    if (!resized && view.tiles == room.tiles && view.label == label) continue
                    view.element.removeAll()
                    ElementScope(view.element).apply {
                        renderTiles(new, room)
                        renderName(new, room)
                    }
                    view.tiles = room.tiles.toSet()
                    view.label = label
                }
                if (ui.initialised) {
                    element.size()
                    element.positionChildren()
                    element.clip()
                }
                ui.recalculateMouse = true
            }
        }
    }

    private class RoomView(val element: Element) {
        var tiles: Set<RoomTile> = emptySet()
        var label: String? = null
    }

    private fun ElementScope<*>.renderDoor(config: MapConfig, door: OdonDoor): Element {
        val pos = door.placement
        val size = door.size
        val col = Colour.Animated(
            from = colour { door.colour.rgb },
            to = colour { door.colour.rgb.multiply(1.15f) }
        )
        val outlineCol = Colour.Animated(
            from = Colour.TRANSPARENT,
            to = Colour.WHITE.withAlpha(180)
        )
        return block(
            constrain(
                (pos.x * config.scale).px,
                (pos.z * config.scale).px,
                (size.x * config.scale).px,
                (size.z * config.scale).px
            ),
            colour = col
        ) {
            outline(outlineCol, thickness = 2.px)
            if (config.autoClear) {
                onClick {
                    if (Dungeon.isDead) return@onClick false
                    pathToDoor(door, InteractiveMap.faceDoorOnArrival)
                    true
                }
                onMouseEnterExit {
                    col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                    outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                }
            }
        }.element
    }

    private fun ElementScope<*>.renderTiles(config: MapConfig, room: OdonRoom) {
        val components = room.tiles
        if (components.isEmpty()) return

        val (scale, radius, _, _, _, _, _, autoClear) = config

        val c = {
            val base = room.colour
            if (autoClear && Dungeon.currentRoom == room) base.mix(InteractiveMap.roomInCol.withAlpha(255), InteractiveMap.roomInCol.alpha).rgb else base.rgb
        }

        val outlineCol = Colour.Animated(
            from = Colour.TRANSPARENT,
            to = Colour.WHITE.withAlpha(180)
        )

        val byPos = components.associateBy { it.placement }
        fun has(p: Vec2i, dx: Int, dz: Int) = byPos.containsKey(p.add(dx, dz))

        val lCorners = components.filter {
            val p = it.placement
            val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))
            val long = (n && s) || (w && e)
            val twoByTwo =
                (n && e && has(p, 20, -20)) ||
                        (n && w && has(p, -20, -20)) ||
                        (s && e && has(p, 20, 20)) ||
                        (s && w && has(p, -20, 20))

            // l corner: 2 connections, not a line, not a part of 2x2
            listOf(n, s, w, e).count { c -> c } == 2 && !long && !twoByTwo
        }.map { it.placement }.toSet()

        components.forEach { comp ->

            val col = Colour.Animated(
                from = colour { c() },
                to = colour { c().multiply(1.15f) }
            )

            val p = comp.placement
            val isL = p in lCorners

            val (n, s, w, e) = listOf(has(p, 0, -20), has(p, 0, 20), has(p, -20, 0), has(p, 20, 0))

            // all blocks start at 16x 16
            // if L corner don't touch
            // if next to L corner 4
            // if 2x2 or long 2
            fun gap(exists: Boolean, dx: Int, dz: Int) = when {
                !exists || isL -> 0f
                p.add(dx, dz) in lCorners -> 4f
                else -> 2f
            }

            val ng = gap(n, 0, -20)
            val sg = gap(s, 0, 20)
            val wg = gap(w, -20, 0)
            val eg = gap(e, 20, 0)

            val width = (16f + wg + eg) * scale
            val height = (16f + ng + sg) * scale

            block(
                constrain(((p.x - wg) * scale).px, ((p.z - ng) * scale).px, width.px, height.px),
                colour = col,
                radius = radius(
                    tl = if (n || w) 0 else radius,
                    bl = if (s || w) 0 else radius,
                    br = if (s || e) 0 else radius,
                    tr = if (n || e) 0 else radius
                )
            ) {
                if (autoClear) outline(outlineCol, 2.px)

                val mx = wg * scale + 0.5f
                val my = ng * scale + 0.5f
                val mw = width - eg * scale - 0.5f
                val mh = height - sg * scale - 0.5f

                fun mask(vert: Boolean, start: Float, end: Float, pos: Float) {
                    val constraints =
                        if (vert) constrain(pos.px, start.px, 4.px, (end - start).px)
                        else constrain(start.px, pos.px, (end - start).px, 4.px)
                    block(constraints, colour = col)
                }

                if (n) mask(
                    false,
                    start = if (has(p, -20, -20)) -1f else mx,
                    end = if (has(p, 20, -20)) width + 1f else mw,
                    pos = -2f
                )

                if (s) mask(
                    false,
                    start = if (has(p, -20, 20)) -1f else mx,
                    end = if (has(p, 20, 20)) width + 1f else mw,
                    pos = height - 2f
                )

                if (w) mask(
                    true,
                    start = if (has(p, -20, -20)) -1f else my,
                    end = if (has(p, -20, 20)) height + 1f else mh,
                    pos = -2f
                )

                if (e) mask(
                    true,
                    start = if (has(p, 20, -20)) -1f else my,
                    end = if (has(p, 20, 20)) height + 1f else mh,
                    pos = width - 2f
                )

                if (autoClear) {
                    onMouseEnterExit {
                        col.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                        outlineCol.animate(0.15.seconds, style = Animation.Style.EaseOutQuint)
                    }
                    onClick {
                        if (Dungeon.isDead) return@onClick false
                        pathToRoom(room, comp, type = 0)
                        true
                    }
                    onClick(button = 1) {
                        if (Dungeon.isDead) return@onClick false
                        pathToRoom(room, comp, type = 2)
                        true
                    }
                }
            }
        }
    }

    private fun ElementScope<*>.renderName(config: MapConfig, room: OdonRoom) {
        val (scale, _, font, fontScale, shadow) = config
        val lines = (if (room.identified) room.name else "?").split(" ")
        val textSize = 18f * fontScale * (scale / 5f)

        lines.forEachIndexed { i, line ->
            val tw = if (font.name == "Minecraft") line.width(textSize / mc.font.lineHeight) else NVGRenderer.textWidth(line, textSize, font)
            val x = room.textPlacement.x * scale + 8f * scale - tw / 2f
            val y = room.textPlacement.z * scale + 8f * scale - lines.size * textSize / 2f + i * textSize

            object : Text(line, font, colour { room.textColour.rgb }, at(x.px, y.px), textSize.px) {
                override fun draw() {
                    if (room.data.type !in unlabelledTypes) super.draw()
                }
            }.scope {}.shadow = shadow
        }
    }

    private val unlabelledTypes = setOf(RoomType.ENTRANCE, RoomType.FAIRY, RoomType.BLOOD)

    data class MapConfig(
        val scale: Float = 3f,
        val radius: Float = DungeonMap.roomRadius,
        val font: Font = DungeonMap.font.selected.get(),
        val fontScale: Float = DungeonMap.fontScale,
        val shadow: Boolean = DungeonMap.shadow,
        val icons: Boolean = true,
        val icon: IconConfig = IconConfig(),
        val autoClear: Boolean = false,
    )

    data class IconConfig(
        val scale: Float = DungeonMap.iconScale,
        val heads: Boolean = DungeonMap.showHeads,
        val ownHead: Boolean = DungeonMap.showOwnHead,
        val border: Boolean = DungeonMap.iconBorder,
        val borderColour: Colour = DungeonMap.iconBorderColour,
        val classColour: Boolean = DungeonMap.classColour,
        val thickness: Int = DungeonMap.iconBorderThickness,
        val name: Boolean = DungeonMap.showNames,
        val whenLeap: Boolean = DungeonMap.whenLeap,
        val nameScale: Float = DungeonMap.nameScale,
    )
}
