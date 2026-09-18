package service;

import domain.PrimitiveRouteData;
import domain.RouteMetrics;
import routing.GraphHopperManager;

public class CostMatrixCalculator {

    private final long[][] matrixPool;
    private final int maxNodes;

    public CostMatrixCalculator(int maxNodesPerCluster) {
        this.maxNodes = maxNodesPerCluster;
        this.matrixPool = new long[maxNodesPerCluster][maxNodesPerCluster];
    }

    public long[][] calculateTimeMatrix(PrimitiveRouteData data, GraphHopperManager ghManager) {
        int n = data.size;

        if (n > maxNodes) {
            throw new IllegalArgumentException("Data size (" + n + ") exceeds matrix pool capacity (" + maxNodes + ")");
        }

        for (int i = 0; i < n; i++) {
            double fromLat = data.latitudes[i];
            double fromLon = data.longitudes[i];

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrixPool[i][j] = 0L;
                } else {
                    try {
                        RouteMetrics metrics = ghManager.getRoute(
                                fromLat,
                                fromLon,
                                data.latitudes[j],
                                data.longitudes[j]
                        );
                        matrixPool[i][j] = metrics.timeSeconds();
                    } catch (Exception e) {
                        matrixPool[i][j] = 999999L;
                    }
                }
            }
        }
        return matrixPool;
    }
}