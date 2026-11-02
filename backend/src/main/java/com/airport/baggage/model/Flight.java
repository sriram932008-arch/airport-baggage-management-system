package com.airport.baggage.model;

/**
 * Flight entity representing scheduled airline departures, loading cutoff times, and assigned bays.
 */
public class Flight {
    private String flightNumber;
    private String destination;
    private String scheduledDeparture; // e.g. "10:30" or ISO
    private String loadingCutoff;      // e.g. "10:00" or ISO
    private double cutoffMinutesRemaining; // simulated or calculated remaining minutes until cutoff
    private String assignedBayId;      // e.g. "LB1"
    private String status;             // "SCHEDULED", "BOARDING", "LOADING_CLOSED", "DEPARTED"

    public Flight() {}

    public Flight(String flightNumber, String destination, String scheduledDeparture,
                  String loadingCutoff, double cutoffMinutesRemaining, String assignedBayId, String status) {
        this.flightNumber = flightNumber;
        this.destination = destination;
        this.scheduledDeparture = scheduledDeparture;
        this.loadingCutoff = loadingCutoff;
        this.cutoffMinutesRemaining = cutoffMinutesRemaining;
        this.assignedBayId = assignedBayId;
        this.status = status;
    }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getScheduledDeparture() { return scheduledDeparture; }
    public void setScheduledDeparture(String scheduledDeparture) { this.scheduledDeparture = scheduledDeparture; }

    public String getLoadingCutoff() { return loadingCutoff; }
    public void setLoadingCutoff(String loadingCutoff) { this.loadingCutoff = loadingCutoff; }

    public double getCutoffMinutesRemaining() { return cutoffMinutesRemaining; }
    public void setCutoffMinutesRemaining(double cutoffMinutesRemaining) { this.cutoffMinutesRemaining = cutoffMinutesRemaining; }

    public String getAssignedBayId() { return assignedBayId; }
    public void setAssignedBayId(String assignedBayId) { this.assignedBayId = assignedBayId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

