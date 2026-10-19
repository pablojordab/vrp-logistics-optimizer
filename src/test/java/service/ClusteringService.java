package service;

import domain.BoundingBox;
import domain.Coordinates;
import domain.Shipment;
import domain.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spatial.QuadNode;
import spatial.Quadtree;

import java.util.ArrayList;
import java.util.List;

/**
 * "Cluster-first" phase of the cluster-first, route-second CVRP decomposition
 * used by this project: greedily assigns each shipment to the nearest cluster
 * that still has spare capacity, seeding one cluster per estimated vehicle.
 *
 * <p>Candidate lookup is backed by a {@link Quadtree} indexing cluster
 * centroids. For every shipment, {@link Quadtree#nearestFeasible} does a
 * single branch-and-bound search of the tree for the closest cluster whose
 * remaining capacity fits the shipment, instead of scanning every existing
 * cluster. This used to be done by hand here with a bounding box that grew
 * around the shipment until a feasible cluster turned up — but a box that
 * happens to contain *a* feasible cluster doesn't guarantee it's the
 * *nearest* one: a cluster just outside the box can be closer in straight-
 * line distance than one inside it, near a corner. {@code nearestFeasible}
 * doesn't have that gap: it prunes using the true minimum distance to each
 * quadrant's boundary, not box containment, so the result is always the
 * exact nearest feasible cluster (or {@code null} if none exists).</p>
 *
 * <p><b>Why the returned cluster count can exceed {@code k}:</b> {@code k}
 * (normally {@link AgentEstimatorService}'s estimate) only seeds the initial
 * clusters; it is a starting point, not a hard cap. If no existing cluster —
 * checked across the whole tree, not just nearby ones — has capacity for a
 * given shipment, this class opens one more "emergency" cluster rather than
 * either dropping the shipment or overloading a vehicle past
 * {@link Vehicle#capacity()}. This is intentional: a fleet that occasionally
 * needs one extra van is a much safer failure mode than one that silently
 * overloads a van. Each emergency cluster is logged as a {@code WARN} so the
 * gap between the estimate and the actual zone count is visible, not
 * silent.</p>
 */
public class ClusteringService {

    private static final Logger log = LoggerFactory.getLogger(ClusteringService.class);

    public static class Cluster {
        public Coordinates centroid;
        public int currentLoad;
        public List<Shipment> assignedShipments;

        /**
         * Count of shipments actually added via {@link #addShipment}, used to
         * compute the running mean centroid. Deliberately separate from
         * {@code assignedShipments.size()} isn't necessary here (they're kept
         * equal), but is named explicitly to make the incremental-mean math
         * below easy to follow.
         */
        private int shipmentCount;

        public Cluster(Coordinates centroid) {
            this.centroid = centroid;
            this.currentLoad = 0;
            this.assignedShipments = new ArrayList<>();
            this.shipmentCount = 0;
        }

        /**
         * Adds a shipment and recenters the centroid as the true running mean
         * of every shipment location assigned so far — not the placeholder
         * location passed to the constructor, which only exists so the
         * cluster has *some* location to seed the quadtree with before its
         * first real shipment arrives.
         *
         * <p><b>Note on the quadtree:</b> a cluster is indexed at whatever
         * centroid it had at insertion time, and this class never re-indexes
         * it as the centroid drifts. That never breaks correctness — the
         * tree still holds every cluster, so {@link Quadtree#nearestFeasible}
         * always finds the true nearest feasible one by exhaustively pruning
         * the whole tree when it has to — but a cluster whose centroid has
         * drifted far from its indexed position may force the search to
         * prune less effectively near it, at some extra (still correct)
         * computational cost.</p>
         */
        public void addShipment(Shipment s) {
            shipmentCount++;

            if (shipmentCount == 1) {
                // First real shipment: the centroid becomes exactly this
                // shipment's location, discarding the constructor's placeholder.
                this.centroid = s.location();
            } else {
                double newLat = centroid.lat() + (s.location().lat() - centroid.lat()) / shipmentCount;
                double newLon = centroid.lon() + (s.location().lon() - centroid.lon()) / shipmentCount;
                this.centroid = new Coordinates(newLat, newLon);
            }

            this.assignedShipments.add(s);
            this.currentLoad += s.demand();
        }
    }

    public List<Cluster> createClusters(List<Shipment> allShipments, int k, Vehicle fleetProfile) {
        List<Cluster> clusters = new ArrayList<>();
        if (allShipments == null || allShipments.isEmpty() || k <= 0) {
            return clusters;
        }

        List<Shipment> sortedShipments = new ArrayList<>(allShipments);
        sortedShipments.sort((s1, s2) -> Integer.compare(s2.demand(), s1.demand()));

        Quadtree<Cluster> spatialIndex = new Quadtree<>(computeBounds(sortedShipments));

        for (int i = 0; i < k && i < sortedShipments.size(); i++) {
            Cluster seed = new Cluster(sortedShipments.get(i).location());
            clusters.add(seed);
            spatialIndex.insert(new QuadNode<>(seed.centroid, seed));
        }

        int capacity = fleetProfile.capacity();

        for (Shipment shipment : sortedShipments) {
            int demand = shipment.demand();

            QuadNode<Cluster> nearest = spatialIndex.nearestFeasible(
                    shipment.location(),
                    candidate -> candidate.currentLoad + demand <= capacity
            );
            Cluster bestCluster = nearest == null ? null : nearest.getData();

            if (bestCluster != null) {
                bestCluster.addShipment(shipment);
            } else {
                Cluster emergencyCluster = new Cluster(shipment.location());
                emergencyCluster.addShipment(shipment);
                clusters.add(emergencyCluster);
                spatialIndex.insert(new QuadNode<>(emergencyCluster.centroid, emergencyCluster));

                log.warn(
                        "No existing cluster had capacity for shipment '{}' (demand={}); opened emergency cluster #{} "
                                + "beyond the {} seeded from the vehicle estimate. This keeps every van under its "
                                + "capacity limit at the cost of needing one more van than originally estimated.",
                        shipment.id(), shipment.demand(), clusters.size(), k
                );
            }
        }

        return clusters;
    }

    /**
     * Builds the quadtree's root boundary around the shipment set, padded so
     * points that fall exactly on the edge, and emergency clusters created at
     * a shipment's own coordinates, are always accepted by {@code insert}.
     */
    private BoundingBox computeBounds(List<Shipment> shipments) {
        double minLat = Double.MAX_VALUE, minLon = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (Shipment s : shipments) {
            Coordinates c = s.location();
            minLat = Math.min(minLat, c.lat());
            minLon = Math.min(minLon, c.lon());
            maxLat = Math.max(maxLat, c.lat());
            maxLon = Math.max(maxLon, c.lon());
        }

        double latPad = Math.max((maxLat - minLat) * 0.1, 0.01);
        double lonPad = Math.max((maxLon - minLon) * 0.1, 0.01);

        return new BoundingBox(
                clampLat(minLat - latPad), clampLon(minLon - lonPad),
                clampLat(maxLat + latPad), clampLon(maxLon + lonPad)
        );
    }

    private double clampLat(double lat) {
        return Math.max(-90.0, Math.min(90.0, lat));
    }

    private double clampLon(double lon) {
        return Math.max(-180.0, Math.min(180.0, lon));
    }
}