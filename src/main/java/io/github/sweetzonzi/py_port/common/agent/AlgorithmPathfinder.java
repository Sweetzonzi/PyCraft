package io.github.sweetzonzi.py_port.common.agent;

import io.github.sweetzonzi.py_port.PyCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A* 寻路器，用于 AlgorithmAgent 自主导航。
 * <p>
 * 在 2.5D 网格上工作：对每个 (x, z) 找到可站立的地表 y，
 * 然后评估相邻位置之间的连通性（落差 ≤ 1 为可通行）。
 */
public class AlgorithmPathfinder {

    private static final int MAX_SEARCH_NODES = 50000;
    private static final int MAX_NEIGHBOR_SCAN_Y = 10;  // 从参考高度向下扫描的最大格数

    /**
     * 单目标 A* 寻路。
     *
     * @param level 世界
     * @param start 起点（地表上方可站立位置）
     * @param end   终点（地表上方可站立位置）
     * @return 路径节点列表（不含起点，含终点），若找不到返回 null
     */
    @Nullable
    public static List<BlockPos> findPath(BlockGetter level, BlockPos start, BlockPos end) {
        return findPath(level, start, end, MAX_SEARCH_NODES);
    }

    @Nullable
    public static List<BlockPos> findPath(BlockGetter level, BlockPos start, BlockPos end, int maxNodes) {
        // 起/终点合法性检查
        if (!isWalkable(level, start)) {
            PyCraft.LOGGER.warn("  findPath: start {} is NOT walkable!", start);
            return null;
        }
        if (!isWalkable(level, end)) {
            PyCraft.LOGGER.warn("  findPath: end {} is NOT walkable!", end);
            return null;
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Long> closedSet = new HashSet<>();
        Map<Long, Node> nodeMap = new HashMap<>();

        // 检查起点的邻居
        List<BlockPos> startNeighbors = getNeighbors(level, start);
        PyCraft.LOGGER.info("  findPath: start={}, end={}, startNeighbors={}", start, end, startNeighbors.size());

        Node startNode = new Node(start, 0, heuristic(start, end));
        openSet.add(startNode);
        nodeMap.put(packPos(start), startNode);

        int iterations = 0;
        while (!openSet.isEmpty() && nodeMap.size() < maxNodes) {
            Node current = openSet.poll();
            long currentKey = packPos(current.pos);

            if (closedSet.contains(currentKey)) continue;
            closedSet.add(currentKey);

            if (current.pos.equals(end)) {
                PyCraft.LOGGER.info("  findPath: found path in {} iterations, {} nodes explored", iterations, nodeMap.size());
                return reconstructPath(current);
            }

            int neighborCount = 0;
            for (BlockPos neighbor : getNeighbors(level, current.pos)) {
                neighborCount++;
                long neighborKey = packPos(neighbor);
                if (closedSet.contains(neighborKey)) continue;

                double stepCost = euclideanDist(current.pos, neighbor);
                // 额外惩罚：上坡多加 50% 代价
                if (neighbor.getY() > current.pos.getY()) {
                    stepCost *= 1.5;
                }
                double g = current.g + stepCost;

                Node existing = nodeMap.get(neighborKey);
                if (existing != null && existing.g <= g) continue;

                Node node = new Node(neighbor, g, heuristic(neighbor, end));
                node.parent = current;
                nodeMap.put(neighborKey, node);
                openSet.add(node);
            }

            if (neighborCount == 0 && iterations < 5) {
                PyCraft.LOGGER.warn("  findPath: node {} has 0 neighbors!", current.pos);
            }

            iterations++;
        }

        PyCraft.LOGGER.warn("  findPath: failed after {} iterations, {} nodes in map, openSet empty={}",
                iterations, nodeMap.size(), openSet.isEmpty());
        return null; // 无法到达
    }

    /**
     * 多目标路线规划（贪婪 TSP 近似）。
     * 从起点出发，按 价值/距离 比率排序访问目标，最后返回起点。
     *
     * @param level   世界
     * @param start   起点（可站立位置）
     * @param targets 扫描到的目标集
     * @return 完整路径（可逐段拼接），若任何一段无法到达返回 null
     */
    @Nullable
    public static List<BlockPos> findOptimalRoute(BlockGetter level, BlockPos start,
                                                  List<ScanResult> targets) {
        if (targets.isEmpty()) {
            return List.of(start);
        }

        // 为每个目标找到最近的可站立位置（检查 26 方向）
        BlockPos current = start;
        List<BlockPos> candidateStands = new ArrayList<>();
        for (ScanResult sr : targets) {
            BlockPos standPos = findClosestStandAround(level, sr.pos(), current);
            if (standPos != null) {
                candidateStands.add(standPos);
            }
        }

        if (candidateStands.isEmpty()) return null;

        // 贪心：重复选择 价值/距离 比率最高的未访问目标
        List<BlockPos> orderedStands = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        Map<String, Integer> valueMap = new HashMap<>();
        for (ScanResult sr : targets) {
            BlockPos standPos = findClosestStandAround(level, sr.pos(), current);
            if (standPos != null) {
                valueMap.put(posKey(standPos), sr.value());
            }
        }

        while (!candidateStands.isEmpty()) {
            BlockPos best = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (BlockPos candidate : candidateStands) {
                if (visited.contains(candidate)) continue;
                double dist = euclideanDist(current, candidate);
                if (dist < 0.1) dist = 0.1;
                int value = valueMap.getOrDefault(posKey(candidate), 1);
                double score = value / dist;

                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }

            if (best == null) break;
            visited.add(best);
            orderedStands.add(best);
            current = best;
        }

        // 逐段拼接路径
        List<BlockPos> fullPath = new ArrayList<>();
        BlockPos currentPos = start;

        for (BlockPos targetStand : orderedStands) {
            List<BlockPos> segment = findPath(level, currentPos, targetStand);
            if (segment == null) return null;

            // 跳过段首与上一段段尾重叠
            if (!fullPath.isEmpty() && !segment.isEmpty() && segment.get(0).equals(fullPath.get(fullPath.size() - 1))) {
                fullPath.addAll(segment.subList(1, segment.size()));
            } else {
                fullPath.addAll(segment);
            }
            currentPos = targetStand;
        }

        // 返回起点
        List<BlockPos> returnSegment = findPath(level, currentPos, start);
        if (returnSegment == null) return null;

        if (!fullPath.isEmpty() && !returnSegment.isEmpty() && returnSegment.get(0).equals(fullPath.get(fullPath.size() - 1))) {
            fullPath.addAll(returnSegment.subList(1, returnSegment.size()));
        } else {
            fullPath.addAll(returnSegment);
        }

        return fullPath;
    }

    // 寻路辅助

    /**
     * 获取某个可站立位置的所有可通行邻居。
     */
    public static List<BlockPos> getNeighbors(BlockGetter level, BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>(4);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : dirs) {
            int nx = pos.getX() + dir[0];
            int nz = pos.getZ() + dir[1];

            BlockPos candidate = findStandPosition(level, nx, nz, pos.getY());
            if (candidate == null) continue;

            int dy = candidate.getY() - pos.getY();
            if (Math.abs(dy) > 3) continue; // 落差超过 3 不可通行

            neighbors.add(candidate);
        }

        return neighbors;
    }

    /**
     * 在指定 (x, z) 处，从参考高度向下扫描找到可站立位置（窄范围，用于路径邻居查找）。
     */
    @Nullable
    public static BlockPos findStandPosition(BlockGetter level, int x, int z, int refY) {
        int startY = Math.min(refY + 2, level.getHeight() - 1);
        int minY = Math.max(level.getMinBuildHeight(), refY - MAX_NEIGHBOR_SCAN_Y);

        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isWalkable(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 在指定 (x, z) 处，从参考高度向上下扩展扫描找到可站立位置（宽范围，用于目标扫描）。
     *
     * @param rangeUp   参考高度向上的扫描范围（格）
     * @param rangeDown 参考高度向下的扫描范围（格）
     */
    @Nullable
    public static BlockPos findStandPositionRange(BlockGetter level, int x, int z,
                                                  int refY, int rangeUp, int rangeDown) {
        int startY = Math.min(refY + rangeUp, level.getHeight() - 1);
        int minY = Math.max(level.getMinBuildHeight(), refY - rangeDown);

        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isWalkable(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 从指定方块位置的相邻位置找到可站立位置。
     */
    @Nullable
    public static BlockPos findStandPosition(BlockGetter level, BlockPos blockPos) {
        return findStandPosition(level, blockPos.getX(), blockPos.getZ(), blockPos.getY());
    }

    /**
     * 在目标方块周围 26 个方向（3×3×3 去掉中心）中查找所有可站立位置。
     * 用于 A* 终点可达性检查——目标方块周围的地表可能因放置方块而改变，
     * 用单点检查可能遗漏，此方法提供更全面的检查。
     *
     * @return 所有可站立位置的列表（可能为空）
     */
    public static List<BlockPos> findStandPositionsAround(BlockGetter level, BlockPos targetPos) {
        List<BlockPos> positions = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos candidate = new BlockPos(
                            targetPos.getX() + dx,
                            targetPos.getY() + dy,
                            targetPos.getZ() + dz);
                    if (isWalkable(level, candidate)) {
                        positions.add(candidate);
                    }
                }
            }
        }
        return positions;
    }

    /**
     * 在目标方块周围 26 个方向中找到离 fromPos 最近的可站立位置。
     *
     * @param level    世界
     * @param targetPos 目标方块位置
     * @param fromPos  参考位置（选距离此点最近的可站立点）
     * @return 最近的可站立位置，若无则返回 null
     */
    @Nullable
    public static BlockPos findClosestStandAround(BlockGetter level, BlockPos targetPos, BlockPos fromPos) {
        List<BlockPos> candidates = findStandPositionsAround(level, targetPos);
        if (candidates.isEmpty()) return null;

        BlockPos closest = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            double dist = euclideanDist(candidate, fromPos);
            if (dist < minDist) {
                minDist = dist;
                closest = candidate;
            }
        }
        return closest;
    }

    /**
     * 判断某位置是否为可站立位置：脚下有碰撞体积的方块，且站立处和上方一格均为空气。
     */
    public static boolean isWalkable(BlockGetter level, BlockPos pos) {
        // pos 是"站立位置"，即站在 (x, y, z) 时，脚底在 y+1：
        // 脚下方块为 (x, y-1, z) 必须有碰撞体积
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.isAir()) return false;
        // 使用碰撞形状检查：非空碰撞形状 = 可站立
        var collisionShape = belowState.getCollisionShape(level, below);
        if (collisionShape.isEmpty()) return false;

        // 站立处和上方一格需为空气或可替代（如草丛、雪层）
        BlockState atState = level.getBlockState(pos);
        if (!atState.isAir() && !atState.canBeReplaced()) return false;

        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (!aboveState.isAir() && !aboveState.canBeReplaced()) return false;

        return true;
    }

    // 内部类与工具

    private static long packPos(BlockPos pos) {
        return ((long) pos.getX() & 0x3FFFFFF) << 38
                | ((long) pos.getY() & 0xFFFFFF) << 12
                | ((long) pos.getZ() & 0x3FFFFFF);
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return euclideanDist(a, b);
    }

    private static double euclideanDist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<BlockPos> reconstructPath(Node node) {
        List<BlockPos> path = new ArrayList<>();
        Node current = node;
        while (current.parent != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private static class Node implements Comparable<Node> {
        final BlockPos pos;
        final double g;
        final double h;
        final double f;
        Node parent;

        Node(BlockPos pos, double g, double h) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node node)) return false;
            return pos.equals(node.pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }
    }
}