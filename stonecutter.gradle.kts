plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1.x" /* [SC] DO NOT EDIT */

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes collected artifacts and every version's build output."
    delete(layout.buildDirectory)
    dependsOn(stonecutter.tasks.named("clean"))
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds every Minecraft version and collects its JAR in build/libs."
    dependsOn(stonecutter.tasks.named("build"))
    dependsOn(stonecutter.tasks.named("collectJar"))
}

tasks.register("buildActive") {
    group = "build"
    description = "Builds only the active Minecraft version."
    dependsOn(stonecutter.tasks.named("build") { metadata.isActive })
}

tasks.register("runActive") {
    group = "fabric"
    description = "Runs the client for the active Minecraft version."
    dependsOn(stonecutter.tasks.named("runClient") { metadata.isActive })
}

stonecutter parameters {
    val gui = current.parsed >= "26.2"
    val sdl = current.parsed >= "26.3"
    val pipelineInBlaze3d = current.parsed < "26.3" || current.parsed >= "26.4-snapshot-1"

    // Minecraft 26.2 moves screens and HUD state into Gui and Hud.
    replacements.string(gui) {
        replace("mc.screen", "mc.gui.screen()")
        replace("minecraft.screen", "minecraft.gui.screen()")
        replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
        replace("mc.setScreen(", "mc.gui.setScreen(")
        replace("minecraft.setScreen(", "minecraft.gui.setScreen(")
        replace("mc.gui.chat", "mc.gui.hud.chat")
        replace("mc.gui.getChat()", "mc.gui.hud.getChat()")
        replace("mc.gameRenderer.mainCamera", "mc.gameRenderer.mainCamera()")
        replace("mc.isSingleplayer", "mc.hasSingleplayerServer()")
        replace("mc.options.hideGui", "mc.gui.hud.isHidden")
        replace("LevelRenderEvents.END_MAIN", "LevelRenderEvents.COLLECT_SUBMITS")
        replace("GameRenderer;getNightVisionScale", "GameRenderer;nightVisionScale")
        replace("mc.gui.setTimes(", "mc.gui.hud.setTimes(")
        replace("mc.gui.setTitle(", "mc.gui.hud.setTitle(")
        replace("mc.gui.setSubtitle(", "mc.gui.hud.setSubtitle(")
    }
    replacements.regex(gui) {
        replace("\\bNVGRenderer\\b", "UIRenderer", "\\bUIRenderer\\b", "NVGRenderer")
    }
    replacements.string(gui, "ui_frame") {
        replace("import quoi.utils.ui.rendering.NVGSpecialRenderer", "import quoi.utils.ui.rendering.UIRenderer")
        replace("NVGSpecialRenderer.draw(ctx, 0, 0, ctx.guiWidth(), ctx.guiHeight())", "UIRenderer.frame(ctx)")
    }

    replacements.string(current.parsed >= "26.4-snapshot-3", "texture_resources") {
        replace(
            "import net.minecraft.client.renderer.texture.DynamicTexture",
            "import net.minecraft.client.renderer.texture.TextureResources",
        )
        replace("DynamicTexture({", "TextureResources.from2dImage({")
        replace("DynamicTexture?", "TextureResources?")
        replace("DynamicTexture,", "TextureResources,")
        replace("it.textureView", "it.textureView()")
    }

    // Registry families replace the individual colored and weathered constants.
    replacements.string(gui) {
        val colors = mapOf(
            "WHITE" to "white", "ORANGE" to "orange", "MAGENTA" to "magenta", "LIGHT_BLUE" to "lightBlue",
            "YELLOW" to "yellow", "LIME" to "lime", "PINK" to "pink", "GRAY" to "gray",
            "LIGHT_GRAY" to "lightGray", "CYAN" to "cyan", "PURPLE" to "purple", "BLUE" to "blue",
            "BROWN" to "brown", "GREEN" to "green", "RED" to "red", "BLACK" to "black",
        )
        mapOf(
            "WOOL" to "WOOL", "CARPET" to "CARPET", "TERRACOTTA" to "DYED_TERRACOTTA",
            "SHULKER_BOX" to "DYED_SHULKER_BOX", "CANDLE" to "DYED_CANDLE",
            "STAINED_GLASS" to "STAINED_GLASS", "STAINED_GLASS_PANE" to "STAINED_GLASS_PANE",
        ).forEach { (old, family) ->
            colors.forEach { (color, method) -> replace("Blocks.${color}_$old", "Blocks.$family.$method()") }
        }
        listOf("DOOR", "TRAPDOOR", "BULB").forEach { family ->
            listOf("EXPOSED", "WEATHERED", "OXIDIZED").forEach { state ->
                replace("Blocks.${state}_COPPER_$family", "Blocks.COPPER_$family.weathering().${state.lowercase()}()")
                replace("Blocks.WAXED_${state}_COPPER_$family", "Blocks.COPPER_$family.waxed().${state.lowercase()}()")
            }
            replace("Blocks.WAXED_COPPER_$family", "Blocks.COPPER_$family.waxed().unaffected()")
        }
    }

    // SDL input, swing and movement packet changes in Minecraft 26.3.
    replacements.string(sdl) {
        replace("RedStoneWireBlock", "RedstoneWireBlock")
        replace("InputConstants.KEY_LSUPER", "InputConstants.KEY_LGUI")
        replace("InputConstants.KEY_RSUPER", "InputConstants.KEY_RGUI")
        replace("input.scancode()", "input.keycode()")
        replace("Util.getPlatform().openUri(", "Blaze3D.openUri(")
        replace("Util.getPlatform().openPath(", "Blaze3D.openPath(")
        replace("com.mojang.blaze3d.pipeline.BlendFunction", "com.mojang.renderpearl.api.pipeline.BlendFunction")
        replace("com.mojang.blaze3d.pipeline.ColorTargetState", "com.mojang.renderpearl.api.pipeline.ColorTargetState")
        replace("com.mojang.blaze3d.pipeline.DepthStencilState", "com.mojang.renderpearl.api.pipeline.DepthStencilState")
        replace("com.mojang.blaze3d.platform.CompareOp", "com.mojang.renderpearl.api.pipeline.CompareOp")
        replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
        replace("com.mojang.blaze3d.textures.FilterMode", "com.mojang.renderpearl.api.textures.FilterMode")
        replace("player.swing(InteractionHand.MAIN_HAND)", "player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false)")
        replace("player.swing(hand)", "player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false)")
        replace("level.addBreakingBlockEffect(pos, direction)", "level.addBreakingBlockEffects(pos, direction, false)")
        replace("packet.values.position", "packet.position.endPosition()")
        replace("player.drop(false)", "gameMode.dropItem(player, false)")
        replace("click.button()", "Mouse.normalizeButton(click.button())")
        replace("mouseButtonEvent.button()", "Mouse.normalizeButton(mouseButtonEvent.button())")
        replace("input.button()", "Mouse.normalizeButton(input.button())")
    }
    replacements.regex(sdl, "enderman_type") {
        replace("\\bEnderMan\\b", "Enderman", "\\bEnderman\\b", "EnderMan")
    }
    replacements.string(!pipelineInBlaze3d) {
        replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
    }

    swaps["system_cursor"] = when {
        !sdl -> "val \$1 by lazy { GLFW.glfwCreateStandardCursor(GLFW.GLFW_\$2_CURSOR) }"
        else -> "val \$1 by lazy { SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_\$3) }"
    }
    swaps["pipeline_start"] = when {
        current.parsed < "26.4-snapshot-1" -> "val \$1: RenderPipeline = RenderPipelines.register("
        else -> "val \$1: RenderPipeline ="
    }
    swaps["pipeline_end"] = when {
        current.parsed < "26.4-snapshot-1" -> ")"
        else -> "// Empty."
    }
    swaps["pipeline_vertices"] = when {
        !gui -> ".withVertexFormat(DefaultVertexFormat.\$1, VertexFormat.Mode.\$2)"
        else -> """
            .withVertexBinding(0, DefaultVertexFormat.${'$'}1)
            .withPrimitiveTopology(PrimitiveTopology.${'$'}2)
        """.trimIndent()
    }
}
