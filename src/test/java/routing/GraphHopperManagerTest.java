package routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GraphHopperManagerTest {

    @Test
    void testGraphHopperInitializationWithMonacoMap(@TempDir Path tempCacheDir) {

        URL mapUrl = getClass().getClassLoader().getResource("monaco-latest.osm.pbf");
        assertNotNull(mapUrl, "Critical Error: monaco-latest.osm.pbf not found in src/main/resources/");


        Path osmPath = assertDoesNotThrow(() -> Paths.get(mapUrl.toURI()),
                "URI conversion should not fail for valid resource paths.");

        System.out.println(">>> Starting GraphHopper ignition test with map: " + osmPath);
        System.out.println(">>> Compiling spatial graph cache at: " + tempCacheDir);

        GraphHopperManager manager = assertDoesNotThrow(() -> new GraphHopperManager(osmPath, tempCacheDir),
                "GraphHopper initialization failed. Check your profile configurations or map integrity.");

        assertNotNull(manager, "Manager instance must be successfully instantiated.");

        manager.close();
        System.out.println(">>> Success! GraphHopper initialized and closed gracefully with zero exceptions.");
    }
}