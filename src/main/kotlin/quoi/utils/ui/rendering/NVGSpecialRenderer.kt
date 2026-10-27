package quoi.utils.ui.rendering

import com.mojang.blaze3d.opengl.GlConst
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.joml.Matrix3x2f
import org.lwjgl.opengl.GL33C

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/ui/rendering/NVGPIPRenderer.kt
 */
class NVGSpecialRenderer(vertexConsumers: MultiBufferSource.BufferSource)
    : PictureInPictureRenderer<NVGSpecialRenderer.NVGRenderState>(vertexConsumers) {

    private var frameBuffer = 0
    private var stencilRenderBuffer = 0
    private var stencilWidth = 0
    private var stencilHeight = 0
    private var validatedTexture = -1

    override fun renderToTexture(state: NVGRenderState, poseStack: PoseStack) {
        val colorView = RenderSystem.outputColorTextureOverride ?: return
        val glColorTexture = colorView.texture() as? GlTexture ?: return
        val width = colorView.getWidth(0)
        val height = colorView.getHeight(0)

        preserveRenderState {
            bindFrameBuffer(glColorTexture.glId(), width, height)
            NVGRenderer.beginFrame(width.toFloat(), height.toFloat())
            try {
                state.renderContent()
            } finally {
                NVGRenderer.endFrame()
            }
        }
    }

    override fun getTranslateY(height: Int, windowScaleFactor: Int): Float = height / 2f
    override fun getRenderStateClass(): Class<NVGRenderState> = NVGRenderState::class.java
    override fun getTextureLabel(): String = "nvg_renderer"

    override fun close() {
        super.close()

        if (frameBuffer != 0) {
            GlStateManager._glDeleteFramebuffers(frameBuffer)
            frameBuffer = 0
        }

        if (stencilRenderBuffer != 0) {
            GL33C.glDeleteRenderbuffers(stencilRenderBuffer)
            stencilRenderBuffer = 0
        }

        stencilWidth = 0
        stencilHeight = 0
        validatedTexture = -1
    }

    private fun bindFrameBuffer(texture: Int, width: Int, height: Int) {
        val needsValidation = texture != validatedTexture || width != stencilWidth || height != stencilHeight

        if (frameBuffer == 0) {
            frameBuffer = GlStateManager.glGenFramebuffers()
        }

        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, frameBuffer)

        GlStateManager._glFramebufferTexture2D(
            GlConst.GL_FRAMEBUFFER,
            GlConst.GL_COLOR_ATTACHMENT0,
            GlConst.GL_TEXTURE_2D,
            texture,
            0
        )

        if (stencilRenderBuffer == 0) {
            stencilRenderBuffer = GL33C.glGenRenderbuffers()
        }

        GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, stencilRenderBuffer)
        if (stencilWidth != width || stencilHeight != height) {
            GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_STENCIL_INDEX8, width, height)
            stencilWidth = width
            stencilHeight = height
        }
        GL33C.glFramebufferRenderbuffer(
            GL33C.GL_FRAMEBUFFER,
            GL33C.GL_STENCIL_ATTACHMENT,
            GL33C.GL_RENDERBUFFER,
            stencilRenderBuffer
        )
        GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, 0)

        if (needsValidation) {
            val status = GL33C.glCheckFramebufferStatus(GlConst.GL_FRAMEBUFFER)
            check(status == GlConst.GL_FRAMEBUFFER_COMPLETE) {
                "Quoi NanoVG framebuffer is incomplete: 0x${status.toString(16)}"
            }
            validatedTexture = texture
        }

        GlStateManager._viewport(0, 0, width, height)
        GL33C.glClear(GL33C.GL_STENCIL_BUFFER_BIT)
    }

    data class NVGRenderState(
        private val x: Int,
        private val y: Int,
        private val width: Int,
        private val height: Int,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle?,
        private val renderPose: Matrix3x2f,
        val renderContent: () -> Unit
    ) : PictureInPictureRenderState {

        override fun scale(): Float = 1f
        override fun x0(): Int = x
        override fun y0(): Int = y
        override fun x1(): Int = x + width
        override fun y1(): Int = y + height
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun bounds(): ScreenRectangle? = bounds
        override fun pose(): Matrix3x2f = renderPose
    }

    /** NanoVG calls GL directly. Restore actual state to match Minecraft's cached state. */
    private class GlState {
        private val activeTexture = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE)
        private val texture: Int
        private val sampler: Int
        private val program = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM)
        private val vao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING)
        private val arrayBuffer = GL33C.glGetInteger(GL33C.GL_ARRAY_BUFFER_BINDING)
        private val uniformBuffer = GL33C.glGetInteger(GL33C.GL_UNIFORM_BUFFER_BINDING)
        private val uniformBinding = GL33C.glGetIntegeri(GL33C.GL_UNIFORM_BUFFER_BINDING, 0)
        private val uniformStart = GL33C.glGetInteger64i(GL33C.GL_UNIFORM_BUFFER_START, 0)
        private val uniformSize = GL33C.glGetInteger64i(GL33C.GL_UNIFORM_BUFFER_SIZE, 0)
        private val flags = intArrayOf(GL33C.GL_BLEND, GL33C.GL_DEPTH_TEST, GL33C.GL_CULL_FACE, GL33C.GL_SCISSOR_TEST, GL33C.GL_STENCIL_TEST)
        private val enabled = flags.map { GL33C.glIsEnabled(it) }
        private val blend = intArrayOf(GL33C.GL_BLEND_SRC_RGB, GL33C.GL_BLEND_DST_RGB, GL33C.GL_BLEND_SRC_ALPHA, GL33C.GL_BLEND_DST_ALPHA).map { GL33C.glGetInteger(it) }
        private val cull = GL33C.glGetInteger(GL33C.GL_CULL_FACE_MODE)
        private val frontFace = GL33C.glGetInteger(GL33C.GL_FRONT_FACE)
        private val colorMask = IntArray(4).also { GL33C.glGetIntegerv(GL33C.GL_COLOR_WRITEMASK, it) }
        private val unpackParameters = intArrayOf(GL33C.GL_UNPACK_ALIGNMENT, GL33C.GL_UNPACK_ROW_LENGTH, GL33C.GL_UNPACK_SKIP_PIXELS, GL33C.GL_UNPACK_SKIP_ROWS)
        private val unpackValues = unpackParameters.map { GL33C.glGetInteger(it) }

        init {
            // Uploads also happen during context creation and UI construction.
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0)
            texture = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D)
            sampler = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING)
        }

        fun restore() {
            GL33C.glUseProgram(program)
            GL33C.glBindVertexArray(vao)
            GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, arrayBuffer)
            if (uniformBinding != 0 && uniformSize > 0) GL33C.glBindBufferRange(GL33C.GL_UNIFORM_BUFFER, 0, uniformBinding, uniformStart, uniformSize)
            else GL33C.glBindBufferBase(GL33C.GL_UNIFORM_BUFFER, 0, uniformBinding)
            GL33C.glBindBuffer(GL33C.GL_UNIFORM_BUFFER, uniformBuffer)
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0)
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, texture)
            GL33C.glBindSampler(0, sampler)
            GL33C.glActiveTexture(activeTexture)
            flags.forEachIndexed { i, flag -> if (enabled[i]) GL33C.glEnable(flag) else GL33C.glDisable(flag) }
            GL33C.glBlendFuncSeparate(blend[0], blend[1], blend[2], blend[3])
            GL33C.glCullFace(cull)
            GL33C.glFrontFace(frontFace)
            GL33C.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0)
            unpackParameters.forEachIndexed { i, parameter -> GL33C.glPixelStorei(parameter, unpackValues[i]) }
        }
    }

    companion object {
        private var rendering = false
        private var renderState: GlState? = null

        internal fun prepareDraw() {
            if (rendering && renderState == null) {
                renderState = GlState()
                GL33C.glBindSampler(0, 0)
            }
        }

        private fun <T> preserveRenderState(content: () -> T): T {
            check(!rendering)
            rendering = true
            try {
                return content()
            } finally {
                try { renderState?.restore() } finally {
                    renderState = null
                    rendering = false
                }
            }
        }

        internal fun <T> preserveGlState(content: () -> T): T {
            val saved = GlState()
            try {
                return content()
            } finally {
                saved.restore()
            }
        }

        fun draw(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            renderContent: () -> Unit
        ) {
            val scissor = context.scissorStack.peek()
            val pose = Matrix3x2f(context.pose())
            val bounds = createBounds(x, y, x + width, y + height, pose, scissor)

            val state = NVGRenderState(
                x, y, width, height,
                scissor, bounds, pose,
                renderContent
            )
            context.guiRenderState.addPicturesInPictureState(state)
        }

        private fun createBounds(x0: Int, y0: Int, x1: Int, y1: Int, pose: Matrix3x2f, scissorArea: ScreenRectangle?): ScreenRectangle? =
            ScreenRectangle(x0, y0, x1 - x0, y1 - y0)
                .transformMaxBounds(pose)
                .let { if (scissorArea != null) scissorArea.intersection(it) else it }
    }
}
