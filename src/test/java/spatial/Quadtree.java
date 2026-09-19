package spatial;

import domain.BoundingBox;
import domain.Coordinates;

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

        if (!divided && points.size() < CAPACITY) {
            points.add(node);
            return true;
        }

        if (!divided && depth >= MAX_DEPTH) {
            points.add(node);
            return true;
        }

        if (!divided) {
            subdivide();
        }

        return insertIntoChild(node);
    }

    private boolean insertIntoChild(QuadNode<T> node) {

        double lat = node.getPoint().lat();
        double lon = node.getPoint().lon();

        double midLat =
                (boundary.minLat() + boundary.maxLat()) / 2.0;

        double midLon =
                (boundary.minLon() + boundary.maxLon()) / 2.0;

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

    public List<QuadNode<T>> query(BoundingBox range) {
        return query(
                range,
                new ArrayList<>()
        );
    }

    /*
     * ============================================================
     * NEAREST NEIGHBOUR
     * ============================================================
     */

    /**
     * Finds the closest node to the given coordinates.
     *
     * Returns null when the tree contains no points.
     */
    public QuadNode<T> nearest(Coordinates target) {

        if (target == null) {
            throw new IllegalArgumentException(
                    "Target coordinates cannot be null."
            );
        }

        NearestResult<T> result =
                new NearestResult<>();

        nearestRecursive(
                this,
                target,
                result
        );

        return result.node;
    }

    private void nearestRecursive(
            Quadtree<T> node,
            Coordinates target,
            NearestResult<T> result) {

        /*
         * If the minimum possible distance from the target
         * to this entire quadrant is already worse than our
         * current best candidate, we can completely ignore
         * this quadrant.
         */
        double minDistance =
                distanceSquaredToBoundary(
                        target,
                        node.boundary
                );

        if (minDistance >= result.bestDistance) {
            return;
        }

        /*
         * Check points stored in this node.
         */
        for (QuadNode<T> point : node.points) {

            double distance =
                    target.distanceSquaredTo(
                            point.getPoint()
                    );

            if (distance < result.bestDistance) {

                result.bestDistance = distance;
                result.node = point;
            }
        }

        if (!node.divided) {
            return;
        }

        /*
         * Search the quadrant containing the target first.
         * This gives us a good initial candidate early and
         * improves pruning of the remaining quadrants.
         */
        Quadtree<T>[] children =
                node.orderedChildren(target);

        for (Quadtree<T> child : children) {

            if (child != null) {

                nearestRecursive(
                        child,
                        target,
                        result
                );
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Quadtree<T>[] orderedChildren(
            Coordinates target) {

        double midLat =
                (boundary.minLat() + boundary.maxLat()) / 2.0;

        double midLon =
                (boundary.minLon() + boundary.maxLon()) / 2.0;

        boolean north =
                target.lat() >= midLat;

        boolean east =
                target.lon() >= midLon;

        Quadtree<T> first;
        Quadtree<T> second;
        Quadtree<T> third;
        Quadtree<T> fourth;

        if (north && east) {

            first = northEast;
            second = northWest;
            third = southEast;
            fourth = southWest;

        } else if (north) {

            first = northWest;
            second = northEast;
            third = southWest;
            fourth = southEast;

        } else if (east) {

            first = southEast;
            second = southWest;
            third = northEast;
            fourth = northWest;

        } else {

            first = southWest;
            second = southEast;
            third = northWest;
            fourth = northEast;
        }

        return new Quadtree[]{
                first,
                second,
                third,
                fourth
        };
    }

    /**
     * Finds the closest node satisfying the supplied predicate.
     *
     * This is the method we will use for logistics:
     *
     *     nearestFeasible(
     *         shipment.location(),
     *         cluster -> cluster.currentLoad + demand <= capacity
     *     )
     */
    public QuadNode<T> nearestFeasible(
            Coordinates target,
            java.util.function.Predicate<T> predicate) {

        if (target == null) {
            throw new IllegalArgumentException(
                    "Target coordinates cannot be null."
            );
        }

        if (predicate == null) {
            throw new IllegalArgumentException(
                    "Predicate cannot be null."
            );
        }

        NearestResult<T> result =
                new NearestResult<>();

        nearestFeasibleRecursive(
                this,
                target,
                predicate,
                result
        );

        return result.node;
    }

    private void nearestFeasibleRecursive(
            Quadtree<T> node,
            Coordinates target,
            java.util.function.Predicate<T> predicate,
            NearestResult<T> result) {

        double minDistance =
                distanceSquaredToBoundary(
                        target,
                        node.boundary
                );

        if (minDistance >= result.bestDistance) {
            return;
        }

        /*
         * Check points stored in this quadrant.
         */
        for (QuadNode<T> point : node.points) {

            T data = point.getData();

            if (!predicate.test(data)) {
                continue;
            }

            double distance =
                    target.distanceSquaredTo(
                            point.getPoint()
                    );

            if (distance < result.bestDistance) {

                result.bestDistance = distance;
                result.node = point;
            }
        }

        if (!node.divided) {
            return;
        }

        Quadtree<T>[] children =
                node.orderedChildren(target);

        for (Quadtree<T> child : children) {

            if (child != null) {

                nearestFeasibleRecursive(
                        child,
                        target,
                        predicate,
                        result
                );
            }
        }
    }

    /**
     * Calculates the minimum squared Euclidean distance
     * between a point and a bounding box.
     *
     * If the point lies inside the box, the distance is 0.
     */
    private double distanceSquaredToBoundary(
            Coordinates point,
            BoundingBox box) {

        double dx = 0.0;
        double dy = 0.0;

        if (point.lon() < box.minLon()) {
            dx = box.minLon() - point.lon();

        } else if (point.lon() > box.maxLon()) {
            dx = point.lon() - box.maxLon();
        }

        if (point.lat() < box.minLat()) {
            dy = box.minLat() - point.lat();

        } else if (point.lat() > box.maxLat()) {
            dy = point.lat() - box.maxLat();
        }

        return dx * dx + dy * dy;
    }

    private static class NearestResult<T> {

        private QuadNode<T> node = null;

        private double bestDistance =
                Double.POSITIVE_INFINITY;
    }
}