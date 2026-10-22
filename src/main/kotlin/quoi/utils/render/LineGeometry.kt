package quoi.utils.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import kotlin.math.tan

internal class LineGeometry(
    private val cameraPos: Vec3,
    private val forward: Vec3,
    private val left: Vec3,
    private val up: Vec3,
    fovDegrees: Double,
    private val windowHeight: Double
) {
    private val tanHalfFov = tan(Math.toRadians(fovDegrees) / 2.0)

    private fun halfWidth(point: Vec3, thickness: Float): Double {
        val depth = point.subtract(cameraPos).dot(forward).coerceAtLeast(0.05)
        return ((thickness / 2.0) * (2.0 * depth * tanHalfFov) / windowHeight).coerceAtLeast(0.0015)
    }

    fun addSegment(buffer: VertexConsumer, pose: PoseStack.Pose, start: Vec3, end: Vec3, colour: Colour, thickness: Float) {
        val direction = end.subtract(start)
        if (direction.lengthSqr() <= 1.0E-6) return

        val normalized = direction.normalize()
        val perpendicular = left.scale(-normalized.dot(up)).add(up.scale(normalized.dot(left)))
        val primary = if (perpendicular.lengthSqr() > 1.0E-6) perpendicular.normalize() else up
        val secondary = direction.cross(primary).normalize()
        val startWidth = halfWidth(start, thickness)
        val endWidth = halfWidth(end, thickness)
        val translatedStart = start.subtract(cameraPos)
        val translatedEnd = end.subtract(cameraPos)

        addQuad(buffer, pose, translatedStart, translatedEnd, primary.scale(startWidth), primary.scale(endWidth), colour)
        addQuad(buffer, pose, translatedStart, translatedEnd, secondary.scale(startWidth), secondary.scale(endWidth), colour)
    }

    private fun addQuad(buffer: VertexConsumer, pose: PoseStack.Pose, start: Vec3, end: Vec3, startOffset: Vec3, endOffset: Vec3, colour: Colour) {
        buffer.addVertex(pose, (start.x - startOffset.x).toFloat(), (start.y - startOffset.y).toFloat(), (start.z - startOffset.z).toFloat()).setColor(colour.rgb)
        buffer.addVertex(pose, (start.x + startOffset.x).toFloat(), (start.y + startOffset.y).toFloat(), (start.z + startOffset.z).toFloat()).setColor(colour.rgb)
        buffer.addVertex(pose, (end.x + endOffset.x).toFloat(), (end.y + endOffset.y).toFloat(), (end.z + endOffset.z).toFloat()).setColor(colour.rgb)
        buffer.addVertex(pose, (end.x - endOffset.x).toFloat(), (end.y - endOffset.y).toFloat(), (end.z - endOffset.z).toFloat()).setColor(colour.rgb)
    }
}
