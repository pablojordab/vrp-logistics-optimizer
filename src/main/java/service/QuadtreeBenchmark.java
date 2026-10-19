package service;

import spatial.Quadtree;
import spatial.QuadNode;
import domain.Coordinates;
import domain.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Benchmarks Quadtree range queries against a brute-force linear scan.
 *
 * This is a hand-rolled microbenchmark, not a JMH benchmark (the original
 * README called it "JMH-style", which overstated it). JMH would be the more
 * rigorous choice (proper forking, more reliable warmup detection), but it
 * would mean pulling in the JMH dependency and a separate benchmark module
 * — left as a known follow-up rather than done here. What this version DOES
 * fix, compared to the original:
 *
 *   1. Both methods run against the EXACT SAME set of query boxes, generated
 *      once up front — the original used two separately-seeded Random
 *      instances, so the two methods were answering different queries.
 *   2. A warmup phase runs both methods (discarding the timings) before any
 *      measurement, so the JIT is equally warmed up for both — the original
 *      ran the quadtree cold and the linear scan second, biasing the result
 *      in the quadtree's favor.
 *   3. Multiple timed trials are run (not one), reporting mean and standard
 *      deviation instead of a single number that could be a fluke.
 *   4. Trial order alternates which method goes first, to cancel out any
 *      residual ordering bias (GC pauses, OS scheduling) across trials.
 *   5. A correctness check runs once before timing: both methods must
 *      return the identical set of point indices for every query box, or
 *      the benchmark aborts. A fast-but-wrong result is not a valid result.
 *   6. Every query's result size is summed into an accumulator that's
 *      printed at the end. Two reasons: it reports selectivity (how much
 *      work each query actually did), and it gives the JIT a reason it
 *      can't prove is side-effect-free, which rules out dead-code
 *      elimination discarding the query loop entirely.
 */
public class QuadtreeBenchmark {

    private static final int N = 10_000;
    private static final int QUERIES = 1_000;
    private static final int WARMUP_TRIALS = 5;
    private static final int TIMED_TRIALS = 10;

    private static final double MIN_LAT = 43.72, MAX_LAT = 43.75;
    private static final double MIN_LON = 7.40, MAX_LON = 7.45;
    private static final long SEED = 42L;

    public static void main(String[] args) {
        Random rand = new Random(SEED);

        // --- Build the dataset once. Neither method mutates it, so both
        // can safely reuse it across every trial. ---
        List<Coordinates> points = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            double lat = MIN_LAT + rand.nextDouble() * (MAX_LAT - MIN_LAT);
            double lon = MIN_LON + rand.nextDouble() * (MAX_LON - MIN_LON);
            points.add(new Coordinates(lat, lon));
        }

        BoundingBox worldBoundary = new BoundingBox(MIN_LAT, MIN_LON, MAX_LAT, MAX_LON);
        Quadtree<Integer> tree = new Quadtree<>(worldBoundary);
        for (int i = 0; i < N; i++) {
            tree.insert(new QuadNode<>(points.get(i), i));
        }

        // --- Build the query set once, shared by both methods and every
        // trial. This is the fix for the original's biggest flaw: both
        // methods must answer the exact same questions. ---
        double queryW = (MAX_LON - MIN_LON) * 0.05;
        double queryH = (MAX_LAT - MIN_LAT) * 0.05;
        List<BoundingBox> queries = new ArrayList<>(QUERIES);
        for (int i = 0; i < QUERIES; i++) {
            double qLat = MIN_LAT + rand.nextDouble() * (MAX_LAT - MIN_LAT - queryH);
            double qLon = MIN_LON + rand.nextDouble() * (MAX_LON - MIN_LON - queryW);
            queries.add(new BoundingBox(qLat, qLon, qLat + queryH, qLon + queryW));
        }

        // --- Correctness check: both methods must agree before we trust
        // either one's timing. Run once, outside the timed loop. ---
        verifyCorrectness(tree, points, queries);

        // --- Warmup: run both methods, discard the timings. Lets the JIT
        // compile hot paths in both before anything gets measured. ---
        for (int t = 0; t < WARMUP_TRIALS; t++) {
            runQuadtreeTrial(tree, queries);
            runLinearTrial(points, queries);
        }

        // --- Timed trials, alternating which method goes first each trial
        // so neither one systematically benefits from running "fresh". ---
        double[] quadMs = new double[TIMED_TRIALS];
        double[] linearMs = new double[TIMED_TRIALS];
        long resultChecksum = 0;

        for (int t = 0; t < TIMED_TRIALS; t++) {
            boolean quadFirst = (t % 2 == 0);
            if (quadFirst) {
                TrialResult q = runQuadtreeTrial(tree, queries);
                TrialResult l = runLinearTrial(points, queries);
                quadMs[t] = q.elapsedMs;
                linearMs[t] = l.elapsedMs;
                resultChecksum += q.totalHits + l.totalHits;
            } else {
                TrialResult l = runLinearTrial(points, queries);
                TrialResult q = runQuadtreeTrial(tree, queries);
                quadMs[t] = q.elapsedMs;
                linearMs[t] = l.elapsedMs;
                resultChecksum += q.totalHits + l.totalHits;
            }
        }

        double quadMean = mean(quadMs);
        double quadStd = stddev(quadMs, quadMean);
        double linearMean = mean(linearMs);
        double linearStd = stddev(linearMs, linearMean);

        // Average hits per query, computed from the last quadtree trial
        // (both methods return identical result sets, per the correctness
        // check above, so either would give the same figure).
        TrialResult selectivitySample = runQuadtreeTrial(tree, queries);
        double avgHitsPerQuery = selectivitySample.totalHits / (double) QUERIES;

        System.out.println("=== Quadtree vs Linear Search Benchmark ===");
        System.out.println("Points indexed: " + N);
        System.out.println("Queries per trial: " + QUERIES + " (identical set for both methods)");
        System.out.println("Warmup trials (untimed): " + WARMUP_TRIALS);
        System.out.println("Timed trials: " + TIMED_TRIALS);
        System.out.println("Avg results per query: " + String.format("%.1f", avgHitsPerQuery)
                + " (" + String.format("%.2f", 100.0 * avgHitsPerQuery / N) + "% of points)");
        System.out.println();
        System.out.printf("Quadtree:      %.3f ms/trial  (stddev %.3f, %.4f ms/query)%n",
                quadMean, quadStd, quadMean / QUERIES);
        System.out.printf("Linear scan:   %.3f ms/trial  (stddev %.3f, %.4f ms/query)%n",
                linearMean, linearStd, linearMean / QUERIES);
        System.out.printf("Speedup (mean linear / mean quadtree): %.2fx%n", linearMean / quadMean);
        System.out.println();
        System.out.println("Correctness check: PASSED (both methods returned identical result sets for all "
                + QUERIES + " queries)");
        // Printed so the JIT can't prove the query loops are side-effect-free
        // and eliminate them as dead code.
        System.out.println("(checksum, ignore: " + resultChecksum + ")");
    }

    private record TrialResult(double elapsedMs, long totalHits) {
    }

    private static TrialResult runQuadtreeTrial(Quadtree<Integer> tree, List<BoundingBox> queries) {
        long hits = 0;
        long start = System.nanoTime();
        for (BoundingBox range : queries) {
            hits += tree.query(range, new ArrayList<>()).size();
        }
        long end = System.nanoTime();
        return new TrialResult((end - start) / 1_000_000.0, hits);
    }

    private static TrialResult runLinearTrial(List<Coordinates> points, List<BoundingBox> queries) {
        long hits = 0;
        long start = System.nanoTime();
        for (BoundingBox range : queries) {
            for (Coordinates c : points) {
                if (range.contains(c)) {
                    hits++;
                }
            }
        }
        long end = System.nanoTime();
        return new TrialResult((end - start) / 1_000_000.0, hits);
    }

    /**
     * Confirms both methods return the same set of point indices for every
     * query, before any timing is trusted. Aborts the benchmark (via
     * assertion-style exception) on the first mismatch, since a speed
     * comparison between a correct and an incorrect implementation is
     * meaningless.
     */
    private static void verifyCorrectness(Quadtree<Integer> tree, List<Coordinates> points,
            List<BoundingBox> queries) {
        for (int qi = 0; qi < queries.size(); qi++) {
            BoundingBox range = queries.get(qi);

            Set<Integer> quadResult = new HashSet<>();
            for (QuadNode<Integer> node : tree.query(range, new ArrayList<>())) {
                quadResult.add(node.getData());
            }

            Set<Integer> linearResult = new HashSet<>();
            for (int i = 0; i < points.size(); i++) {
                if (range.contains(points.get(i))) {
                    linearResult.add(i);
                }
            }

            if (!quadResult.equals(linearResult)) {
                throw new IllegalStateException(
                        "Correctness check FAILED on query " + qi + ": quadtree and linear scan "
                                + "disagree. Quadtree returned " + quadResult.size() + " points, "
                                + "linear scan returned " + linearResult.size() + " points. "
                                + "Aborting — a timing comparison against a wrong result is meaningless.");
            }
        }
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double stddev(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        return Math.sqrt(sumSq / values.length);
    }
}