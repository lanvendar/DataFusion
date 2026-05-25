package com.datafusion.common.graph;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 基于google guava graph 的有向无环图公共类.
 *
 * @param <T> 泛型
 * @author lanvendar
 * @version 1.0.0, 2021-12-24
 * @since 2021-12-24
 */
@Slf4j
public class DirectedAcyclicGraph<T> {
    
    /**
     * 有向无环图.
     */
    private MutableGraph<T> mutableGraph;
    
    /**
     * 边构造.
     *
     * @param edges 有向边
     */
    public DirectedAcyclicGraph(Map<T, List<T>> edges) {
        mutableGraph = GraphBuilder.directed().allowsSelfLoops(false).build();
        putEdge(edges);
    }
    
    /**
     * 边构造.
     *
     * @param edges 有向边
     */
    public DirectedAcyclicGraph(List<Map<T, T>> edges) {
        mutableGraph = GraphBuilder.directed().allowsSelfLoops(false).build();
        putEdge(edges);
    }
    
    /**
     * 定点和边构造.
     *
     * @param nodes 顶点
     * @param edges 有向边
     */
    public DirectedAcyclicGraph(Set<T> nodes, List<Map<T, T>> edges) {
        mutableGraph = GraphBuilder.directed().allowsSelfLoops(false).build();
        addNode(nodes);
        putEdge(edges);
    }
    
    /**
     * 增加节点.
     *
     * @param nodes 顶点
     */
    public void addNode(Set<T> nodes) {
        for (T node : nodes) {
            if (!mutableGraph.nodes().contains(node)) {
                mutableGraph.addNode(node);
            }
        }
    }
    
    /**
     * 增加节点和边. key 节点 value 节点对应边集合List
     *
     * @param edges 有向边
     */
    public void putEdge(Map<T, List<T>> edges) {
        for (T node : edges.keySet()) {
            for (T node1 : edges.get(node)) {
                mutableGraph.putEdge(node, node1);
            }
        }
    }
    
    /**
     * 增加节点和边.
     *
     * @param edges 有向边
     */
    public void putEdge(List<Map<T, T>> edges) {
        for (Map<T, T> nodes : edges) {
            for (T node : nodes.keySet()) {
                T node1 = node;
                T node2 = nodes.get(node1);
                mutableGraph.putEdge(node1, node2);
            }
        }
    }
    
    /**
     * 判断是否为DAG(Directed Acyclic Graph）有向无环图.
     *
     * <p>算法思路：
     * <ul>
     * <li>1. 根据"拓扑排序"算法判断：拓扑排序之后，若还剩有点，则表示有环</li>
     * <li>2. 拓扑排序算法：找到图中所有入度为0的点，放入序列，删除这些点和以这些点为出度的边，再找所有入度为0的点，依次循环</li>
     * </ul>
     *
     * @return true 是
     */
    public boolean isDagGraph() {
        Queue<T> queue = Queues.newArrayDeque();
        //拓扑排序列表维护
        List<T> topologicalSortList = Lists.newArrayList();
        //获取开始节点
        Set<T> list = getStartNode();
        for (T node : list) {
            queue.add(node);
            topologicalSortList.add(node);
        }
        //获取所有节点
        Map<Integer, Integer> nodeInDegreeMap = Maps.newHashMap();
        for (T node : mutableGraph.nodes()) {
            //因为节点是泛型,故取 object 的 hashCode 为 key
            nodeInDegreeMap.put(node.hashCode(), mutableGraph.inDegree(node));
        }
        while (!queue.isEmpty()) {
            //获取并删除
            T preNode = queue.poll();
            for (T successorNode : mutableGraph.successors(preNode)) {
                int indegree = nodeInDegreeMap.get(successorNode.hashCode());
                //-1：等效删除父节点以及相应的边
                if (--indegree == 0) {
                    //insert
                    queue.offer(successorNode);
                    topologicalSortList.add(successorNode);
                }
                nodeInDegreeMap.put(successorNode.hashCode(), indegree);
            }
        }
        
        log.debug("拓扑排序（topologicalSortList）:" + topologicalSortList);
        if (topologicalSortList.size() != mutableGraph.nodes().size()) {
            return false;
        }
        return true;
    }
    
    /**
     * 获取开始节点.
     *
     * @return 开始节点集合
     */
    public Set<T> getStartNode() {
        Set<T> startNodes = new HashSet<>();
        for (T node : mutableGraph.nodes()) {
            int inDegree = mutableGraph.inDegree(node);
            if (inDegree == 0) {
                startNodes.add(node);
            }
        }
        return startNodes;
    }
    
    /**
     * 深度优先,后按节点最后一次访问它们的顺序 单个起始节点.
     *
     * @param node 顶点
     * @return 有序顶点集合
     */
    public List<T> depthFirstPostOrder(T node) {
        Iterable<T> dfs = Traverser.forGraph(mutableGraph).depthFirstPostOrder(node);
        return sortDesc(dfs);
    }
    
    /**
     * 深度优先,后按节点最后一次访问它们的顺序 多个起始节点.
     *
     * @param nodes 顶点
     * @return 有序顶点集合
     */
    public List<T> depthFirstPostOrder(Set<T> nodes) {
        Iterable<T> dfs = Traverser.forGraph(mutableGraph).depthFirstPostOrder(nodes);
        return sortDesc(dfs);
    }
    
    /**
     * 深度优先,后按节点第一次访问它们的顺序 单个起始节点.
     *
     * @param node 顶点
     * @return 有序顶点集合
     */
    public List<T> depthFirstPreOrder(T node) {
        Iterable<T> dfs = Traverser.forGraph(mutableGraph).depthFirstPreOrder(node);
        return sortAsc(dfs);
    }
    
    /**
     * 深度优先,后按节点第一次访问它们的顺序 多个起始节点.
     *
     * @param nodes 顶点
     * @return 有序顶点集合
     */
    public List<T> depthFirstPreOrder(Set<T> nodes) {
        Iterable<T> dfs = Traverser.forGraph(mutableGraph).depthFirstPreOrder(nodes);
        return sortAsc(dfs);
    }
    
    /**
     * 广度优先排序 多个起始节点.
     *
     * @param node 顶点
     * @return 有序顶点集合
     */
    public List<T> breadthFirst(T node) {
        Iterable<T> dfs = Traverser.forGraph(mutableGraph).breadthFirst(node);
        return sortAsc(dfs);
    }
    
    /**
     * 广度优先排序 多个起始节点.
     *
     * @param nodes 顶点
     * @return 有序顶点集合
     */
    public List<T> breadthFirst(Set<T> nodes) {
        Iterable<T> dfs = Traverser.forGraph(mutableGraph).breadthFirst(nodes);
        return sortAsc(dfs);
    }
    
    /**
     * 倒序排列.
     *
     * @param dfs 有序顶点集合
     * @return 有序顶点集合
     */
    private List<T> sortDesc(Iterable<T> dfs) {
        List<T> desc = new ArrayList<>();
        for (T node : dfs) {
            desc.add(node);
        }
        Collections.reverse(desc);
        return desc;
    }
    
    /**
     * 正序排列.
     *
     * @param dfs 有序顶点集合
     * @return 有序顶点集合
     */
    private List<T> sortAsc(Iterable<T> dfs) {
        List<T> desc = new ArrayList<>();
        for (T node : dfs) {
            desc.add(node);
        }
        return desc;
    }
}
