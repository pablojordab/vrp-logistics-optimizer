package spatial;

import domain.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public class Quadtree<T> {

    private static final int CAPACITY = 4;

    private final BoundingBox boundary;
    private final List<QuadNode<T>> points;

    private Quadtree<T> northWest;
    private Quadtree<T> northEast;
    private Quadtree<T> southWest;
    private Quadtree<T> southEast;

    private boolean divided;

    public Quadtree(BoundingBox boundary) {
        this.boundary = boundary;
        this.points = new ArrayList<>(CAPACITY);
        this.divided = false;
    }

    public boolean insert(QuadNode<T> node) {
        if (!boundary.contains(node.getPoint())) {
            return false;
        }

        if (points.size() < CAPACITY) {
            points.add(node);
            return true;
        }

        if (!divided) {
            subdivide();
        }

        return (northWest.insert(node) || northEast.insert(node) ||
                southWest.insert(node) || southEast.insert(node));
    }

    private void subdivide() {
        double x = boundary.minLon();
        double y = boundary.minLat();
        double w = (boundary.maxLon() - boundary.minLon()) / 2.0;
        double h = (boundary.maxLat() - boundary.minLat()) / 2.0;

        BoundingBox nwBoundary = new BoundingBox(y + h, x, boundary.maxLat(), x + w);
        northWest = new Quadtree<>(nwBoundary);

        BoundingBox neBoundary = new BoundingBox(y + h, x + w, boundary.maxLat(), boundary.maxLon());
        northEast = new Quadtree<>(neBoundary);

        BoundingBox swBoundary = new BoundingBox(y, x, y + h, x + w);
        southWest = new Quadtree<>(swBoundary);

        BoundingBox seBoundary = new BoundingBox(y, x + w, y + h, boundary.maxLon());
        southEast = new Quadtree<>(seBoundary);

        divided = true;
    }
}
