package com.airport.baggage.model;

/**
 * Historical record of an intelligent routing calculation.
 */
public class RoutingRecord {
    private String routingId;
    private String baggageId;
    private String calculatedAt;
    private String startNode;
    private String destinationBay;
    private String path;
    private double totalTimeMinutes;
    private boolean feasible;
    private String failureReason; // empty if successful, or explanation if failed / at risk

    public RoutingRecord() {}

    public RoutingRecord(String routingId, String baggageId, String calculatedAt, String startNode,
                         String destinationBay, String path, double totalTimeMinutes,
                         boolean feasible, String failureReason) {
        this.routingId = routingId;
        this.baggageId = baggageId;
        this.calculatedAt = calculatedAt;
        this.startNode = startNode;
        this.destinationBay = destinationBay;
        this.path = path;
        this.totalTimeMinutes = totalTimeMinutes;
        this.feasible = feasible;
        this.failureReason = failureReason;
    }

    public String getRoutingId() { return routingId; }
    public void setRoutingId(String routingId) { this.routingId = routingId; }

    public String getBaggageId() { return baggageId; }
    public void setBaggageId(String baggageId) { this.baggageId = baggageId; }

    public String getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(String calculatedAt) { this.calculatedAt = calculatedAt; }

    public String getStartNode() { return startNode; }
    public void setStartNode(String startNode) { this.startNode = startNode; }

    public String getDestinationBay() { return destinationBay; }
    public void setDestinationBay(String destinationBay) { this.destinationBay = destinationBay; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public double getTotalTimeMinutes() { return totalTimeMinutes; }
    public void setTotalTimeMinutes(double totalTimeMinutes) { this.totalTimeMinutes = totalTimeMinutes; }

    public boolean isFeasible() { return feasible; }
    public void setFeasible(boolean feasible) { this.feasible = feasible; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}

