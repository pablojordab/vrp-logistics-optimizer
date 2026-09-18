package main;

import domain.Coordinates;
import domain.PrimitiveRouteData;
import domain.Shipment;
import domain.TimeWindow;
import domain.Vehicle;
import routing.GraphHopperManager;
import service.AgentEstimatorService;
import service.ClusteringService;
import service.CostMatrixCalculator;
import service.GeoJsonSerializer;
import service.VRPSolverService;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("STARTING VRP ENTERPRISE LOGISTICS ENGINE");
        System.out.println("==================================================");

        System.out.println("[1/6] Booting GraphHopper engine (Monaco)...");
        GraphHopperManager ghManager = new GraphHopperManager(
                Path.of("src/main/resources/monaco-latest.osm.pbf"),
                Path.of("graphhopper-cache")
        );

        System.out.println("[2/6] Generating logistics demand...");
        TimeWindow workingHours = new TimeWindow(8, 18);
        Coordinates depot = new Coordinates(43.7314, 7.4190);
        Vehicle standardVan = new Vehicle("VAN-BASE", 150, depot, workingHours);

        List<Shipment> dailyShipments = generateRandomShipmentsMonaco(30, workingHours);
        System.out.println("      -> Created " + dailyShipments.size() + " shipments.");

        System.out.println("[3/6] Executing Spatial Intelligence (Clustering)...");
        AgentEstimatorService estimator = new AgentEstimatorService();
        int requiredVehicles = estimator.estimateRequiredAgents(dailyShipments, standardVan);

        ClusteringService clusterer = new ClusteringService();
        List<ClusteringService.Cluster> deliveryZones = clusterer.createClusters(dailyShipments, requiredVehicles, standardVan);
        System.out.println("      -> City divided into " + deliveryZones.size() + " zones for " + requiredVehicles + " vans.");

        CostMatrixCalculator matrixCalculator = new CostMatrixCalculator(50);
        VRPSolverService solver = new VRPSolverService();
        Map<String, List<Coordinates>> fleetRoutes = new LinkedHashMap<>();

        System.out.println("[4/6 & 5/6] Solving routes and stitching road geometries...");
        for (int i = 0; i < deliveryZones.size(); i++) {
            String vehicleId = "VAN-" + (i + 1);
            ClusteringService.Cluster zone = deliveryZones.get(i);

            PrimitiveRouteData flattenedData = PrimitiveRouteData.flatten(depot, zone.assignedShipments);
            long[][] timeMatrix = matrixCalculator.calculateTimeMatrix(flattenedData, ghManager);

            List<Coordinates> stopSequence = solver.solveOptimalRoute(flattenedData, timeMatrix);

            List<Coordinates> fullRoadGeometry = new ArrayList<>();
            for (int s = 0; s < stopSequence.size() - 1; s++) {
                List<Coordinates> segment = ghManager.getDetailedPathPoints(stopSequence.get(s), stopSequence.get(s + 1));
                if (!fullRoadGeometry.isEmpty() && !segment.isEmpty()) {
                    segment = segment.subList(1, segment.size());
                }
                fullRoadGeometry.addAll(segment);
            }

            fleetRoutes.put(vehicleId, fullRoadGeometry);
            System.out.println("      -> Optimized " + vehicleId + " (" + zone.assignedShipments.size() + " stops)");
        }

        System.out.println("[6/6] Generating GeoJSON fleet file...");
        String geoJsonOutput = GeoJsonSerializer.fleetToGeoJson(fleetRoutes);
        String outputFile = "route_monaco_fleet.json";
        saveToFile(outputFile, geoJsonOutput);

        ghManager.close();
        System.out.println("==================================================");
        System.out.println("PROCESS COMPLETED SUCCESSFULLY!");
        System.out.println("File created: " + outputFile);
        System.out.println("Visualize it at: https://geojson.io");
        System.out.println("==================================================");
    }

    private static List<Shipment> generateRandomShipmentsMonaco(int quantity, TimeWindow window) {
        List<Shipment> shipments = new ArrayList<>();
        Random rand = new Random(42);

        for (int i = 0; i < quantity; i++) {
            double lat = 43.7300 + (rand.nextDouble() * 0.0150);
            double lon = 7.4100 + (rand.nextDouble() * 0.0250);
            int weight = 10 + rand.nextInt(20);

            shipments.add(new Shipment("PKG-" + i, new Coordinates(lat, lon), weight, window));
        }
        return shipments;
    }

    private static void saveToFile(String fileName, String content) {
        try (FileWriter file = new FileWriter(fileName)) {
            file.write(content);
            System.out.println("      -> Saved: " + fileName);
        } catch (IOException e) {
            System.err.println("      -> Error writing file: " + e.getMessage());
        }
    }
}