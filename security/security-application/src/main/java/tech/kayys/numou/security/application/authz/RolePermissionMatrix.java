package tech.kayys.numou.security.application.authz;

import tech.kayys.numou.security.domain.authorization.Permission;
import tech.kayys.numou.security.domain.valueobject.ActionType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static tech.kayys.numou.security.domain.valueobject.ActionType.CREATE;
import static tech.kayys.numou.security.domain.valueobject.ActionType.DELETE;
import static tech.kayys.numou.security.domain.valueobject.ActionType.PERFORM;
import static tech.kayys.numou.security.domain.valueobject.ActionType.READ;
import static tech.kayys.numou.security.domain.valueobject.ActionType.UPDATE;

/**
 * Immutable role → permission table. Roles are the strings carried in the token's
 * groups claim. Unknown roles grant nothing (deny by default).
 */
public final class RolePermissionMatrix {

    private final Map<String, Set<Permission>> byRole;

    private RolePermissionMatrix(Map<String, Set<Permission>> byRole) {
        this.byRole = byRole;
    }

    public Set<Permission> permissionsFor(Set<String> roles) {
        final var out = new HashSet<Permission>();
        for (final String role : roles) {
            out.addAll(byRole.getOrDefault(role, Set.of()));
        }
        return Set.copyOf(out);
    }

    /** Union of two matrices: a role present in both gets the permissions of both. */
    public RolePermissionMatrix merge(RolePermissionMatrix other) {
        final var out = new LinkedHashMap<String, Set<Permission>>();
        byRole.forEach((k, v) -> out.put(k, new HashSet<>(v)));
        other.byRole.forEach((k, v) -> out.computeIfAbsent(k, x -> new HashSet<>()).addAll(v));
        final var frozen = new LinkedHashMap<String, Set<Permission>>();
        out.forEach((k, v) -> frozen.put(k, Set.copyOf(v)));
        return new RolePermissionMatrix(Map.copyOf(frozen));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Set<Permission>> map = new LinkedHashMap<>();

        public Builder grant(String role, ActionType action, String... resourceTypes) {
            final var set = map.computeIfAbsent(role, r -> new HashSet<>());
            for (final String resourceType : resourceTypes) {
                set.add(Permission.of(action, resourceType));
            }
            return this;
        }

        public Builder grantAll(String role, Set<ActionType> actions, String... resourceTypes) {
            actions.forEach(a -> grant(role, a, resourceTypes));
            return this;
        }

        public RolePermissionMatrix build() {
            final var frozen = new LinkedHashMap<String, Set<Permission>>();
            map.forEach((k, v) -> frozen.put(k, Set.copyOf(v)));
            return new RolePermissionMatrix(Map.copyOf(frozen));
        }
    }

    /**
     * Default matrix for the restaurant suite. Voids, comps and no-show waivers are
     * {@code PERFORM} on dedicated resource types so they can be granted to managers
     * only, independently of ordinary order editing.
     */
    public static RolePermissionMatrix restaurantDefaults() {
        final var all = Set.of(CREATE, READ, UPDATE, DELETE, PERFORM);
        return builder()
                .grantAll("restaurant-admin", all,
                        Resources.VENUE, Resources.MENU, Resources.TABLE, Resources.ORDER,
                        Resources.ORDER_VOID, Resources.KITCHEN_TICKET, Resources.KITCHEN_AVAILABILITY,
                        Resources.RESERVATION, Resources.RESERVATION_WAIVER, Resources.WAITLIST)
                .grantAll("restaurant-manager", Set.of(CREATE, READ, UPDATE, PERFORM),
                        Resources.MENU, Resources.TABLE, Resources.ORDER, Resources.ORDER_VOID,
                        Resources.KITCHEN_TICKET, Resources.KITCHEN_AVAILABILITY,
                        Resources.RESERVATION, Resources.RESERVATION_WAIVER, Resources.WAITLIST)
                .grant("restaurant-manager", READ, Resources.VENUE)
                .grantAll("host", Set.of(CREATE, READ, UPDATE, PERFORM), Resources.RESERVATION, Resources.WAITLIST)
                .grant("host", READ, Resources.TABLE, Resources.VENUE)
                .grant("host", UPDATE, Resources.TABLE)
                .grantAll("waiter", Set.of(CREATE, READ, UPDATE, PERFORM), Resources.ORDER)
                .grant("waiter", READ, Resources.MENU, Resources.TABLE, Resources.RESERVATION, Resources.KITCHEN_TICKET)
                .grant("waiter", UPDATE, Resources.TABLE)
                .grant("kitchen", READ, Resources.KITCHEN_TICKET, Resources.MENU)
                .grant("kitchen", UPDATE, Resources.KITCHEN_TICKET)
                .grant("kitchen", PERFORM, Resources.KITCHEN_TICKET, Resources.KITCHEN_AVAILABILITY)
                .grant("kitchen", READ, Resources.KITCHEN_AVAILABILITY)
                .build();
    }

    /**
     * Default matrix for the ride-hailing suite. Four disjoint roles: riders book and manage their own rides; drivers run the
     * driver app; {@code ops-admin} approves drivers and intervenes in rides; {@code payment-gateway} is a service account that can
     * do exactly one thing, credit wallets. Ownership (a rider only sees their own rides) is enforced in the application layer.
     */
    public static RolePermissionMatrix mobilityDefaults() {
        return builder()
                .grantAll("rider", Set.of(CREATE, READ, UPDATE), Resources.RIDE)
                .grant("rider", READ, Resources.WALLET)
                .grant("rider", CREATE, Resources.WALLET)                 // request a top-up
                .grant("driver", CREATE, Resources.DRIVER_APP)
                .grantAll("driver", Set.of(READ, UPDATE, PERFORM), Resources.DRIVER_APP)
                .grant("driver", UPDATE, Resources.DRIVER_LOCATION)
                .grantAll("driver", Set.of(READ, UPDATE, PERFORM), Resources.RIDE)
                .grant("driver", READ, Resources.WALLET)
                .grant("driver", PERFORM, Resources.WALLET_PAYOUT)
                .grant("ops-admin", PERFORM, Resources.DRIVER_APPROVAL, Resources.RIDE_OPS, Resources.PAYOUT_OPS)
                .grant("ops-admin", READ, Resources.PAYOUT_OPS)
                .grant("ops-admin", READ, Resources.RIDE_OPS, Resources.WALLET, Resources.DRIVER_APP)
                .grant("payment-gateway", PERFORM, Resources.WALLET_TOPUP)
                .build();
    }

    /** Everything this platform ships: restaurant and mobility roles together. */
    public static RolePermissionMatrix platformDefaults() {
        return restaurantDefaults().merge(mobilityDefaults());
    }

    /** Resource-type names shared by REST resources and the matrix. */
    public static final class Resources {
        public static final String VENUE = "Venue";
        public static final String MENU = "Menu";
        public static final String TABLE = "Table";
        public static final String ORDER = "Order";
        public static final String ORDER_VOID = "OrderVoid";
        public static final String KITCHEN_TICKET = "KitchenTicket";
        public static final String KITCHEN_AVAILABILITY = "KitchenAvailability";
        public static final String RESERVATION = "Reservation";
        public static final String RESERVATION_WAIVER = "ReservationWaiver";
        public static final String WAITLIST = "Waitlist";
        public static final String RIDE = "Ride";
        public static final String RIDE_OPS = "RideOps";
        public static final String DRIVER_APP = "DriverApp";
        public static final String DRIVER_LOCATION = "DriverLocation";
        public static final String DRIVER_APPROVAL = "DriverApproval";
        public static final String WALLET = "Wallet";
        public static final String WALLET_PAYOUT = "WalletPayout";
        public static final String WALLET_TOPUP = "WalletTopUp";
        public static final String PAYOUT_OPS = "PayoutOps";

        private Resources() { }
    }
}
