package service;

import domain.PrimitiveRouteData;
import routing.GraphHopperManager;

import java.nio.file.Path;

public class HotPathSimulation {

    public static void main(String[] args) {
        System.out.println("1. Starting GraphHopper motor (Monaco)");
        GraphHopperManager ghManager = new GraphHopperManager(
                Path.of("src/main/resources/monaco-latest.osm.pbf"),
                Path.of("graphhopper-cache")
        );

        int N = 100;
        System.out.println("2. Preparing DOD flattened data (" + N + " packages)...");
        PrimitiveRouteData data = new PrimitiveRouteData(N);
        for (int i = 0; i < N; i++) {
            data.latitudes[i] = 43.7384 + (i * 0.0001);
            data.longitudes[i] = 7.4246 + (i * 0.0001);
            data.demands[i] = 5;
        }

        CostMatrixCalculator calculator = new CostMatrixCalculator(N);

        System.out.println("3. Starting stress test (100 matrices)...");
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            calculator.calculateTimeMatrix(data, ghManager);
            
            if (i % 20 == 0 && i > 0) {
                System.out.println("   Matrices " + i + " calculated...");
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("Test passed! Total time: " + (end - start) + " ms");

        ghManager.close();
    }
}