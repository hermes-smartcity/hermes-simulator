package es.jyago.hermes.simulator;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import es.jyago.hermes.google.directions.Location;
import es.jyago.hermes.location.detail.LocationLogDetail;
import es.jyago.hermes.location.LocationLog;
import es.jyago.hermes.openStreetMap.PositionSimulatedSpeed;
import es.jyago.hermes.person.Person;
import es.jyago.hermes.util.Constants;
import es.jyago.hermes.util.HermesException;
import es.jyago.hermes.util.MessageBundle;
import es.jyago.hermes.util.Util;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.joda.time.LocalTime;
import org.primefaces.context.RequestContext;
import org.primefaces.event.map.OverlaySelectEvent;
import org.primefaces.model.map.DefaultMapModel;
import org.primefaces.model.map.LatLng;
import org.primefaces.model.map.MapModel;
import org.primefaces.model.map.Marker;
import org.primefaces.model.map.Polyline;

@Named("simulatorController")
@ApplicationScoped
public class SimulatorController implements Serializable {

    private static final Logger LOG = Logger.getLogger(SimulatorController.class.getName());

    private static final Location SEVILLE = new Location(37.3898358, -5.986069);
    public static final String MARKER_GREEN_CAR_ICON_PATH = "resources/img/greenCar.png";
    public static final String MARKER_YELLOW_CAR_ICON_PATH = "resources/img/yellowCar.png";
    public static final String MARKER_RED_CAR_ICON_PATH = "resources/img/redCar.png";
    private static final String MARKER_START_ICON_PATH = "resources/img/home.png";
    private static final String MARKER_FINISH_ICON_PATH = "resources/img/finish.png";

    private static final int RR_TIME = 850; // Equivale a una frecuencia cardíaca en reposo media (70 ppm).

    // Máximo retardo para iniciar la ruta, en milisegundos:
    private static final int MAX_INITIAL_DELAY = 60000;

    // Número de tramas de Ztreamy generadas.
    private static volatile int ztreamyObjectsCount = 0;
    // Número de errores contabilizados al enviar las tramas a Ztreamy, distintos de los 'no OK'.
    private static volatile int ztreamyErrors = 0;
    // Número de tramas enviadas a Ztreamy correctamente.
    private static volatile int zTreamyOkSends = 0;
    // Número de tramas enviadas a Ztreamy con recepción de 'no OK'.
    private static volatile int zTreamyNoOkSends = 0;
    // Número de tramas enviadas a Ztreamy con recepción de 'no OK' o erróneas, que se han podido reenviar.
    private static volatile int zTreamyRecovered = 0;
    // Número de tramas enviadas a Ztreamy que no se han podido reenviar porque ha terminado la simulación de cada trayecto.
    private static volatile int zTreamyFinallyPending = 0;
    // Número de hilos en ejecución
    private static volatile int runningThreads = 0;

    private Marker marker;

    // Distancia del trayecto.
    private static int distance = 10;
    // Distancia desde el centro de Sevilla.
    private static int distanceFromSevilleCenter = 1;
    // Número de trayectos a generar.
    private static int tracksAmount = 1;
    // Indicará si se intenta reeenvíar los datos a Ztreamy.
    static boolean retryOnFail = true;

    private static MapModel simulatedMapModel;
    private static ArrayList<LocationLog> locationLogList;

    private static Map<String, Timer> simulationTimers = null;
    private static int simulatedSmartDrivers = 1;
    private static long startSimulationTime = 0l;
    private static long endSimulationTime = 0l;

    @Inject
    @MessageBundle
    private ResourceBundle bundle;

    public SimulatorController() {
    }

    @PostConstruct
    public void init() {
        marker = new Marker(new LatLng(SEVILLE.getLat(), SEVILLE.getLng()));
        marker.setDraggable(false);

        // Generamos trayectos con los parámetros iniciales.
        generateSimulatedTracks();
    }

    public String getMarkerLatitudeLongitude() {
        if (marker != null) {
            return marker.getLatlng().getLat() + "," + marker.getLatlng().getLng();
        }

        return "";
    }

    public Marker getMarker() {
        return marker;
    }

    public void generateSimulatedTracks() {
        simulatedMapModel = new DefaultMapModel();
        // Eliminamos los 'Marker' existentes.
        simulatedMapModel.getMarkers().clear();
        locationLogList = new ArrayList<>();

        // Lista con las tareas de petición de rutas.
        List<Callable<String>> trackRequestTaskList = new ArrayList<>();

        // Crearemos tantas tareas como trayectos se quieran generar.
        for (int i = 0; i < tracksAmount; i++) {
            final Location destination = getRandomLocation(SEVILLE.getLat(), SEVILLE.getLng(), distanceFromSevilleCenter);
            final Location origin = getRandomLocation(destination.getLat(), destination.getLng(), distance);

            // Tarea para la petición de un trayecto.
            Callable callable = new Callable() {
                @Override
                public String call() {
                    String jsonTrack = null;
                    Location o = origin;
                    Location d = destination;
                    while (jsonTrack == null) {
                        try {
                            ///////////////////
                            // OPENSTREETMAP //
                            ///////////////////

                            // Las nuevas peticiones a OpenStreetMap tienen más densidad de puntos y se puede definir un factor de modificación de la velocidad de la vía.
                            // Generaremos factores de alteración de la velocidad de 0.5 a 2.0.
//                                double speedRandomFactor = 0.5d + (new Random().nextDouble() * 1.5d);
                            // TODO: (Muchas peticiones) Ver si es la mejor forma o si calculo yo las modificaciones de las velocidades. 
                            jsonTrack = IOUtils.toString(new URL("http://cronos.lbd.org.es/hermes/api/smartdriver/network/simulate?fromLat=" + o.getLat() + "&fromLng=" + o.getLng() + "&toLat=" + d.getLat() + "&toLng=" + d.getLng() + "&speedFactor=1.0"), "UTF-8");
                        } catch (IOException ex) {
                            LOG.log(Level.SEVERE, "generateSimulatedTracks() - OPENSTREETMAP - Error I/O: {0}", ex.getMessage());
                            // Generamos nuevos puntos aleatorios hasta que sean aceptados.
                            o = getRandomLocation(SEVILLE.getLat(), SEVILLE.getLng(), distanceFromSevilleCenter);
                            d = getRandomLocation(origin.getLat(), origin.getLng(), distance);
                        }
                    }

                    return jsonTrack;
                }
            };

            // Añadimos la tarea al listado de peticiones.            
            trackRequestTaskList.add(callable);
        }

        // Ejecutamos el listado de tareas, que se dividirá en los hilos y con las condiciones que haya configurados en 'TrackRequestWebService'.
        try {
            List<Future<String>> futureTaskList = TrackRequestWebService.submitAllTask(trackRequestTaskList);
            for (Future<String> future : futureTaskList) {
                LocationLog ll = new LocationLog();

                Date currentTime = new Date();

                // Creamos un objeto de localizaciones de 'SmartDriver'.
                ll.setDateLog(currentTime);

                // Procesamos el JSON de respuesta, en función de la plataforma a la que le hayamos hecho la petición.
                try {
                    String json = future.get();

                    ///////////////////
                    // OPENSTREETMAP //
                    ///////////////////
                    // Procesamos el JSON obtenido de OpenStreetMap con las localizaciones y las velocidades de SmartDriver.
                    Type listType = new TypeToken<ArrayList<PositionSimulatedSpeed>>() {
                    }.getType();
                    List<PositionSimulatedSpeed> pssList = new Gson().fromJson(json, listType);
                    createTrackOpenStreetMaps(pssList, ll);
                } catch (InterruptedException | ExecutionException | JsonSyntaxException ex) {
                    LOG.log(Level.SEVERE, "Error al decodificar el JSON de la ruta", ex);
                }

                // Si no fuera un trayecto válido, lo ignoramos y pasamos al siguiente
                if (ll.getLocationLogDetailList() == null || ll.getLocationLogDetailList().isEmpty()) {
                    continue;
                }

                // Creamos un usuario simulado, al que le asignaremos el trayecto.
                Person person = createSimPerson(currentTime.getTime());
                ll.setPerson(person);
                ll.setFilename(person.getFullName());

                locationLogList.add(ll);
            }
        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, "Error al obtener el JSON de la ruta", ex);
        } finally {
            // Paramos el mecanismo.
            TrackRequestWebService.shutdown();
        }

        LOG.log(Level.INFO, "generateSimulatedTracks() - Trayectos generados: {0}", locationLogList.size());
    }

    private Person createSimPerson(long currentTime) {
        Person person = new Person();
        String name = "Sim_" + currentTime;
        person.setFullName(name);
        person.setEmail(name + "@sim.com");

        return person;
    }

    public MapModel getSimulatedMapModel() {
        return simulatedMapModel;
    }

    private void createTrackOpenStreetMaps(List<PositionSimulatedSpeed> pssList, LocationLog ll) {
        if (pssList != null && !pssList.isEmpty()) {
            Polyline polyline = new Polyline();
            polyline.setStrokeWeight(4);
            polyline.setStrokeOpacity(0.7);

            Random rand = new Random();

            // Hacemos que las rutas sean variaciones de azul.
            polyline.setStrokeColor("#2222" + String.format("%02x", rand.nextInt(0x100)));

            // Listado de posiciones que componen el trayecto de SmartDriver.
            ArrayList<LocationLogDetail> locationLogDetailList = new ArrayList<>();

            // TODO: PONER en el marker !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            double trackDistance = 0.0d;
            int trackDurationInSeconds = 0;
            LocalTime localTime = new LocalTime();

            // Posición anterior en el trayecto.
            PositionSimulatedSpeed previous = pssList.get(0);

            // Analizamos la información obtenida de la consulta a OpenStreetMap.
            for (PositionSimulatedSpeed pss : pssList) {
                List<Double> currentCoordinates = pss.getPosition().getCoordinates();
                // Comprobamos que vengan las coordenadas.
                if (currentCoordinates == null || currentCoordinates.isEmpty() || currentCoordinates.size() < 2) {
                    continue;
                }

                // Añadimos un nuevo punto en la polilínea que se dibujará por pantalla.
                LatLng latlng = new LatLng(currentCoordinates.get(1), currentCoordinates.get(0));
                polyline.getPaths().add(latlng);

                // Creamos un nodo del trayecto, como si usásemos SmartDriver.
                LocationLogDetail lld = new LocationLogDetail();
                lld.setLocationLog(ll);
                lld.setLatitude(currentCoordinates.get(1)); // La posición 1 es la latitud.
                lld.setLongitude(currentCoordinates.get(0)); // La posición 0 es la longitud.
                lld.setSpeed(pss.getSpeed());
                lld.setRrTime(RR_TIME);
                lld.setHeartRate((int) Math.ceil(60.0d / (RR_TIME / 1000.0d)));

                // Si ha variado el límite de velocidad respecto al anterior, añadimos un 'marker' con el límite de velocidad.
                if (!previous.getSpeed().equals(pss.getSpeed())) {
                    Marker m = new Marker(latlng);
                    m.setVisible(true);
                    m.setDraggable(false);

                    m.setIcon("resources/img/" + (Math.round(pss.getSpeed())) + ".png");
                    simulatedMapModel.addOverlay(m);
                }

                List<Double> previousCoordinates = previous.getPosition().getCoordinates();
                // Calculamos la distancia en metros entre los puntos previo y actual, así como el tiempo necesario para recorrer dicha distancia.
                Double pointDistance = Util.distanceHaversine(previousCoordinates.get(1), previousCoordinates.get(0), currentCoordinates.get(1), currentCoordinates.get(0));
                trackDistance += pointDistance;

                // Convertimos los Km/h en m/s.
                double currentSpeedMS = lld.getSpeed() / 3.6d;

                // Añadimos los segundos correspondientes a la distancia recorrida entre puntos.
                int pointDuration = (int) Math.ceil(pointDistance / currentSpeedMS);
                // Añadimos los segundos correspondientes a la distancia recorrida entre puntos.
                localTime = localTime.plusSeconds(pointDuration);
                lld.setTimeLog(localTime.toDateTimeToday().toDate());
                // Indicamos cuántos segundos deben pasar para estar en esta posición.
                trackDurationInSeconds += pointDuration;
                lld.setSecondsToBeHere(trackDurationInSeconds);

                locationLogDetailList.add(lld);

                // Asignamos el actual al anterior, para poder seguir calculando las distancias y tiempos respecto al punto previo.
                previous = pss;
            }

            simulatedMapModel.addOverlay(polyline);

            // Asignamos un 'marker' con la posición inicial y final de cada trayecto.
            createStartAndEndMarkers(locationLogDetailList.get(0), locationLogDetailList.get(locationLogDetailList.size() - 1));

            // Asignamos el listado de posiciones.
            ll.setLocationLogDetailList(locationLogDetailList);

            ll.setDistance(trackDistance);
            ll.setDuration(trackDurationInSeconds);
        }
    }

    private void createStartAndEndMarkers(LocationLogDetail startPosition, LocationLogDetail endPosition) {
        LatLng startLatLng = new LatLng(startPosition.getLatitude(), startPosition.getLongitude());

        Marker startMarker = new Marker(startLatLng);
        startMarker.setVisible(true);
        startMarker.setDraggable(false);
//        startMarker.setTitle(startPosition.getMarkerTitle());
        startMarker.setIcon(MARKER_START_ICON_PATH);
        simulatedMapModel.addOverlay(startMarker);

        LatLng endLatLng = new LatLng(endPosition.getLatitude(), endPosition.getLongitude());
        Marker endMarker = new Marker(endLatLng);
        endMarker.setVisible(true);
        endMarker.setDraggable(false);
//        endMarker.setTitle(endPosition.getMarkerTitle());
        endMarker.setIcon(MARKER_FINISH_ICON_PATH);
        simulatedMapModel.addOverlay(endMarker);
    }

    private Location getRandomLocation(double latitude, double longitude, int radius) {
        Random random = new Random();

        // TODO: Comprobar que es una localización que no sea 'unnamed'
        // El radio se considerará en kilómetros. Lo convertimos a grados.
        double radiusInDegrees = radius / 111f;

        double u = random.nextDouble();
        double v = random.nextDouble();
        double w = radiusInDegrees * Math.sqrt(u);
        double t = 2 * Math.PI * v;
        double x = w * Math.cos(t);
        double y = w * Math.sin(t);

        double new_x = x / Math.cos(latitude);

        double foundLongitude = new_x + longitude;
        double foundLatitude = y + latitude;

        LOG.log(Level.FINE, "getRandomLocation() - Longitud: {0}, Latitud: {1}", new Object[]{foundLongitude, foundLatitude});

        Location result = new Location();
        result.setLat(foundLatitude);
        result.setLng(foundLongitude);

        return result;
    }

    public void onMarkerSelect(OverlaySelectEvent event) {
        try {
            marker = (Marker) event.getOverlay();
            if (marker != null) {
                // FIXME: Ver si se puede añadir salto de línea. No funciona '\n' ni '<br/>'
                String sb = marker.getTitle();
                marker.setTitle(sb);
            }
        } catch (ClassCastException ex) {
        }
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int d) {
        distance = d;
    }

    public int getDistanceFromSevilleCenter() {
        return distanceFromSevilleCenter;
    }

    public void setDistanceFromSevilleCenter(int dfsc) {
        distanceFromSevilleCenter = dfsc;
    }

    public int getTracksAmount() {
        return tracksAmount;
    }

    public void setTracksAmount(int ta) {
        tracksAmount = ta;
    }

    public boolean isRetryOnFail() {
        return retryOnFail;
    }

    public void setRetryOnFail(boolean rof) {
        retryOnFail = rof;
    }

    public int getSimulatedSmartDrivers() {
        return simulatedSmartDrivers;
    }

    public void setSimulatedSmartDrivers(int ssd) {
        simulatedSmartDrivers = ssd;
    }

    public static boolean isSimulating() {
        return (simulationTimers != null && !simulationTimers.isEmpty());
    }

    public void getCurrentLatLng() {
        if (isSimulating()) {
            for (Marker m : simulatedMapModel.getMarkers()) {
                LOG.log(Level.FINE, "getCurrentLatLng() - Id del marker: {0}", m.getId());
                LatLng latLng = m.getLatlng();
                RequestContext context = RequestContext.getCurrentInstance();
                // Posición.
                if (latLng != null) {
                    context.addCallbackParam("latLng_" + m.getId(), latLng.getLat() + "," + latLng.getLng());
                }
                // Icono.
                String icon = m.getIcon();
                if (icon != null) {
                    context.addCallbackParam("icon_" + m.getId(), icon);
                }
                // Información.
                String title = m.getTitle();
                if (title != null) {
                    context.addCallbackParam("title_" + m.getId(), title);
                }
            }
        } else {
            // Ha terminado la simulación.
            resetCarMarkers();
        }
    }

    public static void realTimeSimulate() {
        // Si el temporizador está instanciado, es que hay una simulación en marcha y se quiere parar.
        if (isSimulating()) {
            if (ztreamyErrors > 0 || zTreamyNoOkSends > 0) {
                LOG.log(Level.SEVERE, "realTimeSimulate() - RESULTADO:\n\n-> Tramas generadas={0}\n-> Oks={1}\n-> NoOks={2}\n-> Otros errores={3}\n-> Recuperados={4}\n-> No enviados finalmente={5}\n-> Hilos restantes={6}\n\n", new Object[]{ztreamyObjectsCount, zTreamyOkSends, zTreamyNoOkSends, ztreamyErrors, zTreamyRecovered, zTreamyFinallyPending, runningThreads});
            } else {
                LOG.log(Level.INFO, "realTimeSimulate() - RESULTADO:\n\nLos envíos a Ztreamy se han realizado correctamente:\n\n-> Tramas generadas={0}\n-> Oks={1}\n-> Hilos restantes={2}\n\n", new Object[]{ztreamyObjectsCount, zTreamyOkSends, runningThreads});
            }

            for (Timer timer : simulationTimers.values()) {
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
            }

            endSimulationTime = System.currentTimeMillis();
            LOG.log(Level.INFO, "realTimeSimulate() - Inicio de la simulacion: {0} -> Fin de la simulación: {1} ({2})", new Object[]{Constants.dfISO8601.format(startSimulationTime), Constants.dfISO8601.format(endSimulationTime), DurationFormatUtils.formatDuration(endSimulationTime - startSimulationTime, "HH:mm:ss", true)});
            resetSimulation();
        } else {
            resetSimulation();
            simulationTimers = new HashMap<>();
            startSimulationTime = System.currentTimeMillis();
            LOG.log(Level.INFO, "realTimeSimulate() - Comienzo de la simulación: {0}", Constants.dfISO8601.format(startSimulationTime));
            LOG.log(Level.INFO, "realTimeSimulate() - Condiciones:\n-> Actualización cada segundo (Tiempo real)\n-> ¿Reenviar tramas fallidas?: ", retryOnFail);
            runningThreads = simulatedSmartDrivers * locationLogList.size();
            LOG.log(Level.INFO, "realTimeSimulate() - Se crean: {0} hilos de ejecución", runningThreads);
            try {
                Random rand = new Random();
                for (int i = 0; i < locationLogList.size(); i++) {
                    LocationLog ll = locationLogList.get(i);
                    LocationLogDetail smartDriverPosition = ll.getLocationLogDetailList().get(0);
                    LatLng latLng = new LatLng(smartDriverPosition.getLatitude(), smartDriverPosition.getLongitude());

                    for (int j = 0; j < simulatedSmartDrivers; j++) {
                        Marker m = new Marker(latLng, smartDriverPosition.getMarkerTitle(), null, MARKER_GREEN_CAR_ICON_PATH);
                        m.setVisible(true);
                        m.setDraggable(false);

                        Timer timer = new Timer();
                        timer.scheduleAtFixedRate(new SimulatedSmartDriver(ll, m, true, true), 100 + rand.nextInt(MAX_INITIAL_DELAY), 1000);
                        simulationTimers.put(i + "_" + j, timer);

                        simulatedMapModel.addOverlay(m);
                    }
                }
            } catch (MalformedURLException | HermesException ex) {
                // Cancelamos las simulaciones.
                for (Timer timer : simulationTimers.values()) {
                    if (timer != null) {
                        timer.cancel();
                        timer = null;
                    }
                    resetSimulation();
                }
            }
        }
    }

    private static void resetSimulation() {
        RequestContext context = RequestContext.getCurrentInstance();
        if (context != null) {
            if (simulationTimers != null) {
                context.execute("PF('gmapUpdaterVar').stop();");
            } else {
                context.execute("PF('gmapUpdaterVar').start();");
            }
        }

        resetCarMarkers();
        simulationTimers = null;
        ztreamyObjectsCount = 0;
        zTreamyOkSends = 0;
        zTreamyNoOkSends = 0;
        zTreamyRecovered = 0;
        ztreamyErrors = 0;
    }

    private static void resetCarMarkers() {
        for (int i = simulatedMapModel.getMarkers().size() - 1; i >= 0; i--) {
            Marker m = simulatedMapModel.getMarkers().get(i);
            if (m.getIcon().equals(MARKER_GREEN_CAR_ICON_PATH)) {
                simulatedMapModel.getMarkers().remove(i);
            }
        }
    }

    public static synchronized void finishOneThread() {
        if (runningThreads > 0) {
            runningThreads--;
        }
    }

    public static synchronized int getRunningThreads() {
        return runningThreads;
    }

    public static synchronized boolean isAllSimulationsFinished() {
        return runningThreads == 0;
    }

    public static synchronized void increaseZtreamyObjectsCount() {
        ztreamyObjectsCount++;
    }

    public static synchronized void increaseZtreamyOkSends() {
        zTreamyOkSends++;
    }

    public static synchronized void increaseZtreamyNoOkSends() {
        zTreamyNoOkSends++;
    }

    public static synchronized void increaseZtreamyRecovered() {
        zTreamyRecovered++;
    }

    public static synchronized void increaseZtreamyErrors() {
        ztreamyErrors++;
    }

    public static synchronized void addZtreamyFinallyPending(int pending) {
        zTreamyFinallyPending += pending;
    }

    public static synchronized void logCurrentStatus() {
        LOG.log(Level.SEVERE, "logCurrentStatus() - ESTADO ACTUAL: Tramas generadas={0}|Oks={1}|NoOks={2}|Otros errores={3}|Recuperados={4}|No enviados finalmente={5}|Hilos restantes={6}", new Object[]{ztreamyObjectsCount, zTreamyOkSends, zTreamyNoOkSends, ztreamyErrors, zTreamyRecovered, zTreamyFinallyPending, runningThreads});
    }
}
