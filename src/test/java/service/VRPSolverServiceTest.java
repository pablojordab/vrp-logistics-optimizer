package service;

import domain.Coordinates;
import domain.PrimitiveRouteData;
import domain.Shipment;
import domain.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VRPSolverServiceTest {

    @Test
    void testSolverWithSyntheticMatrix() {
        Coordinates depot = new Coordinates(43.7384, 7.4246);
        List<Shipment> shipments = List.of(
                new Shipment("S1", new Coordinates(43.7400, 7.4250), 5, new TimeWindow(0, 100)),
                new Shipment("S2", new Coordinates(43.7420, 7.4300), 10, new TimeWindow(0, 100))
        );

        PrimitiveRouteData data = PrimitiveRouteData.flatten(depot, shipments);

        long[][] syntheticTimeMatrix = {
                {0L,  10L, 20L},
                {10L,  0L,  5L},
                {20L,  5L,  0L}
        };

        VRPSolverService solver = new VRPSolverService();
        List<Coordinates> route = solver.solveOptimalRoute(data, syntheticTimeMatrix);

        assertNotNull(route);
        assertEquals(4, route.size());
        assertEquals(depot, route.get(0));
        assertEquals(depot, route.get(route.size() - 1));
    }
}