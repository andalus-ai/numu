package tech.kayys.numou.courier.domain;
/** First-mile collection lifecycle, independent from final-mile delivery. */
public enum PickupRequestStatus { REQUESTED, ASSIGNED, COLLECTED, FAILED, CANCELLED }