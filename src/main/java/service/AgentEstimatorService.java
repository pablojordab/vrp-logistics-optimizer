package service;

import domain.Shipment;
import domain.Vehicle;

import java.util.List;

public class AgentEstimatorService {

    public int estimateRequiredAgents(List<Shipment> shipments, Vehicle fleetProfile) {
        if (shipments == null || shipments.isEmpty()) {
            return 0;
        }

        double totalDemand = shipments.stream()
                .mapToDouble(Shipment::demand)
                .sum();

        double vehicleCapacity = fleetProfile.capacity();

        if (vehicleCapacity <= 0) {
            throw new IllegalArgumentException("The vehicle's capacity must be above 0");
        }

        double requiredVehicles = totalDemand / vehicleCapacity;

        return (int) Math.ceil(requiredVehicles);
    }
}