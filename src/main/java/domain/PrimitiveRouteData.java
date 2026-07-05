package domain;

import java.util.List;

public class PrimitiveRouteData {
    
    public final int size;
    
    public final double[] latitudes;
    public final double[] longitudes;
    public final int[] demands;
    
    public final String[] originalIds;

    public PrimitiveRouteData(int size) {
        this.size = size;
        this.latitudes = new double[size];
        this.longitudes = new double[size];
        this.demands = new int[size];
        this.originalIds = new String[size];
    }

    public static PrimitiveRouteData flatten(Coordinates depot, List<Shipment> assignedShipments) {

        int totalNodes = assignedShipments.size() + 1;
        PrimitiveRouteData data = new PrimitiveRouteData(totalNodes);

        data.latitudes[0] = depot.lat();
        data.longitudes[0] = depot.lon();
        data.demands[0] = 0; 
        data.originalIds[0] = "DEPOT";

        for (int i = 0; i < assignedShipments.size(); i++) {
            Shipment s = assignedShipments.get(i);
            int arrayIndex = i + 1; 

            data.latitudes[arrayIndex] = s.location().lat();
            data.longitudes[arrayIndex] = s.location().lon();
            data.demands[arrayIndex] = s.demand();
            data.originalIds[arrayIndex] = s.id();
        }

        return data;
    }
}