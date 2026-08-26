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
    }
}