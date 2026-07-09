package service;

import domain.Coordinates;
import java.util.List;

public class GeoJsonSerializer {

    public static String routeToGeoJson(List<Coordinates> route, String vehicleId) {
        if (route == null || route.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"type\": \"Feature\",\n");
        sb.append("  \"properties\": {\n");
        sb.append("    \"vehicle\": \"").append(vehicleId).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"geometry\": {\n");
        sb.append("    \"type\": \"LineString\",\n");
        sb.append("    \"coordinates\": [\n");

        for (int i = 0; i < route.size(); i++) {
            Coordinates c = route.get(i);
            sb.append("      [").append(c.lon()).append(", ").append(c.lat()).append("]");
            
            if (i < route.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("    ]\n");
        sb.append("  }\n");
        sb.append("}");

        return sb.toString();
    }
}