package spatial;

import domain.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public class Quadtree<T> {

    private static final int CAPACITY = 4;
    private static final int MAX_DEPTH = 10;

    private final BoundingBox boundary;
    private final List<QuadNode<T>> points;
    private final int depth;

    private Quadtree<T> northWest;
    private Quadtree<T> northEast;
    private Quadtree<T> southWest;
    private Quadtree<T> southEast;

    private boolean divided;

    public Quadtree(BoundingBox boundary) {
        this(boundary, 0);
    }

    private Quadtree(BoundingBox boundary, int depth) {
        this.boundary = boundary;
        this.depth = depth;
        this.points = new ArrayList<>(CAPACITY);
        this.divided = false;
    }

    public boolean insert(QuadNode<T> node) {
        if (!boundary.contains(node.getPoint())) {
            return false;
        }

        if (divided) {
            return (northWest.insert(node) || northEast.insert(node) ||
                    southWest.insert(node) || southEast.insert(node));
        }

        if (points.size() < CAPACITY || depth >= MAX_DEPTH) {
            points.add(node);
            return true;
        }

        subdivide();

        return (northWest.insert(node) || northEast.insert(node) ||
                southWest.insert(node) || southEast.insert(node));
    }

    private void subdivide() {
        double x = boundary.minLon();
        double y = boundary.minLat();
        double w = (boundary.maxLon() - boundary.minLon()) / 2.0;
        double h = (boundary.maxLat() - boundary.minLat()) / 2.0;

        int nextDepth = depth + 1;
        northWest = new Quadtree<>(new BoundingBox(y + h, x, boundary.maxLat(), x + w), nextDepth);
        northEast = new Quadtree<>(new BoundingBox(y + h, x + w, boundary.maxLat(), boundary.maxLon()), nextDepth);
        southWest = new Quadtree<>(new BoundingBox(y, x, y + h, x + w), nextDepth);
        southEast = new Quadtree<>(new BoundingBox(y, x + w, y + h, boundary.maxLon()), nextDepth);

        divided = true;

        for (QuadNode<T> p : points) {
            northWest.insert(p);
            northEast.insert(p);
            southWest.insert(p);
            southEast.insert(p);
        }

        points.clear();
    }

    public List<QuadNode<T>> query(BoundingBox range, List<QuadNode<T>> found) {
        if (!boundary.intersects(range)) {
            return found;
        }

        for (QuadNode<T> node : points) {
            if (range.contains(node.getPoint())) {
                found.add(node);
            }
        }

        if (divided) {
            northWest.query(range, found);
            northEast.query(range, found);
            southWest.query(range, found);
            southEast.query(range, found);
        }

        return found;
    }
}