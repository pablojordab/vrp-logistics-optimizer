package main;

import domain.Coordinates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end regression test for the full pipeline wired up in {@link Main}.
 *
 * <p>Unlike the per-class unit tests (which mostly feed hand-built matrices
 * or fixed-size data into a single component), this test exercises the real
 * seam between {@code ClusteringService}, {@code CostMatrixCalculator} and
 * {@code VRPSolverService} with realistic, variably-sized zones — exactly
 * the integration point where the matrix-sizing bug
 * ({@code CostMatrixCalculator} always returning a fixed
 * {@code maxNodesPerCluster x maxNodesPerCluster} matrix instead of one sized
 * to the zone) went undetected. This test would have failed with that bug in
 * place.</p>
 */
class MainIntegrationTest {

    private static final String TEST_OUTPUT_FILE = "route_monaco_fleet_test.json";

    @AfterEach
    void cleanUpGeneratedFile() throws Exception {
        Files.deleteIfExists(Path.of(TEST_OUTPUT_FILE));
    }

    @Test
    void fullPipelineRunsEndToEndAndProducesAFleetRoute(@TempDir Path tempCacheDir) throws Exception {

        URL mapUrl = getClass().getClassLoader().getResource("monaco-latest.osm.pbf");
        assertNotNull(mapUrl, "Critical Error: monaco-latest.osm.pbf not found in src/main/resources/");
        Path osmPath = Paths.get(mapUrl.toURI());

        Map<String, List<Coordinates>> fleetRoutes = assertDoesNotThrow(
                () -> Main.runPipeline(osmPath, tempCacheDir, TEST_OUTPUT_FILE, 30, 42L),
                "The full pipeline (clustering -> cost matrix -> solver -> road geometry -> GeoJSON) "
                        + "must run without throwing for a realistic shipment set."
        );

        assertFalse(fleetRoutes.isEmpty(), "At least one vehicle route should be produced for 30 shipments.");

        for (Map.Entry<String, List<Coordinates>> entry : fleetRoutes.entrySet()) {
            assertFalse(
                    entry.getValue().isEmpty(),
                    "Vehicle '" + entry.getKey() + "' should have a non-empty road geometry."
            );
        }

        assertTrue(Files.exists(Path.of(TEST_OUTPUT_FILE)), "The pipeline should write the GeoJSON output file.");

        String geoJson = Files.readString(Path.of(TEST_OUTPUT_FILE));
        assertTrue(
                geoJson.contains("FeatureCollection"),
                "The generated file should be a valid GeoJSON FeatureCollection."
        );
    }

    @Test
    void pipelineHandlesZonesSmallerThanTheMatrixPoolCapacity(@TempDir Path tempCacheDir) throws Exception {

        // A small shipment count guarantees zones far smaller than
        // CostMatrixCalculator's internal maxNodesPerCluster (50), which is
        // precisely the mismatch that used to throw IllegalArgumentException.
        URL mapUrl = getClass().getClassLoader().getResource("monaco-latest.osm.pbf");
        Path osmPath = Paths.get(mapUrl.toURI());

        Map<String, List<Coordinates>> fleetRoutes = assertDoesNotThrow(
                () -> Main.runPipeline(osmPath, tempCacheDir, TEST_OUTPUT_FILE, 5, 7L),
                "Small zones (well under the matrix pool capacity) must not be rejected by the solver."
        );

        assertFalse(fleetRoutes.isEmpty());
    }

    @Test
    void pipelineLoadsShipmentsFromCsvInsteadOfRandomGeneration(@TempDir Path tempCacheDir) throws Exception {

        URL mapUrl = getClass().getClassLoader().getResource("monaco-latest.osm.pbf");
        Path osmPath = Paths.get(mapUrl.toURI());

        URL csvUrl = getClass().getClassLoader().getResource("sample-shipments.csv");
        assertNotNull(csvUrl, "Test fixture sample-shipments.csv not found in src/test/resources/");
        Path csvPath = Paths.get(csvUrl.toURI());

        Map<String, List<Coordinates>> fleetRoutes = assertDoesNotThrow(
                () -> Main.runPipeline(osmPath, tempCacheDir, TEST_OUTPUT_FILE, 30, 42L, csvPath),
                "The pipeline must accept shipments loaded from a CSV file, including rows that "
                        + "omit the optional time-window columns."
        );

        assertFalse(fleetRoutes.isEmpty(), "The 4 shipments in sample-shipments.csv should produce at least one route.");
    }
}