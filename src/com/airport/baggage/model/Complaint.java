package com.airport.baggage.model;

/**
 * Represents a passenger complaint or feedback record.
 * Statuses: SUBMITTED, UNDER_REVIEW, IN_PROGRESS, RESOLVED, CLOSED
 * Categories: STAFF_BEHAVIOUR, BAGGAGE_DELAY, INCORRECT_STATUS, ROUTING_ERROR,
 *             FLIGHT_INFO_ERROR, BAGGAGE_MISSING, TECHNICAL_ERROR, OTHER
 */
public class Complaint {

    private String complaintId;
    private String passengerId;
    private String baggageId;      // optional
    private String flightNumber;   // optional
    private String category;
    private String subject;
    private String description;
    private String status;
    private String adminResponse;
    private String createdAt;
    private String updatedAt;

    public Complaint() {}

    public Complaint(String complaintId, String passengerId, String baggageId,
                     String flightNumber, String category, String subject,
                     String description, String status, String adminResponse,
                     String createdAt, String updatedAt) {
        this.complaintId   = complaintId;
        this.passengerId   = passengerId;
        this.baggageId     = baggageId;
        this.flightNumber  = flightNumber;
        this.category      = category;
        this.subject       = subject;
        this.description   = description;
        this.status        = status;
        this.adminResponse = adminResponse;
        this.createdAt     = createdAt;
        this.updatedAt     = updatedAt;
    }

    public String getComplaintId()   { return complaintId; }
    public void setComplaintId(String v) { this.complaintId = v; }

    public String getPassengerId()   { return passengerId; }
    public void setPassengerId(String v) { this.passengerId = v; }

    public String getBaggageId()     { return baggageId == null ? "" : baggageId; }
    public void setBaggageId(String v) { this.baggageId = v; }

    public String getFlightNumber()  { return flightNumber == null ? "" : flightNumber; }
    public void setFlightNumber(String v) { this.flightNumber = v; }

    public String getCategory()      { return category; }
    public void setCategory(String v) { this.category = v; }

    public String getSubject()       { return subject; }
    public void setSubject(String v) { this.subject = v; }

    public String getDescription()   { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getStatus()        { return status; }
    public void setStatus(String v)  { this.status = v; }

    public String getAdminResponse() { return adminResponse == null ? "" : adminResponse; }
    public void setAdminResponse(String v) { this.adminResponse = v; }

    public String getCreatedAt()     { return createdAt; }
    public void setCreatedAt(String v) { this.createdAt = v; }

    public String getUpdatedAt()     { return updatedAt; }
    public void setUpdatedAt(String v) { this.updatedAt = v; }
}
