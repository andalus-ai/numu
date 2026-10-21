package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.pesantren.domain.event.SantriAssignedToRoom;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** An asrama (dormitory) restricted to one gender, made of rooms with fixed capacity. */
public final class Dormitory extends AbstractAggregateRoot<DormitoryId> {

    /** A room inside the dormitory. */
    public static final class Room {
        private final DormRoomId id;
        private final String name;
        private final int capacity;
        private final Set<SantriId> occupants = new LinkedHashSet<>();

        private Room(DormRoomId id, String name, int capacity) {
            this.id = id;
            this.name = name;
            this.capacity = capacity;
        }

        public DormRoomId getId() { return id; }
        public String getName() { return name; }
        public int getCapacity() { return capacity; }
        public Set<SantriId> getOccupants() { return Collections.unmodifiableSet(occupants); }
        public boolean isFull() { return occupants.size() >= capacity; }
    }

    private final TenantId tenantId;
    private final PesantrenId pesantrenId;
    private final String name;
    private final Gender gender;
    private final Map<DormRoomId, Room> rooms = new LinkedHashMap<>();

    private Dormitory(DormitoryId id, TenantId tenantId, PesantrenId pesantrenId, String name, Gender gender) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.pesantrenId = Objects.requireNonNull(pesantrenId, "pesantrenId must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Dormitory name must not be blank");
        this.name = name.trim();
        this.gender = Objects.requireNonNull(gender, "gender must not be null");
    }

    public static Dormitory create(DormitoryId id, TenantId tenantId, PesantrenId pesantrenId, String name, Gender gender) {
        return new Dormitory(id, tenantId, pesantrenId, name, gender);
    }

    public DormRoomId addRoom(String roomName, int capacity) {
        if (roomName == null || roomName.isBlank()) throw new IllegalArgumentException("Room name must not be blank");
        if (capacity <= 0) throw new IllegalArgumentException("Room capacity must be positive");
        DormRoomId id = DormRoomId.generate();
        rooms.put(id, new Room(id, roomName.trim(), capacity));
        touch();
        return id;
    }

    public void assign(DormRoomId roomId, SantriId santriId, Gender santriGender) {
        Room room = rooms.get(roomId);
        if (room == null) throw new IllegalArgumentException("Unknown room in " + name);
        if (santriGender != gender) throw new IllegalStateException("Dormitory " + name + " is for " + gender + " only");
        if (rooms.values().stream().anyMatch(r -> r.occupants.contains(santriId))) {
            throw new IllegalStateException("Santri is already housed in " + name);
        }
        if (room.isFull()) throw new IllegalStateException("Room " + room.name + " is full");
        room.occupants.add(santriId);
        touch();
        raise(new SantriAssignedToRoom(getId(), tenantId, santriId, roomId));
    }

    public void vacate(SantriId santriId) {
        boolean removed = rooms.values().stream().anyMatch(r -> r.occupants.remove(santriId));
        if (!removed) throw new IllegalStateException("Santri is not housed in " + name);
        touch();
    }

    public int occupancy() { return rooms.values().stream().mapToInt(r -> r.occupants.size()).sum(); }
    public int capacity() { return rooms.values().stream().mapToInt(Room::getCapacity).sum(); }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public PesantrenId getPesantrenId() { return pesantrenId; }
    public String getName() { return name; }
    public Gender getGender() { return gender; }
    public Map<DormRoomId, Room> getRooms() { return Collections.unmodifiableMap(rooms); }
}
