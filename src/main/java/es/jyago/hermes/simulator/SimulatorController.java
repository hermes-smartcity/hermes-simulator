package es.jyago.hermes.simulator;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import es.jyago.hermes.csv.CSVEvent;
import es.jyago.hermes.google.directions.Location;
import es.jyago.hermes.location.detail.LocationLogDetail;
import es.jyago.hermes.location.LocationLog;
import es.jyago.hermes.openStreetMap.PositionSimulatedSpeed;
import es.jyago.hermes.person.Person;
import es.jyago.hermes.util.Constants;
import es.jyago.hermes.util.Email;
import es.jyago.hermes.util.HermesException;
import es.jyago.hermes.util.Util;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.mail.MessagingException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.joda.time.LocalTime;
import org.primefaces.context.RequestContext;
import org.primefaces.event.SlideEndEvent;
import org.primefaces.event.map.OverlaySelectEvent;
import org.primefaces.model.map.DefaultMapModel;
import org.primefaces.model.map.LatLng;
import org.primefaces.model.map.MapModel;
import org.primefaces.model.map.Marker;
import org.primefaces.model.map.Polyline;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

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

    // Paquetes de peticiones en la generación de trayectos. Google ha limitado más el número de peticiones por segundo.
    private static final int REQUEST_PACK_SIZE = 10;

    // Número máximo de hilos en el simulador.
    private static final int MAX_THREADS = 50000;

    // Número de tramas de Ztreamy generadas.
    private static volatile int ztreamyObjects = 0;
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
    // Número de envíos que se han realizado, ya sean correctos o fallidos.
    private static volatile int ztreamySends = 0;
    // Número de tramas descartadas por alcanzar el máximo de reintentos.
    private static volatile int ztreamyDiscarded = 0;
    // Número de hilos en ejecución
    private static volatile int runningThreads = 0;

    private Marker marker;

    // Distancia del trayecto.
    private static int distance = 10;
    // Distancia desde el centro de Sevilla.
    private static int distanceFromSevilleCenter = 1;
    // Número de trayectos a generar.
    private static int tracksAmount = 1;
    // Indicará los segundos que habrá que esperar entre reintentos en caso de fallo.
    static int secondsBetweenRetries = 10;

    private static MapModel simulatedMapModel;
    private static ArrayList<LocationLog> locationLogList;

    private static Map<String, Timer> simulationTimers = null;
    private static int simulatedSmartDrivers = 1;
    static long startSimulationTime = 0l;
    static long endSimulationTime = 0l;

    private static String email = "jorgeyago.ingeniero@gmail.com";
    private static boolean enableGUI = true;

    private static volatile List<CSVEvent> csvEventList;

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
                            LOG.log(Level.SEVERE, "generateSimulatedTracks() - Error I/O: {0}", ex.getMessage());
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

        // Tomamos la marca de tiempo actual. Nos servirá para espaciar las peticiones de trayectos a Google, ya que no se pueden hacer más de 10 peticiones por segundo con la cuenta gratuita.
        // Aplicamos el mismo criterio para OpenStreetMap, aunque no sea necesario en principio.
        long timeMark = System.currentTimeMillis();
        // Ejecutamos el listado de tareas, que se dividirá en los hilos y con las condiciones que haya configurados en 'TrackRequestWebService'.
        for (int i = 0; i <= trackRequestTaskList.size(); i += REQUEST_PACK_SIZE) {
            if (i > 0) {
                long elapsedTime = System.currentTimeMillis() - timeMark;
                if (elapsedTime < 1000) {
                    try {
                        // Antes de hacer la siguiente petición, esperamos 1 segundo, para cumplir las restricciones de Google.
                        Thread.sleep(1000 - elapsedTime);
                    } catch (InterruptedException ex) {
                    } finally {
                        timeMark = System.currentTimeMillis();
                    }
                }
                requestTracks(trackRequestTaskList.subList(i - REQUEST_PACK_SIZE, i));
            }
        }
        int remaining = trackRequestTaskList.size() % REQUEST_PACK_SIZE;
        if (remaining != 0) {
            try {
                // Antes de hacer la siguiente petición, esperamos 1 segundo, para cumplir las restricciones de Google.
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
            requestTracks(trackRequestTaskList.subList(trackRequestTaskList.size() - remaining, trackRequestTaskList.size()));
        }

        // Paramos el 'listener'
        TrackRequestWebService.shutdown();
        LOG.log(Level.INFO, "generateSimulatedTracks() - Trayectos generados: {0}", locationLogList.size());
        RequestContext context = RequestContext.getCurrentInstance();
        if (context != null) {
            context.update("gmap");
            context.update("tracksAmountVar");
        }
    }

    private void requestTracks(List<Callable<String>> trackRequestTaskSublist) {
        try {
            List<Future<String>> futureTaskList = TrackRequestWebService.submitAllTask(trackRequestTaskSublist);
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
        }
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

    public void onSlideEndTracksAmount(SlideEndEvent event) {
        if (event.getValue() * simulatedSmartDrivers > MAX_THREADS) {
            simulatedSmartDrivers = MAX_THREADS / event.getValue();
        }
    }

    public int getSecondsBetweenRetries() {
        return secondsBetweenRetries;
    }

    public void setSecondsBetweenRetries(int sbr) {
        secondsBetweenRetries = sbr;
    }

    public int getSimulatedSmartDrivers() {
        return simulatedSmartDrivers;
    }

    public void setSimulatedSmartDrivers(int ssd) {
        simulatedSmartDrivers = ssd;
    }

    public void onSlideEndSimulatedSmartDrivers(SlideEndEvent event) {
        if (tracksAmount * event.getValue() > MAX_THREADS) {
            tracksAmount = MAX_THREADS / event.getValue();
        }
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
            String simulationSummary;
            if (ztreamyErrors > 0 || zTreamyNoOkSends > 0) {
                simulationSummary = MessageFormat.format("RESULTADO DE LA SIMULACION:\n\n-> Tramas generadas={0}\n-> Envíos realizados={1}\n-> Oks={2}\n-> NoOks={3}\n-> Otros errores={4}\n-> Recuperados={5}\n-> Descartados={6}\n-> No reenviados finalmente={7}\n-> Hilos restantes={8}\n-> Trayectos={9}\n-> Distancia={10}\n-> Instancias SmartDriver por trayecto={11}\n-> Segundos entre reintentos={12}\n\n", new Object[]{ztreamyObjects, ztreamySends, zTreamyOkSends, zTreamyNoOkSends, ztreamyErrors, zTreamyRecovered, ztreamyDiscarded, zTreamyFinallyPending, runningThreads, locationLogList.size(), distance, simulatedSmartDrivers, secondsBetweenRetries});
                LOG.log(Level.SEVERE, "realTimeSimulate() - {0}", simulationSummary);
            } else {
                simulationSummary = MessageFormat.format("RESULTADO DE LA SIMULACION:\n\nLos envíos a Ztreamy se han realizado correctamente:\n\n-> Tramas generadas={0}\n-> Oks={1}\n-> Hilos restantes={2}\n-> Trayectos={3}\n-> Distancia={4}\n-> Instancias SmartDriver por trayecto={5}\n-> Segundos entre reintentos={6}\n\n", new Object[]{ztreamyObjects, zTreamyOkSends, runningThreads, locationLogList.size(), distance, simulatedSmartDrivers, secondsBetweenRetries});
                LOG.log(Level.INFO, "realTimeSimulate() - {0}", simulationSummary);
            }

            for (Timer timer : simulationTimers.values()) {
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
            }

            endSimulationTime = System.currentTimeMillis();
            String timeSummary = MessageFormat.format("Inicio de la simulacion: {0} -> Fin de la simulación: {1} ({2})", new Object[]{Constants.dfISO8601.format(startSimulationTime), Constants.dfISO8601.format(endSimulationTime), DurationFormatUtils.formatDuration(endSimulationTime - startSimulationTime, "HH:mm:ss", true)});
            LOG.log(Level.INFO, "realTimeSimulate() - {0}", timeSummary);

            try {
                List<File> attachedFileList = new ArrayList<>();
                if (csvEventList != null && !csvEventList.isEmpty()) {
                    Path zipFilePath = generateZippedCSV();
                    attachedFileList.add(zipFilePath.toFile());
                }
                // Se envía un e-mail para notificar que la simulación ha terminado.
                Email.generateAndSendEmail(email, "FIN DE SIMULACION", "<html><head><title></title></head><body><p>" + simulationSummary.replaceAll("\n", "<br/>") + "</p><p>" + timeSummary + "</p><p>Un saludo.</p></body></html>", attachedFileList);
            } catch (MessagingException ex) {
                LOG.log(Level.SEVERE, "realTimeSimulate() - No se ha podido enviar el e-mail con los resultados de la simulación", ex);
            }
            resetSimulation();
        } else {
            resetSimulation();
            simulationTimers = new HashMap<>();
            startSimulationTime = System.currentTimeMillis();
            LOG.log(Level.INFO, "realTimeSimulate() - Comienzo de la simulación: {0}", Constants.dfISO8601.format(startSimulationTime));
            LOG.log(Level.INFO, "realTimeSimulate() - Condiciones:\n-> Actualización cada: {0} milisegundos. Ejecución en tiempo real\n-> Segundos entre reintentos={1}\n", new Object[]{secondsBetweenRetries});
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
                LOG.log(Level.SEVERE, "realTimeSimulate() - Ha ocurrido un problema al crear los hilos de ejecución. Se cancela la simulación", ex);
                // Cancelamos las simulaciones.
                realTimeSimulate();
            }
        }
    }

    private static void resetSimulation() {
        RequestContext context = RequestContext.getCurrentInstance();
        if (context != null) {
            if (simulationTimers != null) {
                if (enableGUI) {
                    context.execute("PF('gmapUpdaterVar').stop();");
                }
                context.execute("PF('finishDialogVar').show();");
            } else if (enableGUI) {
                context.execute("PF('gmapUpdaterVar').start();");
            }
        }

        resetCarMarkers();
        simulationTimers = null;
        ztreamyObjects = 0;
        zTreamyOkSends = 0;
        zTreamyNoOkSends = 0;
        zTreamyRecovered = 0;
        ztreamyErrors = 0;
        zTreamyFinallyPending = 0;
        ztreamySends = 0;
        ztreamyDiscarded = 0;
        csvEventList = new ArrayList<>();
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

    public static synchronized void increaseZtreamyObjects() {
        ztreamyObjects++;
    }

    public static synchronized void increaseZtreamyOkSends() {
        zTreamyOkSends++;
    }

    public static synchronized void increaseZtreamyNoOkSends() {
        zTreamyNoOkSends++;
    }

    public static synchronized void addZtreamyRecovered(int recovered) {
        zTreamyRecovered += recovered;
    }

    public static synchronized void increaseZtreamyErrors() {
        ztreamyErrors++;
    }

    public static synchronized void addZtreamyFinallyPending(int pending) {
        zTreamyFinallyPending += pending;
    }

    public static synchronized void increaseZtreamySends() {
        ztreamySends++;
    }

    public static synchronized void increaseZtreamyDiscarded() {
        ztreamyDiscarded++;
    }

    public static void logCurrentStatus() {
        LOG.log(Level.SEVERE, "logCurrentStatus() - ESTADO ACTUAL: Tramas generadas={0}|Envíos realizados={1}|Oks={2}|NoOks={3}|Otros errores={4}|Recuperados={5}|Descartados={6}|No reenviados finalmente={7}|Hilos restantes={8}", new Object[]{ztreamyObjects, ztreamySends, zTreamyOkSends, zTreamyNoOkSends, ztreamyErrors, zTreamyRecovered, ztreamyDiscarded, zTreamyFinallyPending, runningThreads});
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String e) {
        email = e;
    }

    public boolean isEnableGUI() {
        return enableGUI;
    }

    public void setEnableGUI(boolean e) {
        enableGUI = e;
    }

    public static synchronized void addCSVEvents(List<CSVEvent> list) {
        csvEventList.addAll(list);
    }

    private static Path generateZippedCSV() {
        try {
            // Creamos un directorio temporal para contener los archivos generados.
            Path tempDir = Files.createTempDirectory("Hermes_Simulator");
            String tempDirPath = tempDir.toAbsolutePath().toString() + File.separator;
            LOG.log(Level.INFO, "generateZippedCSV() - Directorio temporal para almacenar los CSV: {0}", tempDirPath);

            // Creamos un archivo temporal para el CSV con los datos de los eventos.
            String fileName = Constants.dfFile.format(System.currentTimeMillis());
            String fileNameCSV = fileName + ".csv";
            LOG.log(Level.INFO, "generateZippedCSV() - Generando archivo CSV: {0}", fileNameCSV);
            File file = new File(tempDir.toUri().getPath(), fileNameCSV);
            createDataFile(CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE, false, file);

            // Creamos el archivo ZIP.
            Path zipFile = Files.createTempFile(fileName + "_", ".zip");
            try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFile))) {
                LOG.log(Level.INFO, "generateZippedCSV() - Generando ZIP: {0}", zipFile.getFileName().toString());

                // Recorremos los archivos CSV del directorio temporal.
                // Para almacenar los archivos en el ZIP, sin directorio.
                String sp = file.getAbsolutePath().replace(tempDirPath, "");
                ZipEntry zipEntry = new ZipEntry(sp);
                try {
                    zs.putNextEntry(zipEntry);
                    zs.write(Files.readAllBytes(file.toPath()));
                    zs.closeEntry();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "generateZippedCSV() - No se ha podido comprimir el archivo: {0}", sp);
                }
            }
            return zipFile;
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "generateZippedCSV() - No se ha podido generar el archivo con los datos de todos los eventos", ex);
        }
        return null;
    }

    private static void createDataFile(CsvPreference csvPreference, boolean ignoreHeaders, File file) {
        ICsvBeanWriter beanWriter = null;

        if (csvEventList != null && !csvEventList.isEmpty()) {
            try {

                beanWriter = new CsvBeanWriter(new FileWriter(file), csvPreference);

                CSVEvent bean = csvEventList.get(0);
                // Seleccionamos los atributos que vamos a exportar.
                final String[] fields = bean.getFields();

                // Aplicamos las características de los campos.
                final CellProcessor[] processors = bean.getProcessors();

                if (!ignoreHeaders) {
                    // Ponemos la cabecera con los nombres de los atributos.
                    if (bean.getHeaders() != null) {
                        beanWriter.writeHeader(bean.getHeaders());
                    } else {
                        beanWriter.writeHeader(fields);
                    }
                }

                // Procesamos los elementos.
                for (final CSVEvent element : csvEventList) {
                    beanWriter.write(element, fields, processors);
                }
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "getFileData() - Error al exportar a CSV", ex);
            } finally {
                // Cerramos.
                if (beanWriter != null) {
                    try {
                        beanWriter.close();
                    } catch (IOException ex) {
                        LOG.log(Level.SEVERE, "getFileData() - Error al cerrar el 'writer'", ex);
                    }
                }
            }
        }
    }
}
