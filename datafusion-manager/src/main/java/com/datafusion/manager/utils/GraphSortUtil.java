package com.datafusion.manager.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
    * list<map<>>进行有向排序.
    *
    * @author wei.bowen
    * @version 1.0.0, 2026/4/9
    * @since 2026/4/9
*/

public class GraphSortUtil {

    // 逻辑名提取：a.C -> C
    private static String toLog(String name) {
        if (name == null) return null;
        int lastDot = name.lastIndexOf(".");
        return lastDot == -1 ? name : name.substring(lastDot + 1);
    }

    public static List<Map<String, String>> sortTableResults(List<Map<String, String>> allTableResults) {
        // 2. 构建邻接表和入度统计
        Map<String, List<Map<String, String>>> adj = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Set<String> allLogicalNodes = new HashSet<>();

        for (Map<String, String> m : allTableResults) {
            String rawKey = m.keySet().iterator().next();
            String rawValue = m.values().iterator().next();
            String u = toLog(rawKey);
            String v = toLog(rawValue);

            adj.computeIfAbsent(u, k -> new ArrayList<>()).add(m);
            inDegree.put(v, inDegree.getOrDefault(v, 0) + 1);
            allLogicalNodes.add(u);
            allLogicalNodes.add(v);
        }

        List<Map<String, String>> result = new ArrayList<>();
        Set<Map<String, String>> visitedEdges = new HashSet<>();

        // 3. 第一轮：处理有明确起点的链条（如 E -> F）
        List<String> roots = new ArrayList<>();
        for (String node : allLogicalNodes) {
            if (inDegree.getOrDefault(node, 0) == 0 && adj.containsKey(node)) {
                roots.add(node);
            }
        }
        //roots.sort(Comparator.reverseOrder()); // 确保 E 在前

        for (String root : roots) {
            dfs(root, adj, result, visitedEdges);
        }

        // 4. 第二轮：处理环路（剩余未访问的边）
        for (String node : adj.keySet()) {
            dfs(node, adj, result, visitedEdges);
        }
        return result;
    }

    private static void dfs(String u, Map<String, List<Map<String, String>>> adj,
                            List<Map<String, String>> result, Set<Map<String, String>> visitedEdges) {
        List<Map<String, String>> edges = adj.get(u);
        if (edges == null) return;

        for (Map<String, String> edge : edges) {
            if (!visitedEdges.contains(edge)) {
                visitedEdges.add(edge);
                result.add(edge);
                String nextV = toLog(edge.values().iterator().next());
                dfs(nextV, adj, result, visitedEdges);
            }
        }
    }

    public static void main(String[] args) {
        // 1. 初始化原始数据
        List<Map<String, String>> input = new ArrayList<>();
        input.add(Collections.singletonMap("A", "B"));
        input.add(Collections.singletonMap("B", "A"));
        input.add(Collections.singletonMap("a.C", "c.D"));
        input.add(Collections.singletonMap("B", "C"));
        input.add(Collections.singletonMap("E", "F"));
        input.add(Collections.singletonMap("k.C", "G"));
        input.add(Collections.singletonMap("D", "A")); // 构成环 A-B-C-D-A

        List<Map<String, String>> result = sortTableResults( input);

        // 打印结果
        System.out.println(result);
    }
}