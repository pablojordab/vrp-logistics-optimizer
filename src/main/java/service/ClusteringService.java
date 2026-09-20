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
 * centroids. For every shipment we query a small bounding box around its
 * location first instead of scanning every existing cluster; the box only
 * grows (and, as a last resort, falls back to a full scan) if nothing
 * feasible is found nearby. With S shipments and K clusters this turns the
 * naive O(S * K) assignment scan into roughly O(S * log K) once K grows past
 * a handful of vehicles, since each query only visits the quadrants that
 * actually overlap the search box.</p>
 *
 * <p><b>Why the returned cluster count can exceed {@code k}:</b> {@code k}
 * (normally {@link AgentEstimatorService}'s estimate) only seeds the initial
 * clusters; it is a starting point, not a hard cap. If every existing
 * cluster — including after a full scan — is already at capacity for a given
 * shipment, this class opens one more "emergency" cluster rather than either
 * dropping the shipment or overloading a vehicle past
 * {@link Vehicle#capacity()}. This is intentional: a fleet that occasionally
 * needs one extra van is a much safer failure mode than one that silently
 * overloads a van. Each emergency cluster is logged as a {@code WARN} so the
 * gap between the estimate and the actual zone count is visible, not
 * silent.</p>
 */
public class ClusteringService {

    private static final Logger log = LoggerFactory.getLogger(ClusteringService.class);

    private static final double INITIAL_SEARCH_RADIUS_DEGREES = 0.02; // ~2 km at these latitudes
    private static final int MAX_RADIUS_DOUBLINGS = 10;

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
         * it as the centroid drifts. That never breaks correctness —
         * {@code findNearestFeasibleCluster} always falls back to a full scan
         * when the radius search comes up empty — but a cluster whose
         * centroid has drifted far from its indexed position may occasionally
         * be missed by the initial radius search and only found on that
         * fallback scan, at some extra (still correct) computational cost.</p>
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

        for (Shipment shipment : sortedShipments) {
            Cluster bestCluster = findNearestFeasibleCluster(shipment, spatialIndex, clusters, fleetProfile);

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
     * Finds the closest cluster with spare capacity for {@code shipment} using
     * the quadtree to prune the search to a small, growing neighborhood before
     * ever resorting to scanning every cluster in the fleet.
     */
    private Cluster findNearestFeasibleCluster(Shipment shipment, Quadtree<Cluster> spatialIndex,
                                                List<Cluster> allClusters, Vehicle fleetProfile) {
        Coordinates origin = shipment.location();
        double radius = INITIAL_SEARCH_RADIUS_DEGREES;

        for (int attempt = 0; attempt < MAX_RADIUS_DOUBLINGS; attempt++) {
            BoundingBox searchBox = new BoundingBox(
                    clampLat(origin.lat() - radius), clampLon(origin.lon() - radius),
                    clampLat(origin.lat() + radius), clampLon(origin.lon() + radius)
            );

            List<QuadNode<Cluster>> candidates = spatialIndex.query(searchBox, new ArrayList<>());
            Cluster best = pickBestFeasible(origin, candidates.stream().map(QuadNode::getData).toList(),
                    shipment.demand(), fleetProfile.capacity());

            if (best != null) {
                return best;
            }
            radius *= 2;
        }

        // Local search exhausted (sparse index, or every cluster nearby is full):
        // fall back to a full scan so correctness never depends on radius tuning.
        return pickBestFeasible(origin, allClusters, shipment.demand(), fleetProfile.capacity());
    }

    private Cluster pickBestFeasible(Coordinates origin, List<Cluster> candidates, int demand, int capacity) {
        Cluster bestCluster = null;
        double bestDistance = Double.MAX_VALUE;

        for (Cluster cluster : candidates) {
            if (cluster.currentLoad + demand <= capacity) {
                double dist = origin.distanceSquaredTo(cluster.centroid);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    bestCluster = cluster;
                }
            }
        }
        return bestCluster;
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