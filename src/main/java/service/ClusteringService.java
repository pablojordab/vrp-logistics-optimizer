package service;

import domain.Coordinates;
import domain.Shipment;
import domain.Vehicle;

import java.util.ArrayList;
import java.util.List;

public class ClusteringService {

    public static class Cluster {
        public Coordinates centroid;
        public int currentLoad;
        public List<Shipment> assignedShipments;

        public Cluster(Coordinates centroid) {
            this.centroid = centroid;
            this.currentLoad = 0;
            this.assignedShipments = new ArrayList<>();
        }

        public void addShipment(Shipment s) {
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

        for (int i = 0; i < k && i < sortedShipments.size(); i++) {
            clusters.add(new Cluster(sortedShipments.get(i).location()));
        }

        for (Shipment shipment : sortedShipments) {
            Cluster bestCluster = null;
            double bestDistance = Double.MAX_VALUE;

            for (Cluster cluster : clusters) {
                if (cluster.currentLoad + shipment.demand() <= fleetProfile.capacity()) {

                    double dist = calculateSquaredDistance(shipment.location(), cluster.centroid);

                    if (dist < bestDistance) {
                        bestDistance = dist;
                        bestCluster = cluster;
                    }
                }
            }

            if (bestCluster != null) {
                bestCluster.addShipment(shipment);
            } else {
                Cluster emergencyCluster = new Cluster(shipment.location());
                emergencyCluster.addShipment(shipment);
                clusters.add(emergencyCluster);
            }
        }

        return clusters;
    }

    private double calculateSquaredDistance(Coordinates a, Coordinates b) {
        double dLat = a.lat() - b.lat();
        double dLon = a.lon() - b.lon();
        return (dLat * dLat) + (dLon * dLon);
    }
}
