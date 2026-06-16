package domain;

public record Coordinates(double lat, double lon) {

    public Coordinates {
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90. Found: " + lat);
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180. Found: " + lon);
        }
    }

    public double distanceSquaredTo(Coordinates other) {
        double dLat = this.lat - other.lat;
        double dLon = this.lon - other.lon;
        return (dLat * dLat) + (dLon * dLon);
    }
}
