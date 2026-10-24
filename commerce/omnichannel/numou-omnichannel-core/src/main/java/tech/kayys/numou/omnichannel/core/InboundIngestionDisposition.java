package tech.kayys.numou.omnichannel.core;

/** Safe, payload-free result of the inbound webhook trust and persistence pipeline. */
public enum InboundIngestionDisposition { VERIFIED, REJECTED, DUPLICATE, PAYLOAD_CONFLICT, PAYLOAD_STORAGE_FAILED, VERIFICATION_TRANSITION_FAILED }
