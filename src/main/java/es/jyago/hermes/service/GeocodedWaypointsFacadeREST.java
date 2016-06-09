package es.jyago.hermes.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import es.jyago.hermes.google.directions.GeocodedWaypoints;
import es.jyago.hermes.google.directions.Leg;
import es.jyago.hermes.google.directions.PolylineDecoder;
import es.jyago.hermes.google.directions.Route;
import es.jyago.hermes.google.directions.Location;
import es.jyago.hermes.location.LocationLog;
import es.jyago.hermes.location.detail.LocationLogDetail;
import es.jyago.hermes.person.Person;
import es.jyago.hermes.util.Util;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.joda.time.LocalTime;

/**
 * Ejemplo de URL para probar:
 * http://localhost:8080/HermesWeb/webresources/hermes.citizen.google/create
 * Pasándole el JSON correspondiente. Es útil el plugin de Chrome: Advanced REST
 * Client
 */
@Stateless
@Path("hermes.citizen.google")
public class GeocodedWaypointsFacadeREST {

    private static final Logger LOG = Logger.getLogger(GeocodedWaypointsFacadeREST.class.getName());

    public GeocodedWaypointsFacadeREST() {
    }

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(String json) {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        GeocodedWayPointsWrapper geocodedWayPointsWrapper = gson.fromJson(json, GeocodedWayPointsWrapper.class);
        if (geocodedWayPointsWrapper != null && geocodedWayPointsWrapper.getGeocodedWaypointsList() != null && !geocodedWayPointsWrapper.geocodedWaypointsList.isEmpty()) {
            LOG.log(Level.INFO, "create() - Trayectos recibidos del dispositivo: {0}", geocodedWayPointsWrapper.getDeviceId());
            if (!processGeocodedWayPoints(geocodedWayPointsWrapper.geocodedWaypointsList)) {
                // Ha habido algún problema al procesar los trayectos. Informamos a la otra parte.
                ObjectMapper mapper = new ObjectMapper();
                HashMap<String, String> r = new HashMap<>();
                r.put("message", "There has been some erroneous tracks");
                try {
                    return Response.ok(mapper.writeValueAsString(r), MediaType.APPLICATION_JSON).build();
                } catch (JsonProcessingException ex) {
                    LOG.log(Level.SEVERE, "create() - Error al generar el JSON", ex);
                }
            }
            return Response.ok().build();
        } else {
            return Response.noContent().build();
        }
    }

    private boolean processGeocodedWayPoints(List<GeocodedWaypoints> geocodedWaypointsList) {
        boolean ok = true;
        for (GeocodedWaypoints gcwp : geocodedWaypointsList) {
            try {
                // Creamos un usuario simulado, al que le asignaremos el trayecto.
                Person person = new Person();
                Date currentTime = new Date();
                String name = "Sim_" + currentTime.getTime();
                person.setFullName(name);

                LocationLog ll = new LocationLog();
                ll.setFilename(name);
                ll.setPerson(person);
                ll.setDateLog(currentTime);

                if (gcwp.getRoutes() != null) {
                    // Listado de posiciones que componen el trayecto de SmartDriver.
                    List<LocationLogDetail> locationLogDetailList = new ArrayList<>();
                    if (gcwp.getRoutes() != null && !gcwp.getRoutes().isEmpty()) {
                        Route r = gcwp.getRoutes().get(0);
                        // Comprobamos que traiga información de la ruta.
                        if (r.getLegs() != null) {
                            Leg l = r.getLegs().get(0);

                            double speed;
                            LocalTime localTime = new LocalTime();
                            ArrayList<Location> locationList = PolylineDecoder.decodePoly(r.getOverviewPolyline().getPoints());
                            Location previous = locationList.get(0);

                            // FIXME: ¿Interpolación de velocidades? Otra opción es consultar a Google Distance Matrix para consultar el tiempo que se tarda entre 2 puntos (le afecta el tráfico) y sacar la velocidad.
//                PolynomialFunction p = new PolynomialFunction(new double[]{speed, averagePolylineSpeed,});
                            for (int i = 0; i < locationList.size(); i++) {
                                Location location = locationList.get(i);

                                // Creamos un nodo del trayecto, como si usásemos SmartDriver.
                                LocationLogDetail lld = new LocationLogDetail();
                                lld.setLocationLog(ll);
                                lld.setLatitude(location.getLat());
                                lld.setLongitude(location.getLng());

                                // Calculamos la distancia entre los puntos previo y actual, así como el tiempo necesario para recorrer dicha distancia.
                                Double pointDistance = Util.distanceHaversine(previous.getLat(), previous.getLng(), location.getLat(), location.getLng());
                                Double pointDuration = l.getDuration().getValue() * pointDistance / l.getDistance().getValue();

                                // Convertimos la velocidad a Km/h.
                                speed = pointDuration > 0 ? pointDistance * 3.6 / pointDuration : 0.0d;
                                lld.setSpeed(speed);
                                // Añadimos los segundos correspondientes a la distancia recorrida entre puntos.
                                localTime = localTime.plusSeconds(pointDuration.intValue());
                                lld.setTimeLog(localTime.toDateTimeToday().toDate());

                                locationLogDetailList.add(lld);

                                // Asignamos el actual al anterior, para poder seguir calculando las distancias y tiempos respecto al punto previo.
                                previous = location;
                            }

                            // Asignamos el listado de posiciones.
                            ll.setLocationLogDetailList(locationLogDetailList);
                        }
                    }
                }
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "create() - Error al procesar el trayectos recibido", ex);
                ok = false;
            }
        }

        return ok;
    }

    // JYFR: Para usarlo con Jersey, la clase interna debe ser pública y estática.
    public static class GeocodedWayPointsWrapper {

        private String deviceId;
        private List<GeocodedWaypoints> geocodedWaypointsList;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public List<GeocodedWaypoints> getGeocodedWaypointsList() {
            return geocodedWaypointsList;
        }

        public void setGeocodedWaypointsList(List<GeocodedWaypoints> geocodedWaypointsList) {
            this.geocodedWaypointsList = geocodedWaypointsList;
        }
    }
}
