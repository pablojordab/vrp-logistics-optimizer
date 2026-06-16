package domain;

public record TimeWindow(int startSeconds, int endSeconds) {

    public TimeWindow {
        if (startSeconds < 0 || endSeconds < 0) {
            throw new IllegalArgumentException("Time metrics cannot be negative.");
        }
        if (startSeconds > endSeconds) {
            throw new IllegalArgumentException("Start time cannot be greater than end time.");
        }
    }

    public boolean contains(int arrivalTimeSeconds) {
        return arrivalTimeSeconds >= startSeconds && arrivalTimeSeconds <= endSeconds;
    }

}
