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

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {

    private static final String DEFAULT_OSM_PATH = "src/main/resources/monaco-latest.osm.pbf";
    private static final String DEFAULT_CACHE_DIR = "graphhopper-cache";
    private static final String DEFAULT_OUTPUT_FILE = "route_monaco_fleet.json";
    private static final int DEFAULT_SHIPMENT_COUNT = 30;
    private static final long DEFAULT_SEED = 42L;

    public static void main(String[] args) {
        Path shipmentsCsv = (args.length > 0) ? Path.of(args[0]) : null;

        runPipeline(
                Path.of(DEFAULT_OSM_PATH),
                Path.of(DEFAULT_CACHE_DIR),
                DEFAULT_OUTPUT_FILE,
                DEFAULT_SHIPMENT_COUNT,
                DEFAULT_SEED,
                shipmentsCsv
        );
    }

    /**
     * Convenience overload for callers (like existing tests) that always want
     * randomly generated demand and never pass a shipments file.
     */
    static Map<String, List<Coordinates>> runPipeline(
            Path osmPath,
            Path cacheDir,
            String outputFile,
            int shipmentCount,
            long seed) {

        return runPipeline(osmPath, cacheDir, outputFile, shipmentCount, seed, null);
    }

    /**
     * Runs the full pipeline end to end: boots GraphHopper, generates or loads
     * demand, clusters shipments into zones, solves each zone's route respecting
     * time windows, stitches the real road geometry, and writes the GeoJSON file.
     *
     * <p>Pulled out of {@code main} specifically so an integration test can
     * exercise this exact sequence — including the CostMatrixCalculator /
     * VRPSolverService seam that previously threw {@code IllegalArgumentException}
     * on every zone whose size differed from the matrix pool capacity — without
     * spawning a subprocess or parsing console output.</p>
     *
     * @param shipmentsCsv path to a CSV file of shipments (see
     *                      {@link #loadShipmentsFromCsv}), or {@code null} to
     *                      fall back to {@code shipmentCount} randomly
     *                      generated shipments around Monaco.
     * @return each vehicle's final road geometry, keyed by vehicle id, so
     *         callers (tests included) can assert on the actual result rather
     *         than just "it didn't throw".
     */
    static Map<String, List<Coordinates>> runPipeline(
            Path osmPath,
            Path cacheDir,
            String outputFile,
            int shipmentCount,
            long seed,
            Path shipmentsCsv) {

        System.out.println("==================================================");
        System.out.println("STARTING VRP ENTERPRISE LOGISTICS ENGINE");
        System.out.println("==================================================");

        System.out.println("[1/6] Booting GraphHopper engine (Monaco)...");
        GraphHopperManager ghManager = new GraphHopperManager(osmPath, cacheDir);

        /*
         * TimeWindow is expressed in seconds.
         *
         * 08:00 = 8 * 60 * 60 = 28800 seconds
         * 18:00 = 18 * 60 * 60 = 64800 seconds
         */
        TimeWindow workingHours = new TimeWindow(
                8 * 60 * 60,
                18 * 60 * 60
        );

        Coordinates depot =
                new Coordinates(43.7314, 7.4190);

        Vehicle standardVan =
                new Vehicle(
                        "VAN-BASE",
                        150,
                        depot,
                        workingHours
                );

        List<Shipment> dailyShipments;

        if (shipmentsCsv != null) {
            System.out.println("[2/6] Loading logistics demand from " + shipmentsCsv + "...");
            dailyShipments = loadShipmentsFromCsv(shipmentsCsv, workingHours);
        } else {
            System.out.println("[2/6] Generating logistics demand...");
            dailyShipments =
                    generateRandomShipmentsMonaco(
                            shipmentCount,
                            workingHours,
                            seed
                    );
        }

        System.out.println(
                "      -> Created "
                        + dailyShipments.size()
                        + " shipments."
        );

        System.out.println(
                "[3/6] Executing Spatial Intelligence (Clustering)..."
        );

        AgentEstimatorService estimator =
                new AgentEstimatorService();

        int requiredVehicles =
                estimator.estimateRequiredAgents(
                        dailyShipments,
                        standardVan
                );

        ClusteringService clusterer =
                new ClusteringService();

        List<ClusteringService.Cluster> deliveryZones =
                clusterer.createClusters(
                        dailyShipments,
                        requiredVehicles,
                        standardVan
                );

        System.out.println(
                "      -> City divided into "
                        + deliveryZones.size()
                        + " zones for "
                        + requiredVehicles
                        + " vans."
        );

        CostMatrixCalculator matrixCalculator =
                new CostMatrixCalculator(50);

        VRPSolverService solver =
                new VRPSolverService();

        Map<String, List<Coordinates>> fleetRoutes =
                new LinkedHashMap<>();

        System.out.println(
                "[4/6 & 5/6] Solving routes and stitching road geometries..."
        );

        for (int i = 0; i < deliveryZones.size(); i++) {

            String vehicleId =
                    "VAN-" + (i + 1);

            ClusteringService.Cluster zone =
                    deliveryZones.get(i);

            /*
             * Flatten:
             *
             * index 0 -> depot
             * index 1 -> shipment 1
             * index 2 -> shipment 2
             * ...
             */
            PrimitiveRouteData flattenedData =
                    PrimitiveRouteData.flatten(
                            depot,
                            zone.assignedShipments
                    );

            /*
             * Calculate real road travel times using GraphHopper.
             */
            long[][] timeMatrix =
                    matrixCalculator.calculateTimeMatrix(
                            flattenedData,
                            ghManager
                    );

            /*
             * Build the TimeWindow list in exactly the same
             * order as PrimitiveRouteData.
             *
             * index 0 -> depot
             * index 1 -> first shipment
             * index 2 -> second shipment
             * ...
             */

            List<TimeWindow> timeWindows =
                    new ArrayList<>();

            // Depot working hours
            timeWindows.add(workingHours);

            // Shipment time windows
            for (Shipment shipment : zone.assignedShipments) {
                timeWindows.add(
                        shipment.timeWindow()
                );
            }

            /*
             * Solve the route respecting the TimeWindows.
             */
            List<Coordinates> stopSequence =
                    solver.solveOptimalRoute(
                            flattenedData,
                            timeMatrix,
                            timeWindows
                    );

            /*
             * Reconstruct the real road geometry.
             */
            List<Coordinates> fullRoadGeometry =
                    new ArrayList<>();

            for (int s = 0;
                 s < stopSequence.size() - 1;
                 s++) {

                List<Coordinates> segment =
                        ghManager.getDetailedPathPoints(
                                stopSequence.get(s),
                                stopSequence.get(s + 1)
                        );

                if (!fullRoadGeometry.isEmpty()
                        && !segment.isEmpty()) {

                    segment =
                            segment.subList(
                                    1,
                                    segment.size()
                            );
                }

                fullRoadGeometry.addAll(segment);
            }

            fleetRoutes.put(
                    vehicleId,
                    fullRoadGeometry
            );

            System.out.println(
                    "      -> Optimized "
                            + vehicleId
                            + " ("
                            + zone.assignedShipments.size()
                            + " stops)"
            );
        }

        System.out.println(
                "[6/6] Generating GeoJSON fleet file..."
        );

        String geoJsonOutput =
                GeoJsonSerializer.fleetToGeoJson(
                        fleetRoutes
                );

        saveToFile(
                outputFile,
                geoJsonOutput
        );

        ghManager.close();

        System.out.println(
                "=================================================="
        );
        System.out.println(
                "PROCESS COMPLETED SUCCESSFULLY!"
        );
        System.out.println(
                "File created: " + outputFile
        );
        System.out.println(
                "Visualize it at: https://geojson.io"
        );
        System.out.println(
                "=================================================="
        );

        return fleetRoutes;
    }

    /**
     * Loads shipments from a CSV file so the pipeline can be tried against a
     * real or hand-crafted delivery list without touching any Java code.
     *
     * <p>Expected format, one shipment per row after a header row:</p>
     * <pre>
     * id,lat,lon,demand[,windowStartSeconds,windowEndSeconds]
     * PKG-1,43.7320,7.4200,10
     * PKG-2,43.7330,7.4210,15,28800,50400
     * </pre>
     *
     * <p>The last two columns are optional per row; when omitted,
     * {@code defaultWindow} is used for that shipment (so a whole file can
     * skip them and rely on one working-hours window for everyone).</p>
     */
    private static List<Shipment> loadShipmentsFromCsv(Path csvPath, TimeWindow defaultWindow) {
        List<Shipment> shipments = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            reader.readLine(); // skip header row

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue;
                }

                String[] cols = line.split(",");
                if (cols.length < 4) {
                    throw new IllegalArgumentException(
                            "Line " + lineNumber + " in " + csvPath
                                    + " needs at least 4 columns (id,lat,lon,demand): " + line
                    );
                }

                String id = cols[0].trim();
                double lat = Double.parseDouble(cols[1].trim());
                double lon = Double.parseDouble(cols[2].trim());
                int demand = Integer.parseInt(cols[3].trim());

                TimeWindow window = defaultWindow;
                if (cols.length >= 6) {
                    int windowStart = Integer.parseInt(cols[4].trim());
                    int windowEnd = Integer.parseInt(cols[5].trim());
                    window = new TimeWindow(windowStart, windowEnd);
                }

                shipments.add(new Shipment(id, new Coordinates(lat, lon), demand, window));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shipments from " + csvPath, e);
        }

        if (shipments.isEmpty()) {
            throw new IllegalArgumentException("No shipments found in " + csvPath);
        }

        return shipments;
    }

    private static List<Shipment> generateRandomShipmentsMonaco(
            int quantity,
            TimeWindow window,
            long seed) {

        List<Shipment> shipments =
                new ArrayList<>();

        Random rand =
                new Random(seed);

        for (int i = 0; i < quantity; i++) {

            double lat =
                    43.7300
                            + (rand.nextDouble() * 0.0150);

            double lon =
                    7.4100
                            + (rand.nextDouble() * 0.0250);

            int weight =
                    10 + rand.nextInt(20);

            shipments.add(
                    new Shipment(
                            "PKG-" + i,
                            new Coordinates(lat, lon),
                            weight,
                            window
                    )
            );
        }

        return shipments;
    }

    private static void saveToFile(
            String fileName,
            String content) {

        try (FileWriter file =
                     new FileWriter(fileName)) {

            file.write(content);

            System.out.println(
                    "      -> Saved: "
                            + fileName
            );

        } catch (IOException e) {

            System.err.println(
                    "      -> Error writing file: "
                            + e.getMessage()
            );
        }
    }
}