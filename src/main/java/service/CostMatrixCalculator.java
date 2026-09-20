package service;

import domain.PrimitiveRouteData;
import domain.RouteMetrics;
import routing.GraphHopperManager;

public class CostMatrixCalculator {

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
                        matrix[i][j] = 999999L;
                    }
                }
            }
        }
        return matrix;
    }
}