package quoi.module.impl.dungeon.autoclear.impl

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.impl.Text.Companion.maxWidth
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.api.input.Keybinds
import quoi.api.skyblock.SkyblockPlayer
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer
import quoi.api.skyblock.dungeon.odonscanning.MapRenderer.renderMap
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.module.Module
import quoi.module.impl.dungeon.autoclear.getLockedDoor
import quoi.module.impl.dungeon.autoclear.pathToDoor
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.impl.KeybindComponent
import quoi.utils.ChatUtils.modMessage
import quoi.utils.commas
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.screens.UIScreen.Companion.open

/**
 * TODO:
 *  room queuing while auto routing
 */
object InteractiveMap : Module(
    "Interactive Map",
    desc = "Automatically teleports to a specified target.",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    private val keepChunks by switch("Keep chunks loaded", true, desc = "Keeps the chunks loaded. Good for long distances.")
    private val closeOn by segmented("Close on", "Release", listOf("Release", "Repress"))
    val faceDoorOnArrival by switch("Face door on arrival", desc = "Faces the targeted door after arriving.")

    private val openKey: KeybindComponent = register(keybind("Open key"))
        .onPress {
            if (!enabled || !Dungeon.inClear || Dungeon.isDead) return@onPress
            if (mapOpen && closeOn.index == 1) {
                mc.setScreen(null)
            } else if (mc.screen == null) {
                open(map(), background = false)
            }
        }
        .onRelease {
            if (!enabled || closeOn.index != 0 || !Dungeon.inClear || Dungeon.isDead) return@onRelease
            if (mapOpen) mc.setScreen(null)
        }

    private val lockedKey: KeybindComponent = register(keybind("Locked door key", desc = "Teleports to the closest locked door"))
        .onPress {
            if (!enabled || !Dungeon.inClear || Dungeon.isDead) return@onPress
            val door = getLockedDoor() ?: return@onPress modMessage("No locked doors found.")
            pathToDoor(door, faceDoorOnArrival)
        }

    private val visuals by text("Visuals")
    private val shadow by switch("Shadow", true).childOf(::visuals).asParent()
    private val font by segmented("Font", TextHud.HudFont.Minecraft).childOf(::visuals)
    private val fontScale by slider("Font scale", 1f, 0.5f, 3f, 0.1f).childOf(::visuals)
    private val scale by slider("Map scale", 5f, 1f, 10f, 0.5f).childOf(::visuals)
    private val roomRadius by slider("Room radius", 5f, 1f, 10f, 1f).childOf(::visuals)
    val roomInCol by colourPicker("Highlight colour", Colour.GREY.withAlpha(0.5f), allowAlpha = true, desc = "Room the player currently in colour.").childOf(::visuals)

//    private val icons by switch("Icons")
//    private val showHeads by switch("Show player heads").childOf(::icons)
//    private val showOwnHead by switch("Show own head").childOf(::showHeads)
//    private val iconScale by slider("Icon scale", 1f, 0.1f, 3.0f, 0.1f).childOf(::showHeads)
//    private val iconBorder by switch("Border").json("Icon border").childOf(::showHeads)
//    private val classColour by switch("Class border colour").childOf(::iconBorder)
//    private val iconBorderColour by colourPicker("Border colour", Colour.BLACK).json("Icon border colour").childOf(::iconBorder) { !classColour }
//    private val iconBorderThickness by slider("Border thickness", 2, 1, 10, unit = "px").json("Icon border thickness").childOf(::iconBorder)
//
//    private val showNames by switch("Show names").childOf(::icons)
//    private val nameScale by slider("Name scale", 0.8f, 0.1f, 3.0f, 0.1f).childOf(::showNames)

    val mapOpen: Boolean
        get() = mc.screen?.title?.string == "quoi clear map"

    init {
        on<PacketEvent.Received, ClientboundForgetLevelChunkPacket> {
            if (keepChunks) cancel()
        }
    }

    private fun map() = aboba("quoi clear map") {
//        val iconCfg = MapRenderer.IconConfig(
//            scale = iconScale,
//            heads = showHeads,
//            ownHead = showOwnHead,
//            border = iconBorder,
//            borderColour = iconBorderColour,
//            classColour = classColour,
//            thickness = iconBorderThickness,
//            name = showNames,
//            whenLeap = false,
//            nameScale = nameScale
//        )

        val cfg = MapRenderer.MapConfig(
            scale = scale,
            radius = roomRadius,
            font = font.selected.get(),
            fontScale = fontScale,
            shadow = shadow,
            autoClear = true,
//            icons = icons,
//            icon = iconCfg
        )

        val infoScale = fontScale.coerceAtLeast(1f)

        column(constrain(w = Copying - 32.px), gap = 12.px) {
            block(bounds(x = Centre, padding = 10.px), Colour.BLACK.withAlpha(0.8f), 6.radius()) {
                textSupplied(
                    supplier = { "${SkyblockPlayer.mana.commas()} / ${SkyblockPlayer.maxMana.commas()} Mana" },
                    font = cfg.font,
                    colour = Colour.MINECRAFT_AQUA,
                    pos = at(10.px, 10.px),
                    size = (28f * infoScale).px
                ).shadow = shadow
            }

            renderMap(config = cfg).element.constraints.x = Centre

            block(
                constrain(x = Centre, w = (960f * infoScale).px.coerceAtMost(Copying), h = Bounding + 12.px),
                Colour.BLACK.withAlpha(0.8f),
                6.radius()
            ) {
                column(constrain(16.px, 12.px, Copying - 32.px), gap = 8.px) {
                    text(
                        string = "§bLeft click§f: Room / door   §7|   §bRight click§f: Route start",
                        font = cfg.font,
                        pos = at(x = Centre),
                        size = (24f * infoScale).px
                    ) {
                        maxWidth(Copying)
                    }.shadow = shadow

                    textSupplied(
                        supplier = {
                            val doorHint = if (lockedKey.value.key == Keybinds.KEY_NONE) "§fLocked door: §7Not bound"
                            else "§b${lockedKey.getKeyName()}§f: Closest locked door"
                            val closeHint = if (openKey.value.key == Keybinds.KEY_NONE) "Esc"
                            else "${if (closeOn.index == 0) "Release " else ""}${openKey.getKeyName()} / Esc"
                            "$doorHint   §7|   §b$closeHint§f: Close map"
                        },
                        font = cfg.font,
                        pos = at(x = Centre),
                        size = (24f * infoScale).px
                    ).also {
                        it.maxWidth(Copying)
                        it.shadow = shadow
                    }
                }
            }
        }
    }
}
