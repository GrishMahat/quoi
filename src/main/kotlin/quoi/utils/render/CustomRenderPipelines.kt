package quoi.utils.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import java.util.Optional
import net.minecraft.client.renderer.RenderPipelines
//? if <26.2 {
import com.mojang.blaze3d.vertex.VertexFormat
//?} else {
/*import com.mojang.blaze3d.PrimitiveTopology
*///?}

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/render/CustomRenderPipelines.kt
 */
object CustomRenderPipelines {
    // Follow vanilla's depth convention, which became reversed Z in 26.2.
    private val testedDepth = DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false)
    //$ pipeline_start LINE_LIST
    val LINE_LIST: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation("quoi/pipeline/lines")
            //$ pipeline_vertices POSITION_COLOR_NORMAL LINES {
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            //$}
            .withCull(false)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(testedDepth)
            .build()
    //$ pipeline_end {
    )
    //$}

    //$ pipeline_start LINE_LIST_ESP
    val LINE_LIST_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation("quoi/pipeline/lines_esp")
            //$ pipeline_vertices POSITION_COLOR_NORMAL LINES {
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
            //$}
            .withCull(false)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build()
    //$ pipeline_end {
    )
    //$}

    //$ pipeline_start TRIANGLE_STRIP
    val TRIANGLE_STRIP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("quoi/pipeline/debug_filled_box")
            .withCull(false)
            //$ pipeline_vertices POSITION_COLOR QUADS {
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            //$}
            .withDepthStencilState(testedDepth)
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    //$ pipeline_end {
    )
    //$}

    //$ pipeline_start TRIANGLE_STRIP_ESP
    val TRIANGLE_STRIP_ESP: RenderPipeline = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("quoi/pipeline/debug_filled_box_esp")
            .withCull(false)
            //$ pipeline_vertices POSITION_COLOR QUADS {
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            //$}
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
    //$ pipeline_end {
    )
    //$}
}
