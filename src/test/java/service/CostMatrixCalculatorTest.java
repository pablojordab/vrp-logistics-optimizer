package service;

import domain.Coordinates;
import domain.PrimitiveRouteData;
import domain.Shipment;
import domain.TimeWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import routing.GraphHopperManager;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CostMatrixCalculatorTest {

    @Test
    void matrixSizeMatchesZoneSizeNotPoolCapacity(@TempDir Path tempCacheDir) {

        URL mapUrl = getClass().getClassLoader().getResource("monaco-latest.osm.pbf");
        assertNotNull(mapUrl, "Critical Error: monaco-latest.osm.pbf not found in src/main/resources/");

        Path osmPath = assertDoesNotThrow(() -> Paths.get(mapUrl.toURI()));
        GraphHopperManager ghManager = new GraphHopperManager(osmPath, tempCacheDir);

        Coordinates depot = new Coordinates(43.7314, 7.4190);
        TimeWindow window = new TimeWindow(8 * 60 * 60, 18 * 60 * 60);

        List<Shipment> shipments = List.of(
                new Shipment("PKG-1", new Coordinates(43.7320, 7.4200), 10, window),
                new Shipment("PKG-2", new Coordinates(43.7330, 7.4210), 10, window),
                new Shipment("PKG-3", new Coordinates(43.7340, 7.4220), 10, window)
        );

        PrimitiveRouteData data = PrimitiveRouteData.flatten(depot, shipments);

        CostMatrixCalculator calculator = new CostMatrixCalculator(50);
        long[][] matrix = calculator.calculateTimeMatrix(data, ghManager);

        assertEquals(
                data.size,
                matrix.length,
                "Matrix row count must match the zone size, not the pool capacity."
        );

        for (long[] row : matrix) {
            assertEquals(
                    data.size,
                    row.length,
                    "Matrix column count must match the zone size, not the pool capacity."
            );
        }

        for (int i = 0; i < data.size; i++) {
            assertEquals(0L, matrix[i][i]);
        }

        ghManager.close();
    }
}