package tech.onetap.util.block;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A*-патфайндер по блокам (по мотивам AStarPathBuilder из LiquidBounce).
 * Используется TpAura в режиме Bypass -> Steps для обхода препятствий.
 * Диагональные перемещения разрешены.
 */
public final class AStarPathFinder {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final int MAX_ITERATIONS = 3000;
    private static final double STOP_RANGE = 2.0;
    private static final int MAX_VERTICAL_STEP = 9;

    private static final Vec3i[] HORIZONTAL_DIRECTIONS = {
            new Vec3i(-1, 0, 0), // left
            new Vec3i(1, 0, 0),  // right
            new Vec3i(0, 0, -1), // front
            new Vec3i(0, 0, 1)   // back
    };
    private static final Vec3i[] DIAGONAL_DIRECTIONS = {
            new Vec3i(-1, 0, -1), // left front
            new Vec3i(1, 0, -1),  // right front
            new Vec3i(-1, 0, 1),  // left back
            new Vec3i(1, 0, 1)    // right back
    };

    private AStarPathFinder() {
    }

    /**
     * Ищет путь от start до end.
     *
     * @return список точек (центр блока по X/Z, ноги на нижнем уровне блока) БЕЗ стартовой
     *         точки; пустой список, если цель уже близко или путь не найден
     */
    public static List<Vec3d> findPath(Vec3d start, Vec3d end) {
        if (mc.world == null || mc.player == null) return List.of();

        BlockPos startPos = BlockPos.ofFloored(start);
        BlockPos endPos = BlockPos.ofFloored(end);

        if (endPos.isWithinDistance(startPos, STOP_RANGE)) return List.of();

        List<BlockPos> path = aStar(startPos, endPos);
        if (path == null) return List.of();

        List<Vec3d> result = new ArrayList<>(path.size());
        for (BlockPos pos : path) {
            result.add(Vec3d.ofBottomCenter(pos));
        }
        return result;
    }

    /**
     * Схлопывает путь в минимальное число максимально длинных прямых отрезков:
     * от текущей точки берётся самая дальняя точка (включая end), до которой
     * проходима вся прямая. Только если прямая на всю дистанцию невозможна,
     * берётся максимально длинный прямой участок.
     */
    public static List<Vec3d> smoothPath(Vec3d start, List<Vec3d> path, Vec3d end) {
        List<Vec3d> targets = new ArrayList<>(path.size() + 1);
        targets.addAll(path);
        targets.add(end);

        List<Vec3d> waypoints = new ArrayList<>();
        Vec3d current = start;
        int i = 0;
        while (i < targets.size()) {
            int best = -1;
            for (int j = targets.size() - 1; j >= i; j--) {
                if (hasLineOfSight(current, targets.get(j))) {
                    best = j;
                    break;
                }
            }
            // Дальше только сквозь стены — прекращаем путь на последней валидной точке
            if (best == -1) break;
            Vec3d next = targets.get(best);
            waypoints.add(next);
            current = next;
            i = best + 1;
        }
        return waypoints;
    }

    /**
     * Проверяет, что вся прямая from -> to проходима для игрока.
     * Обход вокселей по лучу (Amanatides & Woo), каждый блок на линии
     * проверяется тем же isPassable, что и в A*.
     */
    public static boolean hasLineOfSight(Vec3d from, Vec3d to) {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        int x = (int) Math.floor(from.x);
        int y = (int) Math.floor(from.y);
        int z = (int) Math.floor(from.z);
        int endX = (int) Math.floor(to.x);
        int endY = (int) Math.floor(to.y);
        int endZ = (int) Math.floor(to.z);

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);

        double tMaxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? x + 1.0 - from.x : from.x - x) * tDeltaX;
        double tMaxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? y + 1.0 - from.y : from.y - y) * tDeltaY;
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? z + 1.0 - from.z : from.z - z) * tDeltaZ;

        if (!isPassable(pos.set(x, y, z))) return false;

        int guard = 0;
        while ((x != endX || y != endY || z != endZ) && guard++ < 2048) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (!isPassable(pos.set(x, y, z))) return false;
        }
        return true;
    }

    private static List<BlockPos> aStar(BlockPos start, BlockPos end) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        Map<BlockPos, Double> gScore = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        gScore.put(start, 0.0);
        open.add(new Node(start, heuristic(start, end)));

        int iterations = 0;
        while (!open.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node current = open.poll();
            if (current.pos().isWithinDistance(end, STOP_RANGE)) {
                return reconstructPath(cameFrom, current.pos());
            }
            if (!closed.add(current.pos())) continue;

            double currentG = gScore.getOrDefault(current.pos(), Double.MAX_VALUE);

            for (Neighbor neighbor : getNeighbors(current.pos())) {
                if (closed.contains(neighbor.pos())) continue;

                double tentativeG = currentG + neighbor.cost();
                if (tentativeG < gScore.getOrDefault(neighbor.pos(), Double.MAX_VALUE)) {
                    gScore.put(neighbor.pos(), tentativeG);
                    cameFrom.put(neighbor.pos(), current.pos());
                    open.add(new Node(neighbor.pos(), tentativeG + heuristic(neighbor.pos(), end)));
                }
            }
        }

        return null;
    }

    private static List<Neighbor> getNeighbors(BlockPos pos) {
        List<Neighbor> neighbors = new ArrayList<>(26);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (Vec3i direction : HORIZONTAL_DIRECTIONS) {
            mutable.set(pos, direction);
            if (isPassable(mutable)) {
                neighbors.add(new Neighbor(mutable.toImmutable(), 1.0));
            }
        }

        // Вертикаль: идём ступенями, чтобы каждый промежуточный блок был проходим —
        // иначе получится туннель сквозь пол/потолок
        for (int dy = 1; dy <= MAX_VERTICAL_STEP; dy++) {
            mutable.set(pos, 0, dy, 0);
            if (!isPassable(mutable)) break;
            neighbors.add(new Neighbor(mutable.toImmutable(), (double) dy * dy));
        }
        for (int dy = -1; dy >= -MAX_VERTICAL_STEP; dy--) {
            mutable.set(pos, 0, dy, 0);
            if (!isPassable(mutable)) break;
            neighbors.add(new Neighbor(mutable.toImmutable(), (double) dy * dy));
        }

        // Диагонали разрешены только если обе прилегающие стороны свободны
        for (Vec3i direction : DIAGONAL_DIRECTIONS) {
            mutable.set(pos, direction);
            if (isPassable(mutable)
                    && isPassable(pos.add(direction.getX(), 0, 0))
                    && isPassable(pos.add(0, 0, direction.getZ()))) {
                neighbors.add(new Neighbor(mutable.toImmutable(), 2.0));
            }
        }

        return neighbors;
    }

    private static boolean isPassable(Vec3i pos) {
        Box box = new Box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0
        );

        // Блоки не должны коллидировать с хитбоксом игрока (2 блока в высоту)
        for (VoxelShape shape : mc.world.getBlockCollisions(mc.player, box)) {
            if (!shape.isEmpty()) return false;
        }
        return true;
    }

    private static double heuristic(BlockPos pos, BlockPos end) {
        return pos.getSquaredDistance(end);
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos current) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos node = current;
        while (cameFrom.containsKey(node)) {
            path.add(node);
            node = cameFrom.get(node);
        }
        // Стартовая нода исключается из пути
        Collections.reverse(path);
        return path;
    }

    private record Node(BlockPos pos, double fScore) {
    }

    private record Neighbor(BlockPos pos, double cost) {
    }
}
