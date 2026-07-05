package service;

import domain.Coordinates;
import domain.PrimitiveRouteData;
import domain.RouteMetrics;
import routing.GraphHopperManager;

public class CostMatrixCalculator {

    private final long[][] matrixPool;

    public CostMatrixCalculator(int maxNodesPerCluster) {
        this.matrixPool = new long[maxNodesPerCluster][maxNodesPerCluster];
    }

    public long[][] calculateTimeMatrix(PrimitiveRouteData data, GraphHopperManager ghManager) {
        int n = data.size;

        for (int i = 0; i < n; i++) 
            Coordinates from = new Coordinates(data.latitudes[i], data.longitudes[i]);

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrixPool[i][j] = 0;
                } else {
                    Coordinates to = new Coordinates(data.latitudes[j], data.longitudes[j]);
                    try {
                        RouteMetrics metrics = ghManager.getRoute(from, to);
                        
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