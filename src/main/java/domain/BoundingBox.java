package domain;

public record BoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
    public BoundingBox {
        if (minLat > maxLat || minLon > maxLon) {
            throw new IllegalArgumentException("Invalid coordinates for BoundingBox");
        }
    }

    public boolean contains(Coordinates point) {
        return point.lat() >= minLat && point.lat() <= maxLat &&
                point.lon() >= minLon && point.lon() <= maxLon;
    }

    public boolean intersects(BoundingBox other) {
        return !(this.minLon > other.maxLon ||
                this.maxLon < other.minLon ||
                this.minLat > other.maxLat ||
                this.maxLat < other.minLat);
    }
}
