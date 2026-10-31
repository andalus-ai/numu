# Push device management projections

The push-device persistence entity contains a provider credential (FCM/APNs token or serialized Web Push subscription) and is intended for internal delivery flows. It must not be serialized from a management API.

## Safe management use cases

Use `PushDeviceService` methods from authenticated host adapters:

- `registerSummary`: registers or refreshes a device and returns metadata.
- `activeDeviceSummaries`: lists active devices for the authenticated tenant and recipient.
- `unregisterSummary`: disables a device and returns metadata only.

The returned `PushDeviceSummary` contains only device ID, tenant and recipient scope, platform, app-instance label, and lifecycle timestamps. It has no token, ciphertext, key ID, token fingerprint, endpoint, public key, or auth secret fields.

The JDBC implementation uses dedicated SQL projections for all three management operations. It does not select encrypted token columns and does not invoke `PushTokenProtector.reveal`, so management reads continue to work even if the decryption key service is unavailable. The token is still encrypted before it is persisted by the registration operation.

## Compatibility boundary

The older `register`, `activeDevices`, and `unregister` methods remain temporarily available for internal delivery-oriented callers. They return token-bearing `PushDevice` entities and are deprecated for management/API use. Host controllers must never serialize those entities. A future breaking API revision may remove the token-bearing service methods after all host integrations have migrated.

Tenant and recipient IDs must continue to be derived from the verified authenticated principal, not trusted from client-supplied identifiers.
