package service;

import domain.BoundingBox;
import domain.Coordinates;
import domain.RouteMetrics;
import routing.GraphHopperManager;
import spatial.QuadNode;
import spatial.Quadtree;

import java.util.ArrayList;
import java.util.List;


public class SpatialRoutingService<T> {

    private final Quadtree<T> quadtree;
    private final GraphHopperManager routingManager;

    private static final double SEARCH_RADIUS_DEGREES = 0.05;

    public SpatialRoutingService(Quadtree<T> quadtree, GraphHopperManager routingManager) {
        this.quadtree = quadtree;
        this.routingManager = routingManager;
    }

    public QuadNode<T> findFastestDestination(Coordinates origin) {
        BoundingBox searchBox = new BoundingBox(
                origin.lat() - SEARCH_RADIUS_DEGREES,
                origin.lon() - SEARCH_RADIUS_DEGREES,
                origin.lat() + SEARCH_RADIUS_DEGREES,
                origin.lon() + SEARCH_RADIUS_DEGREES
        );

        List<QuadNode<T>> candidates = quadtree.query(searchBox, new ArrayList<>());

        if (candidates.isEmpty()) {
            return null;
        }

        QuadNode<T> bestDestination = null;
        long bestTimeMs = Long.MAX_VALUE;

        for (QuadNode<T> candidate : candidates) {
            try {
                RouteMetrics metrics = routingManager.getRoute(origin, candidate.getPoint());

                if (metrics.timeSeconds() < bestTimeMs) {
                    bestTimeMs = metrics.timeSeconds();
                    bestDestination = candidate;
                }
            } catch (Exception e) {
                System.err.println("Cannot be calculated route to candidate: " + e.getMessage());
            }
        }

        return bestDestination;
    }
}
