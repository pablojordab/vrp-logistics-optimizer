package service;

import domain.PrimitiveRouteData;
import domain.RouteMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import routing.GraphHopperManager;

public class CostMatrixCalculator {

    private static final Logger log = LoggerFactory.getLogger(CostMatrixCalculator.class);

    /**
     * Sentinel cost used when GraphHopper cannot find a route between two
     * nodes (e.g. one falls outside the routable road network). It is large
     * enough that OR-Tools will never prefer this edge over a real one, but
     * every use is logged (see {@link #calculateTimeMatrix}) so a systematic
     * routing problem shows up as WARN spam instead of a silently biased
     * solution.
     */
    private static final long UNREACHABLE_PENALTY_SECONDS = 999_999L;

    private final int maxNodes;

    public CostMatrixCalculator(int maxNodesPerCluster) {
        this.maxNodes = maxNodesPerCluster;
    }

    /**
     * Builds a travel-time matrix sized exactly to {@code data.size}.
     *
     * <p>{@code maxNodesPerCluster} is only an upper bound used to fail fast
     * on oversized zones; it must never determine the returned matrix's
     * dimensions, or callers (like {@code VRPSolverService}) that validate
     * {@code timeMatrix.length == data.size} will reject every zone whose
     * size differs from {@code maxNodesPerCluster}.</p>
     */
    public long[][] calculateTimeMatrix(PrimitiveRouteData data, GraphHopperManager ghManager) {
        int n = data.size;

        if (n > maxNodes) {
            throw new IllegalArgumentException("Data size (" + n + ") exceeds matrix pool capacity (" + maxNodes + ")");
        }

        long[][] matrix = new long[n][n];
        int unreachablePairs = 0;

        for (int i = 0; i < n; i++) {
            double fromLat = data.latitudes[i];
            double fromLon = data.longitudes[i];

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 0L;
                } else {
                    try {
                        RouteMetrics metrics = ghManager.getRoute(
                                fromLat,
                                fromLon,
                                data.latitudes[j],
                                data.longitudes[j]
                        );
                        matrix[i][j] = metrics.timeSeconds();
                    } catch (Exception e) {
                        matrix[i][j] = UNREACHABLE_PENALTY_SECONDS;
                        unreachablePairs++;
                        log.warn(
                                "No route found between '{}' ({}, {}) and '{}' ({}, {}); using fallback cost of {}s. "
                                        + "Cause: {}",
                                data.originalIds[i], fromLat, fromLon,
                                data.originalIds[j], data.latitudes[j], data.longitudes[j],
                                UNREACHABLE_PENALTY_SECONDS, e.getMessage()
                        );
                    }
                }
            }
        }

        if (unreachablePairs > 0) {
            log.warn(
                    "{} of {} node pairs in this zone had no GraphHopper route and used the fallback cost; "
                            + "the resulting route may be biased around those nodes.",
                    unreachablePairs, n * (n - 1)
            );
        }

        return matrix;
    }
}