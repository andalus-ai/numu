-- M13: keep container custody facts distinct from shipment exceptions.
-- Existing rows remain valid; only the event vocabulary constraint is expanded.
ALTER TABLE courier_tracking_events
    DROP CONSTRAINT ck_courier_tracking_event_type;

ALTER TABLE courier_tracking_events
    ADD CONSTRAINT ck_courier_tracking_event_type CHECK (event_type IN (
        'SHIPMENT_BOOKED', 'PICKED_UP', 'ARRIVED_AT_HUB', 'DEPARTED_HUB', 'SORTED_AT_HUB',
        'LOADED_FOR_LINEHAUL', 'LINEHAUL_DEPARTED', 'LINEHAUL_ARRIVED',
        'ARRIVED_AT_DESTINATION_DEPOT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED',
        'DELIVERY_FAILED', 'DELIVERED', 'HELD', 'ADDRESS_CORRECTED', 'RETURN_INITIATED',
        'RETURN_IN_TRANSIT', 'RETURNED_TO_SENDER', 'CONTAINER_SEALED', 'EXCEPTION_RECORDED'
    ));
