package es.jyago.hermes.location;

import es.jyago.hermes.location.detail.LocationLogDetail;
import es.jyago.hermes.person.Person;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Logger;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;


@Named("locationLogController")
@SessionScoped
public class LocationLogController implements Serializable {

    private static final Logger LOG = Logger.getLogger(LocationLogController.class.getName());

    private LocationLog selectedLocationLog;
    private List<LocationLog> locationLogList;

    @Inject
    private LocationLogFacade locationLogFacade;
    private Person person;

    public LocationLogController() {
        locationLogList = null;
        selectedLocationLog = null;
        locationLogList = null;
    }

    public void setPerson(Person person) {
        this.person = person;
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

    private double analyzePKE(LocationLogDetail lld, LocationLogDetail lldPrev) {
        // Convertimos los Km/h en m/s.
        double currentSpeedMS = lld.getSpeed() / 3.6d;
        double previousSpeedMS = lldPrev.getSpeed() / 3.6d;

        double speedDifference = currentSpeedMS - previousSpeedMS;
        // Analizamos la diferencia de velocidad.
        if (speedDifference > 0.0d) {
            // Si la diferencia de velocidades es positiva, se tiene en cuenta para el sumatorio.
            return Math.pow(currentSpeedMS, 2) - Math.pow(previousSpeedMS, 2);
        }

        return 0.0d;
    }

    private LocationLogFacade getFacade() {
        return locationLogFacade;
    }
}
