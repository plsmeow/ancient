package meow.ancient.client.util.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Searches actual shape faces, subtracting blockers' projected shadows when simple rays fail. */
internal object BlockFaceResolver {
    private const val EPS = 1.0E-7
    fun aimPoint(eyes: Vec3, hit: BlockHitResult): Vec3 =
        // Continue the successful ray. A face-normal offset can switch faces at an edge.
        hit.location.add(hit.location.subtract(eyes).normalize().scale(0.002))

    private data class Point(val u: Double, val v: Double) {
        fun mix(other: Point, t: Double) = Point(u + (other.u - u) * t, v + (other.v - v) * t)
        fun distanceSq(other: Point) = (u - other.u) * (u - other.u) + (v - other.v) * (v - other.v)
    }
    private class Face(val box: AABB, val side: Direction) {
        val axis = side.axis
        val plane = when (side) {
            Direction.WEST -> box.minX; Direction.EAST -> box.maxX
            Direction.DOWN -> box.minY; Direction.UP -> box.maxY
            Direction.NORTH -> box.minZ; Direction.SOUTH -> box.maxZ
        }
        val min = project(Vec3(box.minX, box.minY, box.minZ))
        val max = project(Vec3(box.maxX, box.maxY, box.maxZ))
        val center = min.mix(max, 0.5)
        val rectangle = listOf(min, Point(max.u, min.v), max, Point(min.u, max.v))
        fun normal(point: Vec3) = when (axis) { Direction.Axis.X -> point.x; Direction.Axis.Y -> point.y; Direction.Axis.Z -> point.z }
        fun project(point: Vec3) = when (axis) {
            Direction.Axis.X -> Point(point.y, point.z)
            Direction.Axis.Y -> Point(point.x, point.z)
            Direction.Axis.Z -> Point(point.x, point.y)
        }
        fun point(point: Point, inside: Boolean = true): Vec3 {
            val depth = if (inside) EPS * side.axisDirection.step else 0.0
            return when (axis) {
                Direction.Axis.X -> Vec3(plane - depth, point.u, point.v)
                Direction.Axis.Y -> Vec3(point.u, plane - depth, point.v)
                Direction.Axis.Z -> Vec3(point.u, point.v, plane - depth)
            }
        }
        fun visible(eyes: Vec3) = (normal(eyes) - plane) * side.axisDirection.step > EPS
        fun nearest(point: Point) = Point(point.u.coerceIn(min.u, max.u), point.v.coerceIn(min.v, max.v))
    }

    @JvmOverloads
    fun direct(eyes: Vec3, pos: BlockPos, boxes: List<AABB>, reach: Double, side: Direction?, secondaryEyes: Vec3? = null): BlockHitResult? {
        if (!reach.isFinite() || reach <= 0.0 || boxes.isEmpty()) return null
        if (side == null && boxes.any { it.contains(eyes) }) return BlockHitResult(eyes, Direction.UP, pos, true)
            .takeIf { secondaryEyes == null || secondaryEyes.distanceToSqr(eyes) <= reach * reach }
        return faces(boxes, side).mapNotNull { face ->
            if (side == null && !face.visible(eyes)) return@mapNotNull null
            val points = buildList {
                add(face.center)
                add(face.nearest(face.project(eyes)))
                secondaryEyes?.let {
                    add(face.nearest(face.project(it)))
                    add(face.nearest(face.project(eyes).mix(face.project(it), 0.5)))
                }
            }
            val point = points.asSequence().map { face.point(it, inside = false) }.firstOrNull {
                eyes.distanceToSqr(it) <= reach * reach && (secondaryEyes == null || secondaryEyes.distanceToSqr(it) <= reach * reach)
            }
            point?.let { BlockHitResult(it, face.side, pos, false) }
        }.minByOrNull { eyes.distanceToSqr(it.location) }
    }

    fun find(eyes: Vec3, pos: BlockPos, boxes: List<AABB>, reach: Double, side: Direction? = null,
             preferred: Vec3? = null, secondaryEyes: Vec3? = null,
             trace: (Vec3, Vec3) -> BlockHitResult?, shapes: (BlockPos) -> List<AABB>,
             accepts: (BlockHitResult) -> Boolean = { true }): BlockHitResult? {
        if (reach <= 0.0 || boxes.isEmpty()) return null
        val reachSq = reach * reach
        val candidates = faces(boxes, side).filter { it.visible(eyes) }.sortedBy {
            eyes.distanceToSqr(it.point(it.nearest(it.project(eyes)), inside = false))
        }
        for (face in candidates) {
            if (eyes.distanceToSqr(face.point(face.nearest(face.project(eyes)), inside = false)) > reachSq) continue
            val blockers = LinkedHashMap<BlockPos, List<AABB>>()
            fun attempt(point: Point): BlockHitResult? {
                val end = face.point(point)
                val hit = trace(eyes, end) ?: return null
                if (hit.blockPos == pos && hit.direction == face.side) {
                    if (eyes.distanceToSqr(hit.location) <= reachSq
                        && (secondaryEyes == null || secondaryEyes.distanceToSqr(hit.location) <= reachSq)
                        && accepts(hit)) return hit
                } else blockers.getOrPut(hit.blockPos.immutable()) { shapes(hit.blockPos) }
                return null
            }
            val quick = buildList {
                preferred?.let { add(face.nearest(face.project(it)).mix(face.center, 0.001)) }
                add(face.center)
                add(face.nearest(face.project(eyes)).mix(face.center, 0.001))
                secondaryEyes?.let {
                    add(face.nearest(face.project(it)).mix(face.center, 0.001))
                    add(face.nearest(face.project(eyes).mix(face.project(it), 0.5)).mix(face.center, 0.001))
                }
                for (u in listOf(0.05, 0.5, 0.95)) for (v in listOf(0.05, 0.5, 0.95))
                    add(Point(face.min.u + (face.max.u - face.min.u) * u, face.min.v + (face.max.v - face.min.v) * v))
            }.distinct()
            for (point in quick) attempt(point)?.let { return it }

            var regions = listOf(face.rectangle)
            val subtracted = HashSet<BlockPos>()
            for (iteration in 0 until 96) {
                for ((block, obstacles) in blockers.toMap()) {
                    if (!subtracted.add(block)) continue
                    for (obstacle in obstacles) {
                        val shadow = shadow(face, eyes, obstacle)
                        if (shadow.size >= 3) regions = regions.flatMap { subtract(it, shadow) }
                        if (regions.isEmpty()) break
                    }
                }
                if (regions.isEmpty()) break
                // Large open patches provide a more stable aim than their exposed edge.
                val region = regions.maxBy { area(it) }
                val center = centroid(region)
                val closest = nearest(region, face.project(eyes)).mix(center, 0.02)
                val points = buildList {
                    add(center)
                    add(closest)
                    preferred?.let { add(nearest(region, face.project(it)).mix(center, 0.1)) }
                    for (vertex in region) add(vertex.mix(center, 0.1))
                }
                val before = blockers.size
                for (point in points) attempt(point)?.let { return it }
                if (blockers.size == before) regions = regions.filterNot { it === region }
                if (regions.size > 512) regions = regions.sortedByDescending { area(it) }.take(512)
            }
        }
        return null
    }

    private fun faces(boxes: List<AABB>, side: Direction?) = boxes.flatMap { box ->
        (side?.let { listOf(it) } ?: Direction.entries).map { Face(box, it) }
    }

    private fun shadow(face: Face, eyes: Vec3, box: AABB): List<Point> {
        val origin = face.normal(eyes)
        val distance = abs(face.plane - origin)
        if (distance <= EPS) return emptyList()
        val sign = if (face.plane > origin) 1.0 else -1.0
        val first = (face.normal(Vec3(box.minX, box.minY, box.minZ)) - origin) * sign
        val second = (face.normal(Vec3(box.maxX, box.maxY, box.maxZ)) - origin) * sign
        val low = maxOf(minOf(first, second), EPS)
        val high = minOf(maxOf(first, second), distance - EPS)
        if (high < low) return emptyList()
        val min = face.project(Vec3(box.minX, box.minY, box.minZ))
        val max = face.project(Vec3(box.maxX, box.maxY, box.maxZ))
        val eye = face.project(eyes)
        val projected = buildList {
            for (depth in listOf(low, high)) for (u in listOf(min.u, max.u)) for (v in listOf(min.v, max.v))
                add(Point(eye.u + (u - eye.u) * distance / depth, eye.v + (v - eye.v) * distance / depth))
        }
        var polygon = hull(projected)
        for (i in face.rectangle.indices) polygon = clip(polygon, face.rectangle[i], face.rectangle[(i + 1) % 4], true)
        return polygon
    }

    private fun hull(points: List<Point>): List<Point> {
        val sorted = points.distinct().sortedWith(compareBy<Point> { it.u }.thenBy { it.v })
        if (sorted.size < 3) return emptyList()
        fun half(points: List<Point>): List<Point> {
            val out = ArrayList<Point>()
            for (point in points) {
                while (out.size >= 2 && cross(out[out.size - 2], out.last(), point) <= EPS) out.removeAt(out.lastIndex)
                out.add(point)
            }
            return out.dropLast(1)
        }
        return half(sorted) + half(sorted.reversed())
    }

    private fun subtract(subject: List<Point>, shadow: List<Point>): List<List<Point>> {
        var inside = subject
        val out = ArrayList<List<Point>>()
        for (i in shadow.indices) {
            val a = shadow[i]
            val b = shadow[(i + 1) % shadow.size]
            val outside = clip(inside, a, b, false)
            if (outside.size >= 3 && area(outside) > EPS * EPS) out.add(outside)
            inside = clip(inside, a, b, true)
            if (inside.size < 3) break
        }
        return out
    }

    private fun clip(subject: List<Point>, a: Point, b: Point, inside: Boolean): List<Point> {
        if (subject.isEmpty()) return emptyList()
        val out = ArrayList<Point>()
        var previous = subject.last()
        var previousSide = cross(a, b, previous)
        for (point in subject) {
            val side = cross(a, b, point)
            val previousIn = if (inside) previousSide >= 0.0 else previousSide <= 0.0
            val currentIn = if (inside) side >= 0.0 else side <= 0.0
            if (previousIn != currentIn) out.add(previous.mix(point, previousSide / (previousSide - side)))
            if (currentIn) out.add(point)
            previous = point
            previousSide = side
        }
        return out
    }

    private fun cross(a: Point, b: Point, c: Point) = (b.u - a.u) * (c.v - a.v) - (b.v - a.v) * (c.u - a.u)
    private fun area(points: List<Point>): Double {
        if (points.size < 3) return 0.0
        return abs((1 until points.size - 1).sumOf { cross(points.first(), points[it], points[it + 1]) }) * 0.5
    }

    private fun centroid(points: List<Point>): Point {
        val origin = points.first()
        var total = 0.0
        var u = 0.0
        var v = 0.0
        for (i in 1 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val weight = abs(cross(origin, a, b))
            total += weight
            u += (origin.u + a.u + b.u) / 3.0 * weight
            v += (origin.v + a.v + b.v) / 3.0 * weight
        }
        return if (total > EPS * EPS) Point(u / total, v / total) else origin
    }

    private fun nearest(polygon: List<Point>, point: Point): Point {
        if (polygon.indices.all { cross(polygon[it], polygon[(it + 1) % polygon.size], point) >= 0.0 }) return point
        return polygon.indices.map { i ->
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            val length = a.distanceSq(b)
            val t = if (length > EPS * EPS) ((point.u - a.u) * (b.u - a.u) + (point.v - a.v) * (b.v - a.v)) / length else 0.0
            a.mix(b, t.coerceIn(0.0, 1.0))
        }.minBy { it.distanceSq(point) }
    }
}
