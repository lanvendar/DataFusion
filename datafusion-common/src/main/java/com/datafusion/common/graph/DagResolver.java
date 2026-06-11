package com.datafusion.common.graph;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * //todo 去除重复 有向无环图解析类.
 *
 * @param <K> 节点Id
 * @author 李正凯
 * @version 3.0 2022/4/24
 * @since 2022/4/24
 */
@Slf4j
public class DagResolver<K> {
    
    /**
     * 所有上游节点对应下游集合的映射map.
     */
    private final Map<K, Set<K>> startIdMap = new HashMap<>(2);
    
    /**
     * 所有下游节点对应上游集合的映射map.
     */
    private final Map<K, Set<K>> endIdMap = new HashMap<>(2);
    
    /**
     * DAG公共类.
     */
    protected DirectedAcyclicGraph<K> graph = null;
    
    /**
     * 尾节点集合.
     */
    private final Set<K> tailNodes = new HashSet<>();
    
    /**
     * 头节点集合.
     */
    private final Set<K> headNodes = new HashSet<>();
    
    /**
     * 构造器，构建DAG信息.
     *
     * @param linkVoList task上下游关系列表
     */
    public DagResolver(List<? extends Link<K>> linkVoList) {
        this(null, linkVoList);
    }
    
    /**
     * 构造器，构建DAG信息.
     *
     * @param nodes      单独节点
     * @param linkVoList task上下游关系列表
     */
    public DagResolver(Set<K> nodes, List<? extends Link<K>> linkVoList) {
        
        if (linkVoList == null) {
            linkVoList = new ArrayList<>();
        }
        startEndIdMap(linkVoList);
        
        List<Map<K, K>> linkList = linkList(linkVoList);
        createGraph(nodes, linkList);
        
        startIdMap.forEach((id, posts) -> {
            if (!endIdMap.containsKey(id)) {
                headNodes.add(id);
            }
        });
        if (CollectionUtil.isNotEmpty(nodes)) {
            headNodes.addAll(nodes);
        }
        
        endIdMap.forEach((id, posts) -> {
            if (!startIdMap.containsKey(id)) {
                tailNodes.add(id);
            }
        });
    }
    
    /**
     * 构建dag图.
     *
     * @param nodes    nodes
     * @param linkList linkList
     */
    private void createGraph(Set<K> nodes, List<Map<K, K>> linkList) {
        if (nodes != null) {
            this.graph = new DirectedAcyclicGraph<>(nodes, linkList);
        } else {
            this.graph = new DirectedAcyclicGraph<>(linkList);
        }
    }
    
    /**
     * 根据节点ID，获取它上游的节点集合.
     *
     * @param id 节点Id
     * @return 上游的节点集合
     */
    public Set<K> getPreNodeSet(K id) {
        return endIdMap.get(id);
    }
    
    /**
     * 根据节点ID，获取它下游的节点集合.
     *
     * @param id 节点Id
     * @return 下游的节点集合
     */
    public Set<K> getPostNodeSet(K id) {
        return startIdMap.get(id);
    }
    
    /**
     * 获取所有尾节点.
     *
     * @return 所有尾节点
     */
    public Set<K> getTailNodes() {
        return tailNodes;
    }
    
    /**
     * 获取所有头节点.
     *
     * @return 所有头节点
     */
    public Set<K> getHeadNodes() {
        return headNodes;
    }
    
    /**
     * 获取连线起始节点key集合(即有向fromVertex).
     *
     * @param linkVoList 界面连线对象
     * @return 起始点 map 集合
     */
    private void startEndIdMap(List<? extends Link<K>> linkVoList) {
        for (Link<K> vo : linkVoList) {
            
            K sId = vo.getStartId();
            K eId = vo.getEndId();
            
            if (sId != null) {
                Set<K> startSet = startIdMap.computeIfAbsent(sId, k -> new HashSet<>());
                startSet.add(eId);
            }
            
            if (eId != null) {
                Set<K> endSet = endIdMap.computeIfAbsent(eId, k -> new HashSet<>());
                endSet.add(sId);
            }
        }
        log.debug("有向开始节点集合:" + startIdMap);
        log.debug("有向结束节点集合:" + endIdMap);
    }
    
    /**
     * 获取节点边集合.
     *
     * @param linkVoList 界面连线对象
     */
    private List<Map<K, K>> linkList(List<? extends Link<K>> linkVoList) {
        List<Map<K, K>> linkList = new ArrayList<>();
        for (Link<K> vo : linkVoList) {
            K sId = vo.getStartId();
            K eId = vo.getEndId();
            
            Map<K, K> linkIdMap = new HashMap<>(4);
            linkIdMap.put(sId, eId);
            linkList.add(linkIdMap);
            
        }
        log.debug("节点边集合" + linkList.size() + "条:" + linkList.toString());
        return linkList;
    }
    
    /**
     * 广度优先遍历DAG图.
     *
     * @param consumer 处理方式
     */
    public void breadthFirstWalk(Consumer<K> consumer) {
        graph.breadthFirst(getHeadNodes()).forEach(consumer);
    }
    
    /**
     * 深度优先遍历DAG图.
     *
     * @param consumer 处理方式
     */
    public void depthFirstPostOrderWalk(Consumer<K> consumer) {
        graph.depthFirstPostOrder(getHeadNodes()).forEach(consumer);
    }
}
