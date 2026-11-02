package com.airport.baggage.model;

/**
 * Directed edge in the airport conveyor network graph with weight (travel time in minutes).
 */
public class ConveyorSegment {
    private String segmentId;           // e.g. "SEG-01"
    private String startNode;           // e.g. "N1"
    private String endNode;             // e.g. "N4"
    private double travelTimeMinutes;   // Positive weight
    private String status;              // "ACTIVE", "FAILED"

    public ConveyorSegment() {}

    public ConveyorSegment(String segmentId, String startNode, String endNode, double travelTimeMinutes, String status) {
        this.segmentId = segmentId;
        this.startNode = startNode;
        this.endNode = endNode;
        this.travelTimeMinutes = travelTimeMinutes;
        this.status = status;
    }

    public String getSegmentId() { return segmentId; }
    public void setSegmentId(String segmentId) { this.segmentId = segmentId; }

    public String getStartNode() { return startNode; }
    public void setStartNode(String startNode) { this.startNode = startNode; }

    public String getEndNode() { return endNode; }
    public void setEndNode(String endNode) { this.endNode = endNode; }

    public double getTravelTimeMinutes() { return travelTimeMinutes; }
    public void setTravelTimeMinutes(double travelTimeMinutes) { this.travelTimeMinutes = travelTimeMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }
}

