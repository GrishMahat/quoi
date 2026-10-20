package quoi.utils.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

object CustomRenderLayer {
    private fun create(
        name: String,
        pipeline: RenderPipeline,
        configure: RenderSetup.RenderSetupBuilder.() -> Unit = {}
    ): RenderType {
        val setup = RenderSetup.builder(pipeline)
            //? if <26.2 {
            .bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)
            //?}
            .apply(configure)
            .createRenderSetup()
        return RenderType.create(name, setup)
    }

    val TRIANGLE_STRIP: RenderType = create("quoi_debug_quads", CustomRenderPipelines.TRIANGLE_STRIP) {
        setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
        sortOnUpload()
    }
    val TRIANGLE_STRIP_ESP: RenderType = create("quoi_debug_quads_esp", CustomRenderPipelines.TRIANGLE_STRIP_ESP) {
        sortOnUpload()
    }
}
