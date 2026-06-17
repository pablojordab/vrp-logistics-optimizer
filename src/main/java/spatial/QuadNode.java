package spatial;

import domain.Coordinates;

public class QuadNode<T> {

    private final Coordinates point;
    private final T data;

    public QuadNode(Coordinates point, T data) {
        this.point = point;
        this.data = data;
    }

    public Coordinates getPoint() {
        return point;
    }

    public T getData() {
        return data;
    }
}
