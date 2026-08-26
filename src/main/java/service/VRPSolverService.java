package service;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import com.google.protobuf.Duration;
import domain.Coordinates;
import domain.PrimitiveRouteData;

import java.util.ArrayList;
import java.util.List;

public class VRPSolverService {

    static {
        Loader.loadNativeLibraries();
    }

    public List<Coordinates> solveOptimalRoute(PrimitiveRouteData data, long[][] timeMatrix) {
        RoutingIndexManager manager = new RoutingIndexManager(data.size, 1, 0);
        RoutingModel routing = new RoutingModel(manager);

        final int transitCallbackIndex = routing.registerTransitCallback((long fromIndex, long toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            return timeMatrix[fromNode][toNode];
        });

        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

        RoutingSearchParameters searchParameters =
                main.defaultRoutingSearchParameters()
                        .toBuilder()
                        .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                        .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                        .setTimeLimit(Duration.newBuilder().setSeconds(2).build())
                        .build();

        Assignment solution = routing.solveWithParameters(searchParameters);

        if (solution == null) {
            throw new RuntimeException("OR-Tools could not find a valid solution");
        }

        return extractRouteCoordinates(manager, routing, solution, data);
    }

    private List<Coordinates> extractRouteCoordinates(
            RoutingIndexManager manager, 
            RoutingModel routing, 
            Assignment solution, 
            PrimitiveRouteData data) {
        
        List<Coordinates> optimalRoute = new ArrayList<>();
        long index = routing.start(0);
        
        while (!routing.isEnd(index)) {
            int nodeIndex = manager.indexToNode(index);
            optimalRoute.add(new Coordinates(data.latitudes[nodeIndex], data.longitudes[nodeIndex]));
            index = solution.value(routing.nextVar(index));
        }
        
        int lastNodeIndex = manager.indexToNode(index);
        optimalRoute.add(new Coordinates(data.latitudes[lastNodeIndex], data.longitudes[lastNodeIndex]));
        
        return optimalRoute;
    }
}