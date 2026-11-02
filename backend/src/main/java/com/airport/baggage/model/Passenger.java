package com.airport.baggage.model;

/**
 * Passenger domain model linked to User credentials and booking records.
 */
public class Passenger {
    private String passengerId;
    private String userId;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String bookingReference;

    public Passenger() {}

    public Passenger(String passengerId, String userId, String fullName, String email,
                     String mobileNumber, String bookingReference) {
        this.passengerId = passengerId;
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.bookingReference = bookingReference;
    }

    public String getPassengerId() { return passengerId; }
    public void setPassengerId(String passengerId) { this.passengerId = passengerId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }
}

