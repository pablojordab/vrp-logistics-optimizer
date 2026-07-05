package service;

import domain.PrimitiveRouteData;
import routing.GraphHopperManager;

import java.nio.file.Path;

public class HotPathSimulation {

    public static void main(String[] args) {
        System.out.println("1. Arrancando motor GraphHopper (Mónaco)...");
        GraphHopperManager ghManager = new GraphHopperManager(
                Path.of("src/main/resources/monaco-latest.osm.pbf"),
                Path.of("graphhopper-cache")
        );

        System.out.println("2. Preparando datos aplanados DOD (10 paquetes)...");
        PrimitiveRouteData data = new PrimitiveRouteData(10);
        for (int i = 0; i < 10; i++) {
            data.latitudes[i] = 43.7384 + (i * 0.0001);
            data.longitudes[i] = 7.4246 + (i * 0.0001);
            data.demands[i] = 5;
        }

        CostMatrixCalculator calculator = new CostMatrixCalculator(10);

        System.out.println("3. Iniciando test de estrés (5.000 matrices)...");
        long start = System.currentTimeMillis();

        for (int i = 0; i < 5000; i++) {
            calculator.calculateTimeMatrix(data, ghManager);
            
            if (i % 1000 == 0 && i > 0) {
                System.out.println("   Calculadas " + i + " matrices...");
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("¡Test superado! Tiempo total: " + (end - start) + " ms");

        ghManager.close();
    }
}