package spatial;

import domain.BoundingBox;
import domain.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for the production {@link Quadtree}, covering all three
 * public query modes: {@link Quadtree#query}, {@link Quadtree#nearest} and
 * {@link Quadtree#nearestFeasible}.
 *
 * <p>Boundaries use a power-of-two box (0..8) wherever a test needs points
 * exactly on a subdivision line, so the midpoints (4, 2, 6, ...) are exactly
 * representable in floating point and the test is deterministic.</p>
 */
class QuadtreeTest {

    /** Node capacity is 4, so 5+ points in one region force a subdivision. */
    private static final BoundingBox EXACT_BOX = new BoundingBox(0, 0, 8, 8);

    private static QuadNode<Integer> node(double lat, double lon, int id) {
        return new QuadNode<>(new Coordinates(lat, lon), id);
    }

    private static Set<Integer> ids(List<QuadNode<Integer>> nodes) {
        Set<Integer> result = new HashSet<>();
        for (QuadNode<Integer> n : nodes) {
            result.add(n.getData());
        }
        return result;
    }

    // ------------------------------------------------------------------
    // query(BoundingBox)
    // ------------------------------------------------------------------

    @Test
    void insertRejectsNullAndOutOfBoundsPoints() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);

        assertFalse(tree.insert(null));
        assertFalse(tree.insert(node(9, 4, 1)), "latitude above the boundary");
        assertFalse(tree.insert(node(4, -1, 2)), "longitude below the boundary");
        assertTrue(tree.insert(node(4, 4, 3)));

        assertEquals(Set.of(3), ids(tree.query(EXACT_BOX)));
    }

    @Test
    void pointsExactlyOnSubdivisionLinesAreStoredOnceAndFound() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);

        assertTrue(tree.insert(node(4, 4, 1)));   // both midlines
        assertTrue(tree.insert(node(4, 1, 2)));   // lat midline only
        assertTrue(tree.insert(node(1, 4, 3)));   // lon midline only
        assertTrue(tree.insert(node(7, 7, 4)));
        assertTrue(tree.insert(node(0.5, 0.5, 5)));

        List<QuadNode<Integer>> all = tree.query(EXACT_BOX);

        assertEquals(5, all.size());
        assertEquals(Set.of(1, 2, 3, 4, 5), ids(all));
    }

    @Test
    void manyIdenticalPointsTerminateAndAreAllReturned() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);

        for (int i = 0; i < 100; i++) {
            assertTrue(tree.insert(node(3.3, 5.5, i)));
        }

        assertEquals(100, tree.query(EXACT_BOX).size());
        assertEquals(100, tree.query(new BoundingBox(3.3, 5.5, 3.3, 5.5)).size());
    }

    @Test
    void queryReturnsOnlyPointsInsideTheRange() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(1, 1, 1));
        tree.insert(node(1, 7, 2));
        tree.insert(node(7, 1, 3));
        tree.insert(node(7, 7, 4));
        tree.insert(node(4.5, 4.5, 5));
        tree.insert(node(3.5, 3.5, 6));

        assertEquals(Set.of(5, 6), ids(tree.query(new BoundingBox(3, 3, 5, 5))));
        assertEquals(Set.of(1), ids(tree.query(new BoundingBox(0, 0, 2, 2))));
        assertEquals(Set.of(), ids(tree.query(new BoundingBox(2, 2, 3, 3))));
    }

    @Test
    void queryRangeBoundaryIsInclusive() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(2, 2, 1));
        tree.insert(node(6, 6, 2));

        assertEquals(Set.of(1, 2), ids(tree.query(new BoundingBox(2, 2, 6, 6))));
    }

    @Test
    void queryOutsideTheBoundaryReturnsNothing() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(4, 4, 1));

        assertTrue(tree.query(new BoundingBox(20, 20, 30, 30)).isEmpty());
    }

    @Test
    void queryAppendsToTheProvidedListAndReturnsIt() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(4, 4, 1));

        List<QuadNode<Integer>> sink = new ArrayList<>();
        sink.add(node(0, 0, 99));

        List<QuadNode<Integer>> returned = tree.query(EXACT_BOX, sink);

        assertSame(sink, returned);
        assertEquals(Set.of(99, 1), ids(sink));
    }

    @Test
    void queryRejectsNullArguments() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);

        assertThrows(IllegalArgumentException.class, () -> tree.query(null, new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> tree.query(EXACT_BOX, null));
    }

    @Test
    void queryMatchesBruteForceOnRandomData() {
        BoundingBox monaco = new BoundingBox(43.72, 7.40, 43.75, 7.45);
        Random rnd = new Random(12345);

        Quadtree<Integer> tree = new Quadtree<>(monaco);
        List<QuadNode<Integer>> reference = new ArrayList<>();

        for (int i = 0; i < 2_000; i++) {
            double lat = 43.72 + rnd.nextDouble() * 0.03;
            double lon = 7.40 + rnd.nextDouble() * 0.05;
            QuadNode<Integer> n = node(lat, lon, i);
            assertTrue(tree.insert(n));
            reference.add(n);
        }

        for (int q = 0; q < 300; q++) {
            double lat = 43.72 + rnd.nextDouble() * 0.03;
            double lon = 7.40 + rnd.nextDouble() * 0.05;
            double h = rnd.nextDouble() * 0.01;
            double w = rnd.nextDouble() * 0.02;
            BoundingBox range = new BoundingBox(lat, lon, lat + h, lon + w);

            Set<Integer> expected = new HashSet<>();
            for (QuadNode<Integer> n : reference) {
                if (range.contains(n.getPoint())) {
                    expected.add(n.getData());
                }
            }

            List<QuadNode<Integer>> actual = tree.query(range);

            assertEquals(expected.size(), actual.size(), "duplicates or misses for query #" + q);
            assertEquals(expected, ids(actual), "wrong result set for query #" + q);
        }
    }

    // ------------------------------------------------------------------
    // nearest(Coordinates)
    // ------------------------------------------------------------------

    @Test
    void nearestRejectsNullTarget() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(4, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> tree.nearest(null));
    }

    @Test
    void nearestOnEmptyTreeReturnsNull() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        assertNull(tree.nearest(new Coordinates(4, 4)));
    }

    @Test
    void nearestFindsTheCloserOfTwoPointsAcrossAQuadrantBoundary() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);

        // Force a split so the two candidates land in different quadrants,
        // which is exactly the case a naive "search my own quadrant only"
        // implementation would get wrong.
        for (int i = 0; i < 4; i++) {
            tree.insert(node(0.1 * i, 0.1 * i, 100 + i));
        }
        tree.insert(node(3.9, 3.9, 1));   // just south-west of center, far quadrant
        tree.insert(node(4.1, 4.1, 2));   // just north-east of center, far quadrant

        QuadNode<Integer> nearest = tree.nearest(new Coordinates(4.0, 4.0));

        assertNotNull(nearest);
        assertTrue(nearest.getData() == 1 || nearest.getData() == 2,
                "expected one of the two closest points, got " + nearest.getData());
    }

    @Test
    void nearestMatchesBruteForceOnRandomData() {
        BoundingBox monaco = new BoundingBox(43.72, 7.40, 43.75, 7.45);
        Random rnd = new Random(777);

        Quadtree<Integer> tree = new Quadtree<>(monaco);
        List<QuadNode<Integer>> reference = new ArrayList<>();

        for (int i = 0; i < 1_500; i++) {
            double lat = 43.72 + rnd.nextDouble() * 0.03;
            double lon = 7.40 + rnd.nextDouble() * 0.05;
            QuadNode<Integer> n = node(lat, lon, i);
            tree.insert(n);
            reference.add(n);
        }

        for (int q = 0; q < 200; q++) {
            Coordinates target = new Coordinates(
                    43.72 + rnd.nextDouble() * 0.03,
                    7.40 + rnd.nextDouble() * 0.05
            );

            double bestDist = Double.MAX_VALUE;
            int bestId = -1;
            for (QuadNode<Integer> n : reference) {
                double d = target.distanceSquaredTo(n.getPoint());
                if (d < bestDist) {
                    bestDist = d;
                    bestId = n.getData();
                }
            }

            QuadNode<Integer> actual = tree.nearest(target);
            assertNotNull(actual, "query #" + q);

            double actualDist = target.distanceSquaredTo(actual.getPoint());
            // Compare distances, not ids: with random doubles a tie is
            // astronomically unlikely, but asserting on distance rather than
            // identity makes the oracle robust if one ever happens anyway.
            assertEquals(bestDist, actualDist, 1e-12, "query #" + q
                    + " expected nearest id " + bestId + " at dist " + bestDist
                    + " but got id " + actual.getData() + " at dist " + actualDist);
        }
    }

    // ------------------------------------------------------------------
    // nearestFeasible(Coordinates, Predicate)
    // ------------------------------------------------------------------

    @Test
    void nearestFeasibleRejectsNullArguments() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(4, 4, 1));
        Predicate<Integer> always = x -> true;

        assertThrows(IllegalArgumentException.class, () -> tree.nearestFeasible(null, always));
        assertThrows(IllegalArgumentException.class,
                () -> tree.nearestFeasible(new Coordinates(4, 4), null));
    }

    @Test
    void nearestFeasibleReturnsNullWhenNoCandidateMatches() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(4, 4, 1));
        tree.insert(node(2, 2, 2));

        assertNull(tree.nearestFeasible(new Coordinates(4, 4), x -> false));
    }

    @Test
    void nearestFeasibleSkipsACloserPointThatFailsThePredicate() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);
        tree.insert(node(4.1, 4.1, 1)); // closest to the target, but will be rejected
        tree.insert(node(1.0, 1.0, 2)); // farther away, but the only feasible one

        QuadNode<Integer> result = tree.nearestFeasible(
                new Coordinates(4.0, 4.0),
                id -> id == 2
        );

        assertNotNull(result);
        assertEquals(2, (int) result.getData());
    }

    /**
     * The scenario that motivated wiring {@code nearestFeasible} into
     * {@code ClusteringService} in the first place: a growing-bounding-box
     * search can return a feasible candidate that is NOT the globally
     * nearest one, if a closer feasible candidate happens to sit just
     * outside the box (near an edge, where Euclidean distance and box
     * containment disagree) while a farther one sits inside it (e.g. near a
     * corner). {@code nearestFeasible} must not have this gap: it has to
     * return the true nearest match regardless of where it falls relative
     * to any fixed-size box.
     */
    @Test
    void nearestFeasibleFindsTheTrueNearestEvenNearAQuadrantEdge() {
        Quadtree<Integer> tree = new Quadtree<>(EXACT_BOX);

        // Filler so the tree actually subdivides around the target.
        tree.insert(node(0.1, 0.1, 100));
        tree.insert(node(0.2, 0.2, 101));
        tree.insert(node(0.3, 0.3, 102));
        tree.insert(node(0.4, 0.4, 103));

        // Closer to (4,4) in straight-line distance (dist ~= 0.1) ...
        Coordinates closerButFar = new Coordinates(4.1, 4.0);
        // ... than this one (dist ~= 0.3), which otherwise "looks" central.
        Coordinates fartherButCentral = new Coordinates(3.7, 3.7);

        tree.insert(new QuadNode<>(closerButFar, 1));
        tree.insert(new QuadNode<>(fartherButCentral, 2));

        QuadNode<Integer> result = tree.nearestFeasible(new Coordinates(4.0, 4.0), x -> true);

        assertNotNull(result);
        assertEquals(1, (int) result.getData(), "expected the truly closest feasible point, not a merely nearby one");
    }

    @Test
    void nearestFeasibleMatchesBruteForceWithARandomPredicate() {
        BoundingBox monaco = new BoundingBox(43.72, 7.40, 43.75, 7.45);
        Random rnd = new Random(2024);

        Quadtree<Integer> tree = new Quadtree<>(monaco);
        List<QuadNode<Integer>> reference = new ArrayList<>();

        for (int i = 0; i < 1_500; i++) {
            double lat = 43.72 + rnd.nextDouble() * 0.03;
            double lon = 7.40 + rnd.nextDouble() * 0.05;
            QuadNode<Integer> n = node(lat, lon, i);
            tree.insert(n);
            reference.add(n);
        }

        // Only even ids are "feasible" — a stand-in for "has spare capacity".
        Predicate<Integer> evenOnly = id -> id % 2 == 0;

        for (int q = 0; q < 200; q++) {
            Coordinates target = new Coordinates(
                    43.72 + rnd.nextDouble() * 0.03,
                    7.40 + rnd.nextDouble() * 0.05
            );

            double bestDist = Double.MAX_VALUE;
            boolean found = false;
            for (QuadNode<Integer> n : reference) {
                if (!evenOnly.test(n.getData())) {
                    continue;
                }
                double d = target.distanceSquaredTo(n.getPoint());
                if (d < bestDist) {
                    bestDist = d;
                    found = true;
                }
            }

            QuadNode<Integer> actual = tree.nearestFeasible(target, evenOnly);

            assertTrue(found, "test setup error: no feasible candidate for query #" + q);
            assertNotNull(actual, "query #" + q);
            assertTrue(actual.getData() % 2 == 0, "returned an infeasible node for query #" + q);

            double actualDist = target.distanceSquaredTo(actual.getPoint());
            assertEquals(bestDist, actualDist, 1e-12, "query #" + q);
        }
    }
}
