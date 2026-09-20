package service;

import domain.Coordinates;
import domain.Shipment;
import domain.TimeWindow;
import domain.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClusteringServiceTest {

    @Test
    void testAgentEstimationAndClustering() {

        TimeWindow dummyWindow = new TimeWindow(8, 18); 
        Coordinates depot = new Coordinates(0.0, 0.0);

        Vehicle van = new Vehicle("V-001", 1000, depot, dummyWindow);

        Shipment s1 = new Shipment("P1", new Coordinates(40.41, -3.70), 500, dummyWindow); // Madrid (Pesado)
        Shipment s2 = new Shipment("P2", new Coordinates(41.38, 2.15), 400, dummyWindow);  // Barcelona (Pesado)
        Shipment s3 = new Shipment("P3", new Coordinates(40.42, -3.71), 300, dummyWindow); // Madrid (Ligero)
        Shipment s4 = new Shipment("P4", new Coordinates(41.39, 2.16), 200, dummyWindow);  // Barcelona (Ligero)

        List<Shipment> todayShipments = Arrays.asList(s1, s2, s3, s4);

        AgentEstimatorService estimator = new AgentEstimatorService();
        int requiredVans = estimator.estimateRequiredAgents(todayShipments, van);

        assertEquals(2, requiredVans, "We should need exactly 2 trucks for 1400kg");

        ClusteringService clusterer = new ClusteringService();
        List<ClusteringService.Cluster> barrios = clusterer.createClusters(todayShipments, requiredVans, van);

        assertEquals(2, barrios.size(), "Exactly 2 neighborhoods should have been created");

        for (ClusteringService.Cluster barrio : barrios) {
            assertTrue(barrio.currentLoad <= van.capacity(),
                    "A neighborhood has exceeded the van's capacity! Load: " + barrio.currentLoad);

            assertFalse(barrio.assignedShipments.isEmpty(), "There is an empty neighborhood with no packages.");
        }

        int totalAssigned = barrios.stream().mapToInt(b -> b.assignedShipments.size()).sum();
        assertEquals(4, totalAssigned, "All 4 packages must be assigned to a neighborhood");

        // Spatial correctness: the quadtree-backed nearest-cluster search must still
        // group Madrid shipments together and Barcelona shipments together.
        ClusteringService.Cluster madridCluster = clusterOf(barrios, "P1");
        ClusteringService.Cluster barcelonaCluster = clusterOf(barrios, "P2");

        assertEquals(madridCluster, clusterOf(barrios, "P3"), "P1 and P3 (Madrid) should share a cluster");
        assertEquals(barcelonaCluster, clusterOf(barrios, "P4"), "P2 and P4 (Barcelona) should share a cluster");
        assertNotEquals(madridCluster, barcelonaCluster, "Madrid and Barcelona should not be merged");
    }

    @Test
    void testClusteringHoldsAtScaleWithSpatialIndex() {
        TimeWindow dummyWindow = new TimeWindow(8, 18);
        Coordinates depot = new Coordinates(43.7314, 7.4190);
        Vehicle van = new Vehicle("VAN-BASE", 150, depot, dummyWindow);

        java.util.Random rand = new java.util.Random(42);
        List<Shipment> shipments = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            double lat = 43.7300 + (rand.nextDouble() * 0.0150);
            double lon = 7.4100 + (rand.nextDouble() * 0.0250);
            int weight = 10 + rand.nextInt(20);
            shipments.add(new Shipment("PKG-" + i, new Coordinates(lat, lon), weight, dummyWindow));
        }

        AgentEstimatorService estimator = new AgentEstimatorService();
        int requiredVans = estimator.estimateRequiredAgents(shipments, van);

        ClusteringService clusterer = new ClusteringService();
        List<ClusteringService.Cluster> clusters = clusterer.createClusters(shipments, requiredVans, van);

        int totalAssigned = clusters.stream().mapToInt(c -> c.assignedShipments.size()).sum();
        assertEquals(shipments.size(), totalAssigned, "Every shipment must end up in exactly one cluster");

        for (ClusteringService.Cluster cluster : clusters) {
            assertTrue(cluster.currentLoad <= van.capacity(),
                    "A cluster exceeded van capacity: " + cluster.currentLoad);
        }
    }

    @Test
    void centroidRecentersToTheMeanOfAssignedShipments() {
        TimeWindow dummyWindow = new TimeWindow(8, 18);
        Coordinates depot = new Coordinates(0.0, 0.0);
        Vehicle van = new Vehicle("V-001", 100, depot, dummyWindow);

        // Highest demand first so it's the one used to seed the (only) cluster;
        // both must still fit in the same single cluster (k = 1).
        Shipment s1 = new Shipment("P1", new Coordinates(10.0, 20.0), 15, dummyWindow);
        Shipment s2 = new Shipment("P2", new Coordinates(12.0, 24.0), 10, dummyWindow);

        ClusteringService clusterer = new ClusteringService();
        List<ClusteringService.Cluster> clusters = clusterer.createClusters(List.of(s1, s2), 1, van);

        assertEquals(1, clusters.size(), "Both shipments should fit in the single seeded cluster");

        ClusteringService.Cluster cluster = clusters.get(0);
        double expectedLat = (10.0 + 12.0) / 2;
        double expectedLon = (20.0 + 24.0) / 2;

        assertEquals(expectedLat, cluster.centroid.lat(), 1e-9,
                "Centroid latitude should be the mean of both shipment latitudes, not the seed's own location");
        assertEquals(expectedLon, cluster.centroid.lon(), 1e-9,
                "Centroid longitude should be the mean of both shipment longitudes, not the seed's own location");
    }

    private static ClusteringService.Cluster clusterOf(List<ClusteringService.Cluster> clusters, String shipmentId) {
        return clusters.stream()
                .filter(c -> c.assignedShipments.stream().anyMatch(s -> s.id().equals(shipmentId)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Shipment not found in any cluster: " + shipmentId));
    }
}