package domain;

public record RouteMetrics(double distanceMeters, int timeSeconds) {

    public RouteMetrics {
        if (distanceMeters < 0) {
            throw new IllegalArgumentException("Physical distance cannot be negative.");
        }
        if (timeSeconds < 0) {
            throw new IllegalArgumentException("Travel time cannot be negative.");
        }
    }
}
