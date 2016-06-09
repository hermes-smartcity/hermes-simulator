package es.jyago.hermes.location;

import es.jyago.hermes.person.Person;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;


@Named("locationLogController")
@ApplicationScoped
public class LocationLogController implements Serializable {

    private static final Logger LOG = Logger.getLogger(LocationLogController.class.getName());

    private LocationLog selectedLocationLog;
    private List<LocationLog> locationLogList;

    public LocationLogController() {
        locationLogList = null;
        selectedLocationLog = null;
        locationLogList = null;
    }

    public LocationLog getSelectedLocationLog() {
        return selectedLocationLog;
    }

    public void setSelectedLocationLog(LocationLog selectedLocationLog) {
        this.selectedLocationLog = selectedLocationLog;
    }

    public List<LocationLog> getLocationLogList() {
        return locationLogList;
    }
}
