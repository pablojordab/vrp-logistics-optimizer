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
    void testSolverRespectsDifferentTimeWindows() {

        Coordinates depot =
                new Coordinates(43.7384, 7.4246);

        List<Shipment> shipments = List.of(

                new Shipment(
                        "S1",
                        new Coordinates(43.7400, 7.4250),
                        5,
                        new TimeWindow(
                                8 * 60 * 60,
                                8 * 60 * 60 + 20 * 60
                        )
                ),

                new Shipment(
                        "S2",
                        new Coordinates(43.7420, 7.4300),
                        10,
                        new TimeWindow(
                                8 * 60 * 60 + 40 * 60,
                                9 * 60 * 60
                        )
                ),

                new Shipment(
                        "S3",
                        new Coordinates(43.7440, 7.4350),
                        15,
                        new TimeWindow(
                                9 * 60 * 60 + 20 * 60,
                                9 * 60 * 60 + 40 * 60
                        )
                )
        );

        PrimitiveRouteData data =
                PrimitiveRouteData.flatten(
                        depot,
                        shipments
                );

        long[][] syntheticTimeMatrix = {

                // Depot  S1    S2    S3
                {    0L,  600L,  900L, 1200L }, // Depot
                {  600L,    0L,  600L,  900L }, // S1
                {  900L,  600L,    0L,  600L }, // S2
                { 1200L,  900L,  600L,    0L }  // S3
        };

        List<TimeWindow> timeWindows = List.of(

                // Depot: 08:00 - 18:00
                new TimeWindow(
                        8 * 60 * 60,
                        18 * 60 * 60
                ),

                // S1: 08:00 - 08:20
                new TimeWindow(
                        8 * 60 * 60,
                        8 * 60 * 60 + 20 * 60
                ),

                // S2: 08:40 - 09:00
                new TimeWindow(
                        8 * 60 * 60 + 40 * 60,
                        9 * 60 * 60
                ),

                // S3: 09:20 - 09:40
                new TimeWindow(
                        9 * 60 * 60 + 20 * 60,
                        9 * 60 * 60 + 40 * 60
                )
        );

        VRPSolverService solver =
                new VRPSolverService();

        List<Coordinates> route =
                solver.solveOptimalRoute(
                        data,
                        syntheticTimeMatrix,
                        timeWindows
                );

        assertNotNull(route);

        assertEquals(5, route.size());

        assertEquals(
                depot,
                route.get(0)
        );

        // End at depot
        assertEquals(
                depot,
                route.get(route.size() - 1)
        );

        assertEquals(
                shipments.get(0).location(),
                route.get(1)
        );

        assertEquals(
                shipments.get(1).location(),
                route.get(2)
        );

        assertEquals(
                shipments.get(2).location(),
                route.get(3)
        );
    }
}