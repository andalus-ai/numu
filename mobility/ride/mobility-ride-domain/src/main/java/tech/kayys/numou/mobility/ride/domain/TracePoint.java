package tech.kayys.numou.mobility.ride.domain;

import tech.kayys.numou.mobility.kernel.geo.LatLng;

import java.time.Instant;

/** One accepted GPS fix of a trip. */
public record TracePoint(LatLng at, Instant time) { }
