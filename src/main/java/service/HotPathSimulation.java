package service;

import domain.PrimitiveRouteData;
import routing.GraphHopperManager;

import java.nio.file.Path;

public class HotPathSimulation {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("1. Starting GraphHopper motor (Monaco)...");
        GraphHopperManager ghManager = new GraphHopperManager(
                Path.of("src/main/resources/monaco-latest.osm.pbf"),
                Path.of("graphhopper-cache")
        );

        int nPackages = 30;

        System.out.println("2. Preparing DOD flattened data (" + nPackages + " packages)...");
        PrimitiveRouteData data = new PrimitiveRouteData(nPackages);
        for (int i = 0; i < nPackages; i++) {
            data.latitudes[i] = 43.7384 + (i * 0.0001);
            data.longitudes[i] = 7.4246 + (i * 0.0001);
            data.demands[i] = 5;
            data.originalIds[i] = "SIM-" + i;
        }

        CostMatrixCalculator calculator = new CostMatrixCalculator(nPackages);

        System.out.println("3. JIT Warmup (5 matrices)...");
        for (int i = 0; i < 5; i++) {
            calculator.calculateTimeMatrix(data, ghManager);
        }

        System.out.println("\n>>> TIENES 10 SEGUNDOS PARA IR A VISUALVM Y HACER DOBLE CLIC EN EL PROCESO <<<");
        Thread.sleep(10000);

        long durationMs = 3 * 60 * 1000;
        System.out.println("4. Starting Hot Path stress test for 3 minutes...");
        
        long start = System.currentTimeMillis();
        int completedMatrices = 0;

        while (System.currentTimeMillis() - start < durationMs) {
            calculator.calculateTimeMatrix(data, ghManager);
            completedMatrices++;
        }

        long end = System.currentTimeMillis();
        long totalTime = end - start;
        double avgPerMatrix = (double) totalTime / completedMatrices;
        long totalQueries = (long) completedMatrices * (nPackages * nPackages);

        System.out.println("--------------------------------------------------");
        System.out.println("✅ Test finished!");
        System.out.println("Total matrices calculated: " + completedMatrices);
        System.out.println("Total GraphHopper route queries: " + totalQueries);
        System.out.println("Total time: " + (totalTime / 1000) + " seconds");
        System.out.println("Average per matrix: " + avgPerMatrix + " ms");
        System.out.println("--------------------------------------------------");

        ghManager.close();
    }
}