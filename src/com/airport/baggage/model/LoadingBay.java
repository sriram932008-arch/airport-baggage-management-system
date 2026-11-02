package com.airport.baggage.model;

/**
 * Loading Bay model representing the destination bay for flight baggage loading.
 */
public class LoadingBay {
    private String bayId;           // e.g. "LB1"
    private String bayName;         // e.g. "Loading Bay 1 (Gate A1)"
    private String assignedFlight;  // e.g. "AI202"
    private int capacity;
    private String status;          // "ACTIVE", "MAINTENANCE"

    public LoadingBay() {}

    public LoadingBay(String bayId, String bayName, String assignedFlight, int capacity, String status) {
        this.bayId = bayId;
        this.bayName = bayName;
        this.assignedFlight = assignedFlight;
        this.capacity = capacity;
        this.status = status;
    }

    public String getBayId() { return bayId; }
    public void setBayId(String bayId) { this.bayId = bayId; }

    public String getBayName() { return bayName; }
    public void setBayName(String bayName) { this.bayName = bayName; }

    public String getAssignedFlight() { return assignedFlight; }
    public void setAssignedFlight(String assignedFlight) { this.assignedFlight = assignedFlight; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

