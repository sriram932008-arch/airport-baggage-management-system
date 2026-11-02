package com.airport.baggage.service;

import java.util.*;

/**
 * Directed weighted graph representing conveyor connections and travel times.
 */
public class Graph {

    public static class Edge {
        private final String segmentId;
        private final String target;
        private final double weight; // travel time in minutes
        private boolean active;

        public Edge(String segmentId, String target, double weight, boolean active) {
            this.segmentId = segmentId;
            this.target = target;
            this.weight = weight;
            this.active = active;
        }

        public String getSegmentId() { return segmentId; }
        public String getTarget() { return target; }
        public double getWeight() { return weight; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    private final Map<String, List<Edge>> adjacencyList = new HashMap<>();

    public void addEdge(String segmentId, String source, String destination, double weight, boolean active) {
        adjacencyList.putIfAbsent(source, new ArrayList<>());
        adjacencyList.putIfAbsent(destination, new ArrayList<>());
        adjacencyList.get(source).add(new Edge(segmentId, destination, weight, active));
    }

    public List<Edge> getNeighbors(String node) {
        return adjacencyList.getOrDefault(node, Collections.emptyList());
    }

    public Set<String> getNodes() {
        return adjacencyList.keySet();
    }

    public void setSegmentStatus(String segmentId, boolean active) {
        for (List<Edge> edges : adjacencyList.values()) {
            for (Edge edge : edges) {
                if (edge.getSegmentId().equalsIgnoreCase(segmentId)) {
                    edge.setActive(active);
                }
            }
        }
    }

    public void clear() {
        adjacencyList.clear();
    }
}

