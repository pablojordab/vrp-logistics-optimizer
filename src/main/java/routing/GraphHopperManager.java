package routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.util.CustomModel;

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

    public void close(){
        this.hopper.close();
    }
}
