package com.airport.baggage.service;

import java.util.*;

/**
 * Intelligent Conveyor Route Planner implementing Dijkstra's algorithm
 * on a directed, weighted graph with positive travel times.
 */
public class ConveyorRoutePlanner {

    public static class RouteResult {
        private final boolean success;
        private final List<String> path;
        private final List<String> segmentIds;
        private final double totalTimeMinutes;
        private final String feasibility; // "FEASIBLE", "AT_RISK", "MISSED_CUTOFF", "NO_ROUTE"
        private final String message;

        public RouteResult(boolean success, List<String> path, List<String> segmentIds,
                           double totalTimeMinutes, String feasibility, String message) {
            this.success = success;
            this.path = path != null ? path : Collections.emptyList();
            this.segmentIds = segmentIds != null ? segmentIds : Collections.emptyList();
            this.totalTimeMinutes = Math.round(totalTimeMinutes * 100.0) / 100.0;
            this.feasibility = feasibility;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public List<String> getPath() { return path; }
        public List<String> getSegmentIds() { return segmentIds; }
        public double getTotalTimeMinutes() { return totalTimeMinutes; }
        public String getFeasibility() { return feasibility; }
        public String getMessage() { return message; }

        public String getFormattedPath() {
            return String.join(" -> ", path);
        }
    }

    private static class NodeRecord implements Comparable<NodeRecord> {
        final String node;
        final double distance;

        NodeRecord(String node, double distance) {
            this.node = node;
            this.distance = distance;
        }

        @Override
        public int compareTo(NodeRecord other) {
            return Double.compare(this.distance, other.distance);
        }
    }

    /**
     * Compute shortest path using Dijkstra's algorithm.
     * Skips failed/unavailable conveyor segments.
     * Evaluates cutoff feasibility against flight cutoff time remaining.
     */
    public RouteResult calculateRoute(Graph graph, String startNode, String destinationBay, double cutoffMinutesRemaining) {
        if (graph == null || startNode == null || destinationBay == null) {
            return new RouteResult(false, null, null, 0, "NO_ROUTE", "Invalid start or destination parameters.");
        }

        if (startNode.equalsIgnoreCase(destinationBay)) {
            List<String> path = Collections.singletonList(startNode);
            return new RouteResult(true, path, Collections.emptyList(), 0, "FEASIBLE", "Already at destination bay.");
        }

        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previousNode = new HashMap<>();
        Map<String, String> previousSegment = new HashMap<>();
        PriorityQueue<NodeRecord> pq = new PriorityQueue<>();

        distances.put(startNode, 0.0);
        pq.add(new NodeRecord(startNode, 0.0));

        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            NodeRecord current = pq.poll();
            String u = current.node;

            if (visited.contains(u)) continue;
            visited.add(u);

            if (u.equalsIgnoreCase(destinationBay)) {
                break; // Reached target with shortest distance
            }

            for (Graph.Edge edge : graph.getNeighbors(u)) {
                if (!edge.isActive()) {
                    continue; // Segment is failed/unavailable, skip!
                }

                String v = edge.getTarget();
                double weight = edge.getWeight();
                double newDist = distances.get(u) + weight;

                if (newDist < distances.getOrDefault(v, Double.MAX_VALUE)) {
                    distances.put(v, newDist);
                    previousNode.put(v, u);
                    previousSegment.put(v, edge.getSegmentId());
                    pq.add(new NodeRecord(v, newDist));
                }
            }
        }

        if (!distances.containsKey(destinationBay)) {
            return new RouteResult(false, null, null, 0, "NO_ROUTE",
                    "No operational conveyor route available from " + startNode + " to " + destinationBay + " due to network topology or segment failure.");
        }

        // Reconstruct path
        LinkedList<String> path = new LinkedList<>();
        LinkedList<String> segmentIds = new LinkedList<>();
        String curr = destinationBay;
        while (curr != null) {
            path.addFirst(curr);
            String seg = previousSegment.get(curr);
            if (seg != null) {
                segmentIds.addFirst(seg);
            }
            curr = previousNode.get(curr);
        }

        double totalTime = distances.get(destinationBay);

        // Evaluate cutoff feasibility
        // Rule: If totalTime <= cutoffMinutesRemaining - 5.0 => FEASIBLE
        // If between cutoff - 5.0 and cutoff => AT_RISK
        // If totalTime > cutoffMinutesRemaining => MISSED_CUTOFF
        String feasibility;
        String message;
        if (totalTime <= (cutoffMinutesRemaining - 5.0)) {
            feasibility = "FEASIBLE";
            message = "Optimal route calculated. Baggage will arrive with safe margin before cutoff.";
        } else if (totalTime <= cutoffMinutesRemaining) {
            feasibility = "AT_RISK";
            message = "Warning: Tight connection. Baggage travel time (" + String.format("%.1f", totalTime) +
                      "m) is close to flight cutoff window (" + String.format("%.1f", cutoffMinutesRemaining) + "m).";
        } else {
            feasibility = "MISSED_CUTOFF";
            message = "Critical: Conveyor travel time (" + String.format("%.1f", totalTime) +
                      "m) exceeds flight cutoff window (" + String.format("%.1f", cutoffMinutesRemaining) + "m). Baggage will miss loading cutoff!";
        }

        return new RouteResult(true, path, segmentIds, totalTime, feasibility, message);
    }
}

