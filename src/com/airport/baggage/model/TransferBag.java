package com.airport.baggage.model;

/**
 * TransferBag represents connecting baggage arriving from an inbound feeder flight.
 * Demonstrates Inheritance and elevated transfer handling priority for equal cutoffs.
 */
public class TransferBag extends Baggage {
    private String inboundFlight;       // e.g. "AI101"
    private double connectionWindowMinutes; // available transfer connection window

    public TransferBag() {
        super();
        setBaggageType("TRANSFER");
    }

    public TransferBag(String baggageId, String passengerId, String bookingReference,
                       String flightNumber, double weightKg, boolean operatorClearance,
                       String currentLocation, String status, String assignedBayId,
                       String routePath, double estimatedTravelTime, String cutoffFeasibility,
                       double cutoffMinutesRemaining, String infeedTime, String lastUpdated,
                       String inboundFlight, double connectionWindowMinutes) {
        super(baggageId, "TRANSFER", passengerId, bookingReference, flightNumber, weightKg,
              operatorClearance, currentLocation, status, assignedBayId, routePath,
              estimatedTravelTime, cutoffFeasibility, cutoffMinutesRemaining, infeedTime, lastUpdated);
        this.inboundFlight = inboundFlight;
        this.connectionWindowMinutes = connectionWindowMinutes;
    }

    public String getInboundFlight() { return inboundFlight; }
    public void setInboundFlight(String inboundFlight) { this.inboundFlight = inboundFlight; }

    public double getConnectionWindowMinutes() { return connectionWindowMinutes; }
    public void setConnectionWindowMinutes(double connectionWindowMinutes) { this.connectionWindowMinutes = connectionWindowMinutes; }

    @Override
    public String getSpecificTypeDetails() {
        return "Transfer from " + (inboundFlight != null ? inboundFlight : "Inbound Feeder") +
               " (Connection window: " + connectionWindowMinutes + "m)";
    }

    @Override
    public int getHandlingPriorityScore() {
        // Transfer status receives higher tie-breaker priority (score 50) over origin bags (10-30),
        // but note that compareTo strictly evaluates cutoffMinutesRemaining first!
        return 50;
    }
}

