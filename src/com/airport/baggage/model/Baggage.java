package com.airport.baggage.model;

/**
 * Abstract base class representing a baggage unit in the airport handling system.
 * Demonstrates Abstraction, Encapsulation, and Polymorphism.
 */
public abstract class Baggage implements Comparable<Baggage> {
    private String baggageId;
    private String baggageName;
    private String baggageType; // "ORIGIN" or "TRANSFER"
    private String passengerId;
    private String bookingReference;
    private String flightNumber;
    private double weightKg;
    private boolean operatorClearance;
    private String currentLocation; // Current conveyor node (e.g. "N1", "N4", "LB1")
    private String status;          // "AWAITING_CLEARANCE", "IN_TRANSIT", "LOADED", "CUTOFF_AT_RISK", "MISSED_CUTOFF", "ROUTE_FAILURE"
    private String assignedBayId;   // e.g. "LB1"
    private String routePath;       // e.g. "N1 -> N4 -> N5 -> N6 -> N9 -> N10 -> LB1"
    private double estimatedTravelTime; // in minutes
    private double estimatedRemainingProcessingTime;
    private long arrivalOrder;
    private String cutoffFeasibility;   // "FEASIBLE", "AT_RISK", "MISSED_CUTOFF", "UNROUTED"
    private double cutoffMinutesRemaining; // dynamic or flight-linked cutoff time remaining in minutes
    private String infeedTime;      // ISO format or time string e.g. "08:15"
    private String lastUpdated;

    public Baggage() {}

    public Baggage(String baggageId, String baggageType, String passengerId, String bookingReference,
                   String flightNumber, double weightKg, boolean operatorClearance, String currentLocation,
                   String status, String assignedBayId, String routePath, double estimatedTravelTime,
                   String cutoffFeasibility, double cutoffMinutesRemaining, String infeedTime, String lastUpdated) {
        this.baggageId = baggageId;
        this.baggageType = baggageType;
        this.passengerId = passengerId;
        this.bookingReference = bookingReference;
        this.flightNumber = flightNumber;
        this.weightKg = weightKg;
        this.operatorClearance = operatorClearance;
        this.currentLocation = currentLocation;
        this.status = status;
        this.assignedBayId = assignedBayId;
        this.routePath = routePath;
        this.estimatedTravelTime = estimatedTravelTime;
        this.cutoffFeasibility = cutoffFeasibility;
        this.cutoffMinutesRemaining = cutoffMinutesRemaining;
        this.infeedTime = infeedTime;
        this.lastUpdated = lastUpdated;
    }

    // Abstract methods demonstrating Polymorphism
    public abstract String getSpecificTypeDetails();
    public abstract int getHandlingPriorityScore(); // Higher value = higher priority for tie-breakers

    // Getters and Setters
    public String getBaggageId() { return baggageId; }
    public void setBaggageId(String baggageId) { this.baggageId = baggageId; }

    public String getBaggageName() { return baggageName; }
    public void setBaggageName(String baggageName) { this.baggageName = baggageName; }

    public String getBaggageType() { return baggageType; }
    public void setBaggageType(String baggageType) { this.baggageType = baggageType; }

    public String getPassengerId() { return passengerId; }
    public void setPassengerId(String passengerId) { this.passengerId = passengerId; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public boolean isOperatorClearance() { return operatorClearance; }
    public void setOperatorClearance(boolean operatorClearance) { this.operatorClearance = operatorClearance; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedBayId() { return assignedBayId; }
    public void setAssignedBayId(String assignedBayId) { this.assignedBayId = assignedBayId; }

    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }

    public double getEstimatedTravelTime() { return estimatedTravelTime; }
    public void setEstimatedTravelTime(double estimatedTravelTime) { this.estimatedTravelTime = estimatedTravelTime; }

    public double getEstimatedRemainingProcessingTime() { return estimatedRemainingProcessingTime; }
    public void setEstimatedRemainingProcessingTime(double time) {
        if (!Double.isFinite(time) || time < 0) throw new IllegalArgumentException("Processing time must be a non-negative number.");
        this.estimatedRemainingProcessingTime = time;
    }

    public long getArrivalOrder() { return arrivalOrder; }
    public void setArrivalOrder(long arrivalOrder) {
        if (arrivalOrder < 0) throw new IllegalArgumentException("Arrival order must not be negative.");
        this.arrivalOrder = arrivalOrder;
    }

    public String getCutoffFeasibility() { return cutoffFeasibility; }
    public void setCutoffFeasibility(String cutoffFeasibility) { this.cutoffFeasibility = cutoffFeasibility; }

    public double getCutoffMinutesRemaining() { return cutoffMinutesRemaining; }
    public void setCutoffMinutesRemaining(double cutoffMinutesRemaining) { this.cutoffMinutesRemaining = cutoffMinutesRemaining; }

    public String getInfeedTime() { return infeedTime; }
    public void setInfeedTime(String infeedTime) { this.infeedTime = infeedTime; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }

    /**
     * Scheduling comparator implementation:
     * 1. Earliest loading cutoff (strictly first - transfer status must never override an earlier cutoff).
     * 2. Equal cutoff tie-breaker: transfer status.
     * 3. Equal transfer status: configured passenger category.
     * 4. Equal category: arrival order.
     */
    @Override
    public int compareTo(Baggage other) {
        if (other == null) return -1;
        int cutoffComp = Double.compare(this.cutoffMinutesRemaining, other.cutoffMinutesRemaining);
        if (cutoffComp != 0) return cutoffComp;

        int transferComp = Boolean.compare("TRANSFER".equalsIgnoreCase(other.getBaggageType()),
                "TRANSFER".equalsIgnoreCase(this.getBaggageType()));
        if (transferComp != 0) return transferComp;

        int categoryComp = Integer.compare(other.getHandlingPriorityScore(), this.getHandlingPriorityScore());
        if (categoryComp != 0) return categoryComp;

        int arrivalComp = Long.compare(this.arrivalOrder, other.arrivalOrder);
        if (arrivalComp != 0) return arrivalComp;

        String t1 = this.infeedTime != null ? this.infeedTime : "";
        String t2 = other.infeedTime != null ? other.infeedTime : "";
        return t1.compareTo(t2);
    }
}
