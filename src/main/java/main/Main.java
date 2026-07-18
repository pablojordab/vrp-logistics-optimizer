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
import java.util.List;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("🚀 STARTING VRP ENTERPRISE LOGISTICS ENGINE 🚀");
        System.out.println("==================================================");

        System.out.println("[1/6] Booting GraphHopper engine (Monaco)...");
        GraphHopperManager ghManager = new GraphHopperManager(
                Path.of("src/main/resources/monaco-latest.osm.pbf"),
                Path.of("graphhopper-cache")
        );

        System.out.println("[2/6] Generating logistics demand...");
        TimeWindow workingHours = new TimeWindow(8, 18);
        Coordinates depot = new Coordinates(43.7314, 7.4190);
        Vehicle standardVan = new Vehicle("V-001", 1000, depot, workingHours);

        List<Shipment> dailyShipments = generateRandomShipmentsMonaco(25, workingHours);
        System.out.println("      -> Created " + dailyShipments.size() + " shipments for delivery.");

        System.out.println("[3/6] Executing Spatial Intelligence (Clustering)...");
        AgentEstimatorService estimator = new AgentEstimatorService();
        int requiredVehicles = estimator.estimateRequiredAgents(dailyShipments, standardVan);
        
        ClusteringService clusterer = new ClusteringService();
        List<ClusteringService.Cluster> deliveryZones = clusterer.createClusters(dailyShipments, requiredVehicles, standardVan);
        System.out.println("      -> City divided into " + deliveryZones.size() + " optimal zones.");

        System.out.println("[4/6] Extracting Cost Matrices (Data-Oriented Design)...");
        CostMatrixCalculator matrixCalculator = new CostMatrixCalculator(50);

        ClusteringService.Cluster zone1 = deliveryZones.get(0);
        
        PrimitiveRouteData flattenedData = PrimitiveRouteData.flatten(depot, zone1.assignedShipments);
        
        long[][] timeMatrix = matrixCalculator.calculateTimeMatrix(flattenedData, ghManager);

        System.out.println("[5/6] Calculating Optimal Route with Google OR-Tools...");
        VRPSolverService solver = new VRPSolverService();
        List<Coordinates> optimalRoute = solver.solveOptimalRoute(flattenedData, timeMatrix);

        System.out.println("[6/6] Generating GeoJSON file for the visualizer...");
        String geoJsonOutput = GeoJsonSerializer.routeToGeoJson(optimalRoute, standardVan.id());

        saveToFile("route_monaco_" + standardVan.id() + ".json", geoJsonOutput);

        ghManager.close();
        System.out.println("==================================================");
        System.out.println("✅ PROCESS COMPLETED SUCCESSFULLY!");
        System.out.println("🗺️  Open 'index.html' and load the generated file.");
        System.out.println("==================================================");
    }

    private static List<Shipment> generateRandomShipmentsMonaco(int quantity, TimeWindow window) {
        List<Shipment> shipments = new ArrayList<>();
        Random rand = new Random(42);

        for (int i = 0; i < quantity; i++) {
            double lat = 43.7300 + (rand.nextDouble() * 0.0150);
            double lon = 7.4100 + (rand.nextDouble() * 0.0250);
            int weight = 10 + rand.nextInt(40);

            shipments.add(new Shipment("PKG-" + i, new Coordinates(lat, lon), weight, window));
        }
        return shipments;
    }

    private static void saveToFile(String fileName, String content) {
        try (FileWriter file = new FileWriter(fileName)) {
            file.write(content);
            System.out.println("      -> File saved successfully: " + fileName);
        } catch (IOException e) {
            System.err.println("      -> Error saving file: " + e.getMessage());
        }
    }
}