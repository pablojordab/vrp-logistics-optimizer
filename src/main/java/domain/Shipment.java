package domain;

import java.util.Objects;

public record Shipment(String id, Coordinates location, int demand, TimeWindow timeWindow) {

    public Shipment{
        Objects.requireNonNull(id, "Shipment ID cannot be null.");
        Objects.requireNonNull(location, "Shipment location cannot be null.");
        Objects.requireNonNull(timeWindow, "Shipment time window cannot be null.");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Shipment ID cannot be empty.");
        }
        if (demand <= 0) {
            throw new IllegalArgumentException("Shipment demand must be strictly positive. Found: " + demand);
        }
    }
}
