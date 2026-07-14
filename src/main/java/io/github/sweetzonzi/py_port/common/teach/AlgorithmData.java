package io.github.sweetzonzi.py_port.common.teach;

import java.util.List;

public class AlgorithmData {

    public static List<AlgorithmEntry> getAll() {
        return List.of(A_STAR, DIJKSTRA, GREEDY, SORTING);
    }

    public static AlgorithmEntry getById(String id) {
        return getAll().stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static final AlgorithmEntry A_STAR = new AlgorithmEntry(
            "a_star",
            "A* 寻路算法",
            "寻路",
            "一种启发式搜索算法，结合了 Dijkstra 的准确性和 Greedy Best-First-Search 的效率。",
            """
A*（A-Star）是一种广泛应用于路径规划和图遍历的启发式搜索算法。
它通过评估函数 f(n) = g(n) + h(n) 来选择下一个要探索的节点，其中：
- g(n) 是从起点到当前节点 n 的实际代价
- h(n) 是从当前节点 n 到终点的估计代价（启发函数）

A* 算法保证在启发函数 h(n) 满足可采纳性（即不高估实际代价）的条件下找到最短路径。
它结合了 Dijkstra 算法（保证最优）和贪心最佳优先搜索（效率高）的优点。

特点：
- 完整性：在有限图中一定能找到路径
- 最优性：使用可采纳启发函数时保证找到最优路径
- 时间复杂度：O(b^d)，其中 b 是分支因子，d 是解的深度
- 空间复杂度：O(b^d)，需要存储所有生成的节点""",
            "O(b^d)  b=分支因子 d=解的深度",
            """
function AStar(start, goal, h)
    openSet = {start}
    cameFrom = empty map
    gScore = map with default value Infinity
    gScore[start] = 0
    fScore = map with default value Infinity
    fScore[start] = h(start)

    while openSet is not empty
        current = node in openSet with lowest fScore
        if current == goal
            return reconstructPath(cameFrom, current)

        openSet.remove(current)
        for each neighbor of current
            tentativeG = gScore[current] + d(current, neighbor)
            if tentativeG < gScore[neighbor]
                cameFrom[neighbor] = current
                gScore[neighbor] = tentativeG
                fScore[neighbor] = tentativeG + h(neighbor)
                if neighbor not in openSet
                    openSet.add(neighbor)

    return failure"""
    );

    private static final AlgorithmEntry DIJKSTRA = new AlgorithmEntry(
            "dijkstra",
            "迪杰斯特拉算法",
            "寻路",
            "经典的单源最短路径算法，适用于边权非负的图。",
            """
Dijkstra 算法由荷兰计算机科学家 Edsger W. Dijkstra 于 1956 年提出，
用于计算图中一个节点到其他所有节点的最短路径。

算法思路：
1. 从起点开始，将起点到自身的距离设为 0，到其他节点的距离设为无穷大
2. 每次从未处理的节点中选择距离最小的节点
3. 更新该节点所有邻居的距离（松弛操作）
4. 标记该节点为已处理
5. 重复步骤 2-4，直到所有节点都被处理

特点：
- 适用于有向图和无向图
- 要求图中所有边的权重为非负数
- 可以使用优先队列（二叉堆）优化到 O((V+E)logV)
- 是 A* 算法在启发函数 h(n)=0 时的特例""",
            "O((V+E)logV)  V=顶点数 E=边数",
            """
function Dijkstra(graph, start)
    dist = map with default value Infinity
    prev = empty map
    dist[start] = 0
    pq = priority queue ordered by dist
    pq.add((start, 0))

    while pq is not empty
        (u, d) = pq.poll()
        if d > dist[u]
            continue
        for each edge (u, v, w) in graph
            newDist = dist[u] + w
            if newDist < dist[v]
                dist[v] = newDist
                prev[v] = u
                pq.add((v, newDist))

    return dist, prev"""
    );

    private static final AlgorithmEntry GREEDY = new AlgorithmEntry(
            "greedy",
            "贪心算法",
            "策略",
            "每步选择当前最优解，期望最终得到全局最优解的策略。",
            """
贪心算法（Greedy Algorithm）是一种在每一步选择中都采取当前状态下最优（即最有利）的选择，
从而希望导致结果是全局最优的算法策略。

贪心算法的核心要素：
1. 贪心选择性质：全局最优解可以通过一系列局部最优选择来达到
2. 最优子结构：问题的最优解包含其子问题的最优解

并不是所有问题都适合用贪心算法，只有当问题具有上述两个性质时，
贪心算法才能保证得到全局最优解。

经典应用：
- 活动选择问题（区间调度）
- 霍夫曼编码（Huffman Coding）
- 最小生成树（Prim、Kruskal 算法）
- 找零问题（特定币制下）
- 背包问题的分数版本""",
            "O(n log n) 或 O(n)，取决于具体问题",
            """
function Greedy(problem)
    solution = empty
    while problem has remaining choices
        // 在当前状态下做出最优选择
        bestChoice = selectBest(problem.remainingChoices)
        solution.add(bestChoice)
        // 更新问题状态
        problem.update(bestChoice)
    return solution

// 示例：活动选择问题
function ActivitySelection(activities)
    sort activities by end time
    selected = {activities[0]}
    lastEnd = activities[0].end
    for i = 1 to activities.length - 1
        if activities[i].start >= lastEnd
            selected.add(activities[i])
            lastEnd = activities[i].end
    return selected"""
    );

    private static final AlgorithmEntry SORTING = new AlgorithmEntry(
            "sorting",
            "排序算法",
            "排序",
            "将一组数据按特定顺序重新排列的算法，是计算机科学中最基础的算法类别。",
            """
排序算法（Sorting Algorithm）是一种将一系列数据按照特定顺序（如升序或降序）重新排列的算法。
排序是计算机科学中最基本、最常用的算法之一，也是学习算法的入门内容。

常见排序算法分类：
1. 冒泡排序 (Bubble Sort) - O(n^2)
   - 重复遍历数组，比较相邻元素并交换
   - 简单但效率低，适合小规模数据

2. 选择排序 (Selection Sort) - O(n^2)
   - 每次从未排序部分选出最小元素放到已排序末尾
   - 不稳定排序

3. 插入排序 (Insertion Sort) - O(n^2)
   - 将未排序元素插入已排序部分的正确位置
   - 对近乎有序的数据效率高

4. 快速排序 (Quick Sort) - O(n log n)
   - 分治策略，选取基准元素分割数组
   - 实践中最快的通用排序算法之一

5. 归并排序 (Merge Sort) - O(n log n)
   - 分治策略，先分割后合并
   - 稳定排序，需要额外 O(n) 空间

6. 堆排序 (Heap Sort) - O(n log n)
   - 利用堆数据结构进行排序
   - 原地排序，不稳定""",
            "O(n log n) ~ O(n^2)，取决于具体算法",
            """
// 快速排序示例
function QuickSort(arr, low, high)
    if low < high
        pivot = partition(arr, low, high)
        QuickSort(arr, low, pivot - 1)
        QuickSort(arr, pivot + 1, high)

function partition(arr, low, high)
    pivot = arr[high]
    i = low - 1
    for j = low to high - 1
        if arr[j] <= pivot
            i = i + 1
            swap arr[i] and arr[j]
    swap arr[i + 1] and arr[high]
    return i + 1

// 归并排序示例
function MergeSort(arr, left, right)
    if left < right
        mid = (left + right) / 2
        MergeSort(arr, left, mid)
        MergeSort(arr, mid + 1, right)
        merge(arr, left, mid, right)"""
    );
}