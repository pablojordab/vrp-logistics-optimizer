package routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import domain.Coordinates;
import domain.RouteMetrics;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphHopperManagerTest {

    @Test
    void testGraphHopperInitializationWithMonacoMap(@TempDir Path tempCacheDir) {

        URL mapUrl = getClass().getClassLoader().getResource("monaco-latest.osm.pbf");
        assertNotNull(mapUrl, "Critical Error: monaco-latest.osm.pbf not found in src/main/resources/");

        Path osmPath = assertDoesNotThrow(() -> Paths.get(mapUrl.toURI()));

        GraphHopperManager manager = assertDoesNotThrow(() -> new GraphHopperManager(osmPath, tempCacheDir));
        assertNotNull(manager);
        manager.close();
    }

    @Test
    void testRealRouteCalculationsBetweenPoints(@TempDir Path tempCacheDir){
        URL mapUrl = getClass().getClassLoader().getResource("monaco-latest.osm.pbf");
        Path osmPath = assertDoesNotThrow(() -> Paths.get(mapUrl.toURI()));

        GraphHopperManager manager = new GraphHopperManager(osmPath, tempCacheDir);

        Coordinates casino = new Coordinates(43.7393, 7.4281);
        Coordinates heliport = new Coordinates(43.7259, 7.4191);

        System.out.println(">>> Querying road metrics from Casino to Heliport...");
        RouteMetrics metrics = manager.getRoute(casino, heliport);

        assertNotNull(metrics, "The returned metrics should never be null for connected points.");
        System.out.println(">>> [SUCCESS] Road Distance: " + metrics.distanceMeters() + " meters");
        System.out.println(">>> [SUCCESS] Estimated Time: " + metrics.timeSeconds() + " seconds (" + (metrics.timeSeconds() / 60) + " mins)");

        assertTrue(metrics.distanceMeters() > 1500, "Road distance should be reasonably long.");
        assertTrue(metrics.distanceMeters() < 5000, "Road distance shouldn't exceed geographical bounds of Monaco.");
        assertTrue(metrics.timeSeconds() > 0, "Travel time must be strictly positive.");

        manager.close();
    }
}