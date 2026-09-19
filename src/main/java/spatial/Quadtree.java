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

        if (node == null || !boundary.contains(node.getPoint())) {
            return false;
        }

        /*
         * Leaf with available capacity.
         */
        if (!divided && points.size() < CAPACITY) {
            points.add(node);
            return true;
        }

        /*
         * Maximum depth reached.
         *
         * We deliberately allow the bucket to grow here.
         * This prevents pathological recursion with very close
         * or identical coordinates.
         */
        if (!divided && depth >= MAX_DEPTH) {
            points.add(node);
            return true;
        }

        /*
         * Subdivide the current region.
         */
        if (!divided) {
            subdivide();
        }

        /*
         * Insert into exactly ONE child.
         */
        return insertIntoChild(node);
    }

    private boolean insertIntoChild(QuadNode<T> node) {

        double lat = node.getPoint().lat();
        double lon = node.getPoint().lon();

        double midLat =
                (boundary.minLat() + boundary.maxLat()) / 2.0;

        double midLon =
                (boundary.minLon() + boundary.maxLon()) / 2.0;

        /*
         * We use >= for the north/east side.
         *
         * This gives every point exactly one destination,
         * including points located exactly on a subdivision line.
         */
        if (lat >= midLat) {

            if (lon < midLon) {
                return northWest.insert(node);
            } else {
                return northEast.insert(node);
            }

        } else {

            if (lon < midLon) {
                return southWest.insert(node);
            } else {
                return southEast.insert(node);
            }
        }
    }

    private void subdivide() {

        double minLat = boundary.minLat();
        double maxLat = boundary.maxLat();

        double minLon = boundary.minLon();
        double maxLon = boundary.maxLon();

        double midLat =
                (minLat + maxLat) / 2.0;

        double midLon =
                (minLon + maxLon) / 2.0;

        int nextDepth = depth + 1;

        /*
         * North-West
         */
        northWest =
                new Quadtree<>(
                        new BoundingBox(
                                midLat,
                                minLon,
                                maxLat,
                                midLon
                        ),
                        nextDepth
                );

        /*
         * North-East
         */
        northEast =
                new Quadtree<>(
                        new BoundingBox(
                                midLat,
                                midLon,
                                maxLat,
                                maxLon
                        ),
                        nextDepth
                );

        /*
         * South-West
         */
        southWest =
                new Quadtree<>(
                        new BoundingBox(
                                minLat,
                                minLon,
                                midLat,
                                midLon
                        ),
                        nextDepth
                );

        /*
         * South-East
         */
        southEast =
                new Quadtree<>(
                        new BoundingBox(
                                minLat,
                                midLon,
                                midLat,
                                maxLon
                        ),
                        nextDepth
                );

        divided = true;

        /*
         * Reinsert existing points.
         *
         * IMPORTANT:
         * Each point is inserted into exactly ONE child.
         */
        List<QuadNode<T>> existingPoints =
                new ArrayList<>(points);

        points.clear();

        for (QuadNode<T> point : existingPoints) {
            insertIntoChild(point);
        }
    }

    public List<QuadNode<T>> query(
            BoundingBox range,
            List<QuadNode<T>> found) {

        if (range == null) {
            throw new IllegalArgumentException(
                    "Query range cannot be null."
            );
        }

        if (found == null) {
            throw new IllegalArgumentException(
                    "Result list cannot be null."
            );
        }

        /*
         * No intersection -> entire subtree can be ignored.
         */
        if (!boundary.intersects(range)) {
            return found;
        }

        /*
         * Check points stored at this node.
         *
         * Normally this happens only for leaves, or when
         * MAX_DEPTH was reached.
         */
        for (QuadNode<T> node : points) {

            if (range.contains(node.getPoint())) {
                found.add(node);
            }
        }

        /*
         * Search children only if this node has been subdivided.
         */
        if (divided) {

            northWest.query(range, found);
            northEast.query(range, found);
            southWest.query(range, found);
            southEast.query(range, found);
        }

        return found;
    }

    /**
     * Convenience overload.
     */
    public List<QuadNode<T>> query(BoundingBox range) {
        return query(
                range,
                new ArrayList<>()
        );
    }
}