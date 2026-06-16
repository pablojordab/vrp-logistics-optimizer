package domain;

import java.util.Objects;

public record Vehicle(
        String id,
        int capacity,
        Coordinates depotLocation,
        TimeWindow shift) {

    public Vehicle {
        Objects.requireNonNull(id, "Vehicle ID cannot be null.");
        Objects.requireNonNull(depotLocation, "Vehicle depot cannot be null.");
        Objects.requireNonNull(shift, "Vehicle shift cannot be null.");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Vehicle ID cannot be empty.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Vehicle capacity must be greater than zero. Found: " + capacity);
        }
    }
}
