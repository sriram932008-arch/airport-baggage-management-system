package com.airport.baggage.model;

/**
 * Event entry in the baggage audit tracking history.
 */
public class TrackingEvent {
    private String historyId;
    private String baggageId;
    private String timestamp;
    private String locationNode;
    private String status;
    private String message;
    private String recordedBy;

    public TrackingEvent() {}

    public TrackingEvent(String historyId, String baggageId, String timestamp,
                         String locationNode, String status, String message, String recordedBy) {
        this.historyId = historyId;
        this.baggageId = baggageId;
        this.timestamp = timestamp;
        this.locationNode = locationNode;
        this.status = status;
        this.message = message;
        this.recordedBy = recordedBy;
    }

    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }

    public String getBaggageId() { return baggageId; }
    public void setBaggageId(String baggageId) { this.baggageId = baggageId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getLocationNode() { return locationNode; }
    public void setLocationNode(String locationNode) { this.locationNode = locationNode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRecordedBy() { return recordedBy; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
}

