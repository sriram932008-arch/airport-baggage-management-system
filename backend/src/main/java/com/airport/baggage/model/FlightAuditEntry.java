package com.airport.baggage.model;

public class FlightAuditEntry {
    private final String auditId;
    private final String flightNumber;
    private final String fieldName;
    private final String previousValue;
    private final String newValue;
    private final String changedBy;
    private final String changedAt;

    public FlightAuditEntry(String auditId, String flightNumber, String fieldName,
                            String previousValue, String newValue, String changedBy, String changedAt) {
        this.auditId = auditId;
        this.flightNumber = flightNumber;
        this.fieldName = fieldName;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public String getAuditId() { return auditId; }
    public String getFlightNumber() { return flightNumber; }
    public String getFieldName() { return fieldName; }
    public String getPreviousValue() { return previousValue; }
    public String getNewValue() { return newValue; }
    public String getChangedBy() { return changedBy; }
    public String getChangedAt() { return changedAt; }
}
