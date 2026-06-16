package routing;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.util.CustomModel;

import domain.Coordinates;
import domain.RouteMetrics;

import java.nio.file.Path;

public class GraphHopperManager {

    private final GraphHopper hopper;

    public GraphHopperManager(Path osmFilePath, Path cacheDirectory) {
        this.hopper = new GraphHopper();

        this.hopper.setOSMFile(osmFilePath.toString());
        this.hopper.setGraphHopperLocation(cacheDirectory.toString());

        // FIX: GraphHopper 8.0 requiere CustomModel para evitar calles prohibidas.
        this.hopper.setProfiles(new Profile("car")
                .setVehicle("car")
                .setWeighting("custom")
                .setCustomModel(new CustomModel()));

        this.hopper.importOrLoad();
    }

    public RouteMetrics getRoute(Coordinates from, Coordinates to) {
        GHRequest request = new GHRequest(
                from.lat(), from.lon(),
                to.lat(), to.lon()
        ).setProfile("car");

        GHResponse response = hopper.route(request);

        if (response.hasErrors()) {
            throw new RuntimeException("Routing failed: " + response.getErrors().get(0).getMessage());
        }

        ResponsePath path = response.getBest();

        int timeInSeconds = (int) (path.getTime() / 1000);
        double distanceInMeters = path.getDistance();

        return new RouteMetrics(distanceInMeters, timeInSeconds);
    }

    public void close(){
        this.hopper.close();
    }
}
