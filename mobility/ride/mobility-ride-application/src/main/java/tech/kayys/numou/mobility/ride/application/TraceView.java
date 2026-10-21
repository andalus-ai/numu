package tech.kayys.numou.mobility.ride.application;

/** A trip's route for receipts and disputes: simplified (10 m tolerance) and polyline-encoded. */
public record TraceView(String polyline, int recordedPoints, int shownPoints, double distanceMeters) { }
