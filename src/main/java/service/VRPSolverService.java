package service;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingDimension;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import com.google.protobuf.Duration;
import domain.Coordinates;
import domain.PrimitiveRouteData;
import domain.TimeWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VRPSolverService {

    static {
        Loader.loadNativeLibraries();
    }

    /*
     * Maximum amount of waiting time allowed at a location.
     *
     * Example:
     * Vehicle arrives at 08:40
     * TimeWindow starts at 09:00
     * -> vehicle can wait 20 minutes.
     *
     * We use seconds because TimeWindow stores seconds.
     */
    private static final long MAX_WAITING_TIME_SECONDS =
            24 * 60 * 60;

    /*
     * Maximum duration of a complete route.
     */
    private static final long MAX_ROUTE_TIME_SECONDS =
            24 * 60 * 60;

    public List<Coordinates> solveOptimalRoute(
            PrimitiveRouteData data,
            long[][] timeMatrix,
            List<TimeWindow> timeWindows) {

        if (data == null || data.size == 0) {
            return Collections.emptyList();
        }

        if (timeMatrix == null ||
                timeMatrix.length != data.size) {

            throw new IllegalArgumentException(
                    "Time matrix size must match route data size."
            );
        }

        if (timeWindows == null ||
                timeWindows.size() != data.size) {

            throw new IllegalArgumentException(
                    "Number of time windows must match route data size."
            );
        }

        if (data.size == 1) {
            return List.of(
                    new Coordinates(
                            data.latitudes[0],
                            data.longitudes[0]
                    )
            );
        }

        /*
         * One cluster = one vehicle.
         */
        RoutingIndexManager manager =
                new RoutingIndexManager(
                        data.size,
                        1,
                        0
                );

        RoutingModel routing =
                new RoutingModel(manager);

        /*
         * ============================================================
         * 1. TRAVEL TIME CALLBACK
         * ============================================================
         */

        final int transitCallbackIndex =
                routing.registerTransitCallback(
                        (long fromIndex, long toIndex) -> {

                            int fromNode =
                                    manager.indexToNode(fromIndex);

                            int toNode =
                                    manager.indexToNode(toIndex);

                            return timeMatrix[fromNode][toNode];
                        }
                );

        /*
         * Minimize total travel time.
         */
        routing.setArcCostEvaluatorOfAllVehicles(
                transitCallbackIndex
        );

        /*
         * ============================================================
         * 2. TIME DIMENSION
         * ============================================================
         */

        boolean dimensionAdded =
                routing.addDimension(
                        transitCallbackIndex,
                        MAX_WAITING_TIME_SECONDS,
                        MAX_ROUTE_TIME_SECONDS,
                        false,
                        "Time"
                );

        if (!dimensionAdded) {
            throw new IllegalStateException(
                    "Could not create Time dimension."
            );
        }

        RoutingDimension timeDimension =
                routing.getMutableDimension("Time");

        /*
         * ============================================================
         * 3. APPLY TIME WINDOWS
         * ============================================================
         */

        for (int i = 0; i < data.size; i++) {

            long index;

            /*
             * Node 0 = depot.
             */
            if (i == 0) {
                index = routing.start(0);
            } else {
                index = manager.nodeToIndex(i);
            }

            TimeWindow window =
                    timeWindows.get(i);

            timeDimension
                    .cumulVar(index)
                    .setRange(
                            window.startSeconds(),
                            window.endSeconds()
                    );
        }

        /*
         * ============================================================
         * 4. MINIMIZE START AND END TIMES
         * ============================================================
         */

        routing.addVariableMinimizedByFinalizer(
                timeDimension.cumulVar(
                        routing.start(0)
                )
        );

        routing.addVariableMinimizedByFinalizer(
                timeDimension.cumulVar(
                        routing.end(0)
                )
        );

        /*
         * ============================================================
         * 5. SEARCH PARAMETERS
         * ============================================================
         */

        RoutingSearchParameters searchParameters =
                main.defaultRoutingSearchParameters()
                        .toBuilder()
                        .setFirstSolutionStrategy(
                                FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC
                        )
                        .setLocalSearchMetaheuristic(
                                LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH
                        )
                        .setTimeLimit(
                                Duration.newBuilder()
                                        .setSeconds(2)
                                        .build()
                        )
                        .build();

        /*
         * ============================================================
         * 6. SOLVE
         * ============================================================
         */

        Assignment solution =
                routing.solveWithParameters(
                        searchParameters
                );

        if (solution == null) {
            throw new IllegalStateException(
                    "OR-Tools could not find a valid route " +
                    "respecting the TimeWindows. " +
                    "Check the time windows, travel times and " +
                    "road network connectivity."
            );
        }

        /*
         * ============================================================
         * 7. PRINT CALCULATED TIMES
         * ============================================================
         */

        printRouteTimes(
                manager,
                routing,
                solution,
                timeDimension
        );

        /*
         * ============================================================
         * 8. EXTRACT ROUTE
         * ============================================================
         */

        return extractRouteCoordinates(
                manager,
                routing,
                solution,
                data
        );
    }

    /**
     * Prints the actual time assigned by OR-Tools
     * to each node in the solution.
     *
     * solution.min(cumulVar(index)) gives the concrete
     * lower bound of the cumulative time in the solution.
     */
    private void printRouteTimes(
            RoutingIndexManager manager,
            RoutingModel routing,
            Assignment solution,
            RoutingDimension timeDimension) {

        long index =
                routing.start(0);

        System.out.println(
                "      -> Calculated route times:"
        );

        while (!routing.isEnd(index)) {

            int nodeIndex =
                    manager.indexToNode(index);

            long arrivalTime =
                    solution.min(
                            timeDimension.cumulVar(index)
                    );

            System.out.println(
                    "         Node "
                            + nodeIndex
                            + " -> "
                            + formatTime(arrivalTime)
            );

            index =
                    solution.value(
                            routing.nextVar(index)
                    );
        }

        /*
         * Final depot.
         */
        long finalArrivalTime =
                solution.min(
                        timeDimension.cumulVar(index)
                );

        System.out.println(
                "         Depot -> "
                        + formatTime(finalArrivalTime)
        );
    }

    /**
     * Converts seconds from midnight into HH:mm.
     */
    private String formatTime(long seconds) {

        long hours =
                seconds / 3600;

        long minutes =
                (seconds % 3600) / 60;

        return String.format(
                "%02d:%02d",
                hours,
                minutes
        );
    }

    private List<Coordinates> extractRouteCoordinates(
            RoutingIndexManager manager,
            RoutingModel routing,
            Assignment solution,
            PrimitiveRouteData data) {

        List<Coordinates> optimalRoute =
                new ArrayList<>();

        long index =
                routing.start(0);

        while (!routing.isEnd(index)) {

            int nodeIndex =
                    manager.indexToNode(index);

            optimalRoute.add(
                    new Coordinates(
                            data.latitudes[nodeIndex],
                            data.longitudes[nodeIndex]
                    )
            );

            index =
                    solution.value(
                            routing.nextVar(index)
                    );
        }

        /*
         * Add the final depot.
         */
        int lastNodeIndex =
                manager.indexToNode(index);

        optimalRoute.add(
                new Coordinates(
                        data.latitudes[lastNodeIndex],
                        data.longitudes[lastNodeIndex]
                )
        );

        return optimalRoute;
    }
}