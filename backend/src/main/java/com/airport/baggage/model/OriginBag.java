package com.airport.baggage.model;

/**
 * OriginBag represents baggage originating at this airport terminal check-in desk.
 * Demonstrates Inheritance and Polymorphic method overrides.
 */
public class OriginBag extends Baggage {
    private String checkInDesk;         // e.g. "DESK-A12"
    private String passengerCategory;   // "FIRST_CLASS", "BUSINESS", "ECONOMY"

    public OriginBag() {
        super();
        setBaggageType("ORIGIN");
    }

    public OriginBag(String baggageId, String passengerId, String bookingReference,
                     String flightNumber, double weightKg, boolean operatorClearance,
                     String currentLocation, String status, String assignedBayId,
                     String routePath, double estimatedTravelTime, String cutoffFeasibility,
                     double cutoffMinutesRemaining, String infeedTime, String lastUpdated,
                     String checkInDesk, String passengerCategory) {
        super(baggageId, "ORIGIN", passengerId, bookingReference, flightNumber, weightKg,
              operatorClearance, currentLocation, status, assignedBayId, routePath,
              estimatedTravelTime, cutoffFeasibility, cutoffMinutesRemaining, infeedTime, lastUpdated);
        this.checkInDesk = checkInDesk;
        this.passengerCategory = passengerCategory;
    }

    public String getCheckInDesk() { return checkInDesk; }
    public void setCheckInDesk(String checkInDesk) { this.checkInDesk = checkInDesk; }

    public String getPassengerCategory() { return passengerCategory; }
    public void setPassengerCategory(String passengerCategory) { this.passengerCategory = passengerCategory; }

    @Override
    public String getSpecificTypeDetails() {
        return "Origin Check-in at " + (checkInDesk != null ? checkInDesk : "Main Terminal") +
               " [" + (passengerCategory != null ? passengerCategory : "ECONOMY") + "]";
    }

    @Override
    public int getHandlingPriorityScore() {
        if ("FIRST_CLASS".equalsIgnoreCase(passengerCategory)) return 30;
        if ("BUSINESS".equalsIgnoreCase(passengerCategory)) return 20;
        return 10;
    }
}

