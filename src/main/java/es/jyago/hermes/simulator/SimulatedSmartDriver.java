package es.jyago.hermes.simulator;

import es.jyago.hermes.location.LocationLog;
import es.jyago.hermes.location.detail.LocationLogDetail;
import es.jyago.hermes.smartDriver.DataSection;
import es.jyago.hermes.smartDriver.RoadSection;
import es.jyago.hermes.util.Constants;
import es.jyago.hermes.util.HermesException;
import es.jyago.hermes.util.Util;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.MediaType;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.primefaces.model.map.LatLng;
import org.primefaces.model.map.Marker;
import ztreamy.Event;
import ztreamy.JSONSerializer;
import ztreamy.PublisherHC;

public class SimulatedSmartDriver extends TimerTask {

    private static final Logger LOG = Logger.getLogger(SimulatedSmartDriver.class.getName());

    // En este caso, no cogemos la configuración de Ztreamy, sino que enviamos los datos a una URL con un Ztreamy para pruebas.
    // El 'dashboard' está en: http://hermes1.gast.it.uc3m.es:9209/backend/dashboard.html
    private static final String URL = "http://hermes1.gast.it.uc3m.es:9220/collector/publish";

    // Parámetros recogidos de SmartDriver.
    private static final int ZTREAMY_SEND_INTERVAL_SECONDS = 10;
    private static final int ZTREAMY_SEND_INTERVAL_METERS = 500;
    private static final double HIGH_ACCELERATION_THRESHOLD = 2.5d;
    private static final double HIGH_DECELERATION_THRESHOLD = -3.5d;

    // Parámetros míos de la simulación.
    private int stressLoad; // Indicará el nivel de carga de estrés.
    private boolean relaxing; // Indicará si el usuario está relajándose tras una carga de estrés.

    private final Marker trackMarker;
    private int elapsedSeconds;
    private boolean locationChanged;
    private PublisherHC publisher;
    private List<LocationLogDetail> localLocationLogDetailList;
    private double speedRandomFactor;

    private int currentPosition;
    private boolean finished;
    private final LocationLog ll;
    private double sectionDistance;
    private double cummulativePositiveSpeeds;
    private final List<RoadSection> roadSectionList;
    private int ztreamySecondsCount;
    private final int minRrTime;
    private final String sha;

    /**
     * Constructor para cada instancia de 'SmartDriver'.
     *
     * @param ll Contendrá los datos de la ruta que debe seguir.
     * @param trackMarker 'Marker' para mostrar la posición actual en el mapa.
     * @param variableSpeed Indicará si su velocidad será variable o no.
     * @param variableHeartRate Indicará si su ritmo cardíaco será variable o
     * no.
     *
     * @throws MalformedURLException
     * @throws HermesException
     */
    public SimulatedSmartDriver(LocationLog ll, Marker trackMarker, boolean variableSpeed, boolean variableHeartRate) throws MalformedURLException, HermesException {
        this.ll = ll;
        this.trackMarker = trackMarker;
        this.elapsedSeconds = 0;
        this.locationChanged = false;
        this.currentPosition = 0;
        this.finished = false;
        this.sectionDistance = 0.0d;
        this.roadSectionList = new ArrayList();
        this.cummulativePositiveSpeeds = 0.0d;
        this.ztreamySecondsCount = 0;
        this.stressLoad = 0; // Suponemos que inicialmente no está estresado.
        int age = ThreadLocalRandom.current().nextInt(18, 65 + 1); // Simularemos conductores de distintas edades (entre 18 y 65 años), para establecer el ritmo cardíaco máximo en la simulación.
        this.minRrTime = (int) Math.ceil(60000.0d / (220 - age)); // Mínimo R-R, que establecerá el ritmo cardíaco máximo.
        this.publisher = new PublisherHC(new URL(URL), new JSONSerializer());
        this.sha = new String(Hex.encodeHex(DigestUtils.sha256(System.currentTimeMillis() + ll.getPerson().getEmail())));
        if (variableSpeed) {
            speedRandomFactor = 0.5d + (new Random().nextDouble() * 1.0d);
            localLocationLogDetailList = new ArrayList<>();
            for (LocationLogDetail lld : ll.getLocationLogDetailList()) {
                // Aplicamos la variación aleatoria de la velocidad.
                double newSpeed = lld.getSpeed() * speedRandomFactor;
                localLocationLogDetailList.add(new LocationLogDetail(lld.getLatitude(), lld.getLongitude(), newSpeed, lld.getHeartRate(), lld.getRrTime(), (int) (Math.ceil(lld.getSecondsToBeHere() / speedRandomFactor))));
            }
        }
        // FIXME: Hacer variable el ritmo cardíaco.
    }

    @Override
    public void run() {
        LocationLogDetail currentLocationLogDetail = localLocationLogDetailList.get(currentPosition);

        double distance;
        double bearing;
        // Por defecto, en la simulación se tiende al estado relajado.
        relaxing = true;

        LOG.log(Level.FINE, "SimulatedSmartDriver.run() - El usuario de SmartDriver se encuentra en: ({0}, {1})", new Object[]{currentLocationLogDetail.getLatitude(), currentLocationLogDetail.getLongitude()});

        if (!finished) {
            LOG.log(Level.FINE, "SimulatedSmartDriver.run() - Elemento actual: {0} de {1}", new Object[]{currentPosition, localLocationLogDetailList.size()});

            // Comprobamos si ha pasado suficiente tiempo como para pasar a la siguiente localización.
            if (elapsedSeconds >= currentLocationLogDetail.getSecondsToBeHere()) {
                // Comprobamos si hemos llegado al destino.
                if (currentPosition == localLocationLogDetailList.size() - 1) {
                    // Si hemos llegado, hacemos invisible el marker del mapa.
                    trackMarker.setVisible(false);
                    finished = true;
                    // Descontamos el hilo actual.
                    SimulatorController.finishOneThread();
                    LOG.log(Level.FINE, "SimulatedSmartDriver.run() - Hilos de ejecución restantes: {0}", SimulatorController.getRunningThreads());
                    this.cancel();
                } else {
                    // No hemos llegado al destino, avanzamos de posición.
                    int previousPosition = currentPosition;
                    currentPosition = previousPosition + 1;

                    LOG.log(Level.FINE, "SimulatedSmartDriver.run() - Avanzamos de posición: {0}", currentPosition);
                    currentLocationLogDetail = localLocationLogDetailList.get(currentPosition);
                    LOG.log(Level.FINE, "SimulatedSmartDriver.run() - El usuario de SmartDriver se encuentra en: ({0}, {1})", new Object[]{currentLocationLogDetail.getLatitude(), currentLocationLogDetail.getLongitude()});

                    // Modificamos el 'marker' de Google Maps.
                    trackMarker.setLatlng(new LatLng(currentLocationLogDetail.getLatitude(), currentLocationLogDetail.getLongitude()));
                    LocationLogDetail previousLocationLogDetail = localLocationLogDetailList.get(previousPosition);

                    // Calculamos la distancia recorrida.
                    distance = Util.distanceHaversine(previousLocationLogDetail.getLatitude(), previousLocationLogDetail.getLongitude(), currentLocationLogDetail.getLatitude(), currentLocationLogDetail.getLongitude());

                    // Calculamos la orientación para simular estrés al entrar en una curva.
                    bearing = Util.bearing(previousLocationLogDetail.getLatitude(), previousLocationLogDetail.getLongitude(), currentLocationLogDetail.getLatitude(), currentLocationLogDetail.getLongitude());

                    if (previousPosition > 1) {
                        LocationLogDetail antePreviousLocationLogDetail = localLocationLogDetailList.get(previousPosition - 1);
                        double previousBearing = Util.bearing(antePreviousLocationLogDetail.getLatitude(), antePreviousLocationLogDetail.getLongitude(), previousLocationLogDetail.getLatitude(), previousLocationLogDetail.getLongitude());
                        double bearingDiff = Math.abs(bearing - previousBearing);

                        // Si hay una desviación brusca de la trayectoria, suponemos una componente de estrés.
                        stressForDeviation(bearingDiff);
                    }

                    double speedDiff = Math.abs(currentLocationLogDetail.getSpeed() - previousLocationLogDetail.getSpeed());

                    // Si hay un salto grande de velocidad, suponemos una componente de estrés.
                    stressForSpeed(speedDiff);

                    // Analizamos el ritmo cardíaco, 
                    // Medimos las unidades de estrés y dibujamos el marker del color correspondiente (verde -> sin estrés, amarillo -> ligeramente estresado, rojo -> estresado)
                    if (stressLoad == 0) {
                        // No hay estrés.
                        trackMarker.setIcon(SimulatorController.MARKER_GREEN_CAR_ICON_PATH);
                    } else {
                        // Si se está calmando, le subimos el intervalo RR y si se está estresando, le bajamos el intervalo RR.
                        if (relaxing) {
                            if (stressLoad > 0) {
                                currentLocationLogDetail.setRrTime(previousLocationLogDetail.getRrTime() - ((previousLocationLogDetail.getRrTime() - currentLocationLogDetail.getRrTime()) / stressLoad));
                            }
                        } else if (stressLoad < 5) {
                            currentLocationLogDetail.setRrTime(previousLocationLogDetail.getRrTime() - (minRrTime / stressLoad));
                        } else {
                            // Establecemos un mínimo R-R en función de la edad del conductor.
                            currentLocationLogDetail.setRrTime(minRrTime);
                        }

                        if (stressLoad < 5) {
                            // Existe una situación de estrés 'ligero'.
                            // Para que Víctor pueda detectar una situación de estrés, debe haber una diferencia de 50ms en el RR.
                            trackMarker.setIcon(SimulatorController.MARKER_YELLOW_CAR_ICON_PATH);
                        } else {
                            //  Estrés elevado.
                            trackMarker.setIcon(SimulatorController.MARKER_RED_CAR_ICON_PATH);
                        }
                    }

                    // Calculamos el ritmo cardíaco a partir del intervalo RR.
                    currentLocationLogDetail.setHeartRate((int) Math.ceil(60.0d / (currentLocationLogDetail.getRrTime() / 1000.0d)));

                    // Acumulamos la distancia recorrida.
                    sectionDistance += distance;

                    // Hacemos el análisis del PKE (Positive Kinetic Energy)
                    cummulativePositiveSpeeds += analyzePKE(currentLocationLogDetail, previousLocationLogDetail);

                    if (currentLocationLogDetail.getTimeLog() == null) {
                        currentLocationLogDetail.setTimeLog(new Date());
                    }
                    // Información.
                    trackMarker.setTitle(currentLocationLogDetail.getMarkerTitle());

                    // Creamos un elementos de tipo 'RoadSection', para añadirlo al 'DataSection' que se envía a 'Ztreamy' cada 500 metros.
                    RoadSection rd = new RoadSection();
                    rd.setTime(currentLocationLogDetail.getTimeLog().getTime());
                    rd.setLatitude(currentLocationLogDetail.getLatitude());
                    rd.setLongitude(currentLocationLogDetail.getLongitude());
                    int tDiff = (currentLocationLogDetail.getSecondsToBeHere() - previousLocationLogDetail.getSecondsToBeHere());
                    if (tDiff > 0) {
                        rd.setSpeed(distance * 3.6 / tDiff);
                    } else {
                        rd.setSpeed(previousLocationLogDetail.getSpeed());
                    }
                    rd.setHeartRate(currentLocationLogDetail.getHeartRate());
                    rd.setRrTime(currentLocationLogDetail.getRrTime());
                    rd.setAccuracy(0);

                    roadSectionList.add(rd);

                    // Hemos cambiado de localización.
                    locationChanged = true;
                }
            }

            if (locationChanged && isTimeToSend()) {
                // Sólo si cambiamos de posición y han pasado más de 10 segundos, se envía información a 'Ztreamy'.
                sendEvery10SecondsIfLocationChanged(currentLocationLogDetail);
            }

            // Se enviará un resumen cada 500 metros.
            if (sectionDistance >= ZTREAMY_SEND_INTERVAL_METERS) {
                sendDataSectionToZtreamy();
            }

            // Comprobamos si han terminado todas las simulaciones para parar los 'Timer'
            if (SimulatorController.isAllSimulationsFinished()) {
                LOG.log(Level.INFO, "SimulatedSmartDriver.run() - Todos los hilos completados");
                SimulatorController.realTimeSimulate();
            }

            elapsedSeconds++;
            ztreamySecondsCount++;
            LOG.log(Level.FINE, "SimulatedSmartDriver.run() - Tiempo de simulación transcurrido: {0}", DurationFormatUtils.formatDuration(elapsedSeconds * 1000, "HH:mm:ss", true));
        }
    }

    private void stressForDeviation(double bearingDiff) {
        // TODO: ¿Tener en cuenta la velocidad? 
        // http://www.causadirecta.com/especial/centro-de-calculo/calculo-de-la-velocidad-critica-de-una-curva
        // https://doblevia.wordpress.com/2007/03/19/curvas-circulares-simples/
        // FIXME: ¿Graduación del estrés por el cambio de trayectoria?
        if (bearingDiff < 25.0d) {
            // Es un tramo 'fácil'.
            if (stressLoad > 0) {
                stressLoad--;
            }
        } else if (bearingDiff < 45.0d) {
            // Es una curva algo cerrada, añadimos un punto de estrés.
            stressLoad++;
            relaxing = false;
        } else if (bearingDiff < 45.0d) {
            // Es una curva cerrada, añadimos 2 punto de estrés.
            stressLoad += 2;
            relaxing = false;
        } else {
            // Es un giro muy cerrado, añadimos 5 punto de estrés.
            stressLoad += 5;
            relaxing = false;
        }
    }

    private void stressForSpeed(double speedDiff) {
        // FIXME: ¿Graduación del estrés por la velocidad?
        if (speedDiff < 30.0d) {
            //  Es una variación de velocidad moderada.
            if (stressLoad > 0) {
                stressLoad--;
            }
        } else if (speedDiff < 50.0d) {
            // Es una variación de velocidad alta, añadimos un punto de estrés.
            stressLoad++;
            relaxing = false;
        } else if (speedDiff < 100.0d) {
            // Es una variación de velocidad muy alta, añadimos 2 punto de estrés.
            stressLoad += 2;
            relaxing = false;
        } else {
            // Es una variación de velocidad brusca, añadimos 5 puntos de estrés.
            stressLoad += 5;
            relaxing = false;
        }
    }

    private boolean isTimeToSend() {
        return ztreamySecondsCount >= ZTREAMY_SEND_INTERVAL_SECONDS;
    }

    private void sendEvery10SecondsIfLocationChanged(LocationLogDetail currentLocationLogDetail) {
        // Creamos un objeto de tipo 'Location' de los que 'SmartDriver' envía a 'Ztreamy'.
        es.jyago.hermes.smartDriver.Location smartDriverLocation = new es.jyago.hermes.smartDriver.Location();
        smartDriverLocation.setLatitude(currentLocationLogDetail.getLatitude());
        smartDriverLocation.setLongitude(currentLocationLogDetail.getLongitude());
        smartDriverLocation.setSpeed(currentLocationLogDetail.getSpeed());
        smartDriverLocation.setAccuracy(0);
        smartDriverLocation.setScore(0);
        // Asignamos el momento actual del envío de la trama a Ztreamy al LocationLogDetail.
        smartDriverLocation.setTimeStamp(Constants.dfISO8601.format(new Date()));

        try {
            HashMap<String, Object> bodyObject = new HashMap<>();
            bodyObject.put("Location", smartDriverLocation);
            int result = publisher.publish(new Event(sha, MediaType.APPLICATION_JSON, Constants.SIMULATOR_APPLICATION_ID, "Vehicle Location", bodyObject), true);
            SimulatorController.increaseZtreamySends();

            if (result == HttpURLConnection.HTTP_OK) {
                LOG.log(Level.FINE, "sendEvery10SecondsIfLocationChanged() - Localización de trayecto simulado enviada correctamante. SmartDriver: {0}", ll.getPerson().getEmail());
                locationChanged = false;
                // Iniciamos el contador de tiempo para el siguiente envío.
                ztreamySecondsCount = 0;
            } else {
                SimulatorController.increaseZtreamyErrors();
                LOG.log(Level.SEVERE, "sendEvery10SecondsIfLocationChanged() - Error SEND: Result: {0} - Trama: {1} - Enviada a las: {2} - Errores: {3} / Total: {4}", new Object[]{result, Constants.dfISO8601.format(currentLocationLogDetail.getTimeLog()), Constants.dfISO8601.format(System.currentTimeMillis()), SimulatorController.getZtreamyErrors(), SimulatorController.getZtreamySends()});
                reconnectPublisher();
            }
        } catch (MalformedURLException ex) {
            LOG.log(Level.SEVERE, "sendEvery10SecondsIfLocationChanged() - Error en la URL", ex);
        } catch (IOException ex) {
            SimulatorController.increaseZtreamyErrors();
            LOG.log(Level.SEVERE, "sendEvery10SecondsIfLocationChanged() - Error I/O: {0} - Trama: {1} - Enviada a las: {2} - Errores: {3} / Total: {4}", new Object[]{ex.getMessage(), Constants.dfISO8601.format(currentLocationLogDetail.getTimeLog()), Constants.dfISO8601.format(System.currentTimeMillis()), SimulatorController.getZtreamyErrors(), SimulatorController.getZtreamySends()});
            reconnectPublisher();
            // FIXME: ¿Qué hacemos con los que no se hayan podido mandar? ¿Los guardamos y los intentamos enviar después?
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "sendEvery10SecondsIfLocationChanged() - Error desconocido: {0} - Trama: {1} - Enviada a las: {2} - Errores: {3} / Total: {4}", new Object[]{ex.getMessage(), Constants.dfISO8601.format(currentLocationLogDetail.getTimeLog()), Constants.dfISO8601.format(System.currentTimeMillis()), SimulatorController.getZtreamyErrors(), SimulatorController.getZtreamySends()});
            // FIXME: ¿Qué hacemos con los que no se hayan podido mandar? ¿Los guardamos y los intentamos enviar después?
        }
    }

    private void sendDataSectionToZtreamy() {
        // Creamos un objeto de tipo 'DataSection' de los que 'SmartDriver' envía a 'Ztreamy'.
        DataSection dataSection = new DataSection();

        dataSection.setRoadSection(roadSectionList);

        DescriptiveStatistics speedStats = new DescriptiveStatistics();
        DescriptiveStatistics heartRateStats = new DescriptiveStatistics();
        DescriptiveStatistics rrStats = new DescriptiveStatistics();
        DescriptiveStatistics accelerationStats = new DescriptiveStatistics();
        DescriptiveStatistics decelerationStats = new DescriptiveStatistics();
        RoadSection rdPrevious = roadSectionList.get(0);
        speedStats.addValue(rdPrevious.getSpeed());
        rrStats.addValue(rdPrevious.getRrTime());
        int numHighAccelerations = 0;
        int numHighDecelerations = 0;

        for (int i = 1; i < roadSectionList.size(); i++) {
            RoadSection rs = roadSectionList.get(i);
            speedStats.addValue(rs.getSpeed());

            double vDiff = (rs.getSpeed() - rdPrevious.getSpeed()) / 3.6d; // Diferencia de velocidades pasadas a m/s.
            double tDiff = (rs.getTime() - rdPrevious.getTime()) / 1000.0; // Diferencia de tiempos en segundos.
            double acceleration = tDiff > 0.0d ? vDiff / tDiff : 0.0d; // Aceleración o deceleración en m/s2.

            if (acceleration > 0.0d) {
                accelerationStats.addValue(acceleration);
                if (acceleration > HIGH_ACCELERATION_THRESHOLD) {
                    numHighAccelerations++;
                }
            } else if (acceleration < 0.0d) {
                decelerationStats.addValue(acceleration);
                if (numHighDecelerations < HIGH_DECELERATION_THRESHOLD) {
                    numHighDecelerations++;
                }
            }

            heartRateStats.addValue(rs.getHeartRate());
            rrStats.addValue(rs.getRrTime());

            rdPrevious = rs;
        }
        dataSection.setAverageAcceleration(accelerationStats.getN() > 0 ? (!Double.isNaN(accelerationStats.getMean()) ? accelerationStats.getMean() : 0.0d) : 0.0d);
        dataSection.setAverageDeceleration(decelerationStats.getN() > 0 ? (!Double.isNaN(decelerationStats.getMean()) ? decelerationStats.getMean() : 0.0d) : 0.0d);
        dataSection.setAverageHeartRate(heartRateStats.getN() > 0 ? (!Double.isNaN(heartRateStats.getMean()) ? heartRateStats.getMean() : 0.0d) : 0.0d);
        dataSection.setAverageRR(rrStats.getN() > 0 ? (!Double.isNaN(rrStats.getMean()) ? rrStats.getMean() : 0.0d) : 0.0d);
        dataSection.setAverageSpeed(speedStats.getN() > 0 ? (!Double.isNaN(speedStats.getMean()) ? speedStats.getMean() : 0.0d) : 0.0d);
        dataSection.setNumHighAccelerations(numHighAccelerations);
        dataSection.setNumHighDecelerations(numHighDecelerations);
        dataSection.setMaxSpeed(speedStats.getN() > 0 ? speedStats.getMax() : 0.0d);
        dataSection.setMedianSpeed(speedStats.getN() > 0 ? (!Double.isNaN(speedStats.getPercentile(50)) ? speedStats.getPercentile(50) : 0.0d) : 0.0d);
        dataSection.setMinSpeed(speedStats.getN() > 0 ? speedStats.getMin() : 0.0d);
        dataSection.setPke(sectionDistance > 0.0d ? (cummulativePositiveSpeeds / sectionDistance) : 0.0d);
        List<Integer> rrSectionList = new ArrayList();
        for (double rr : rrStats.getValues()) {
            rrSectionList.add((int) rr);
        }
        dataSection.setRrSection(rrSectionList);
        dataSection.setStandardDeviationHeartRate(heartRateStats.getN() > 0 ? (!Double.isNaN(heartRateStats.getStandardDeviation()) ? heartRateStats.getStandardDeviation() : 0.0d) : 0.0d);
        dataSection.setStandardDeviationRR(rrStats.getN() > 0 ? (!Double.isNaN(rrStats.getStandardDeviation()) ? rrStats.getStandardDeviation() : 0.0d) : 0.0d);
        dataSection.setStandardDeviationSpeed(speedStats.getN() > 0 ? (!Double.isNaN(speedStats.getStandardDeviation()) ? speedStats.getStandardDeviation() : 0.0d) : 0.0d);

        // Asignamos la lista de datos del tramo.
        dataSection.setRoadSection(roadSectionList);

        try {
            HashMap<String, Object> bodyObject = new HashMap<>();
            bodyObject.put("Data Section", dataSection);
            // Como el envío se produce de manera menos contínua, creamos un 'Publisher' nuevo para hacer el envío, en lugar de intentar reutilizar el de la clase.
            int result = publisher.publish(new Event(sha, MediaType.APPLICATION_JSON, Constants.SIMULATOR_APPLICATION_ID, "Data Section", bodyObject), true);
            SimulatorController.increaseZtreamySends();

            if (result == HttpURLConnection.HTTP_OK) {
                LOG.log(Level.FINE, "sendDataSectionToZtreamy() - Datos de sección de trayecto simulado enviada correctamante. SmartDriver: {0}", ll.getPerson().getEmail());
            } else {
                SimulatorController.increaseZtreamyErrors();
                LOG.log(Level.SEVERE, "sendDataSectionToZtreamy() - Error SEND: Primera trama de la sección: {0} - Enviada a las: {1} - Errores: {2} / Total: {3}", new Object[]{dataSection.getRoadSection().get(0).getTimeStamp(), Constants.dfISO8601.format(System.currentTimeMillis()), SimulatorController.getZtreamyErrors(), SimulatorController.getZtreamySends()});
                reconnectPublisher();
            }
        } catch (MalformedURLException ex) {
            LOG.log(Level.SEVERE, "sendDataSectionToZtreamy() - Error en la URL", ex);
        } catch (IOException ex) {
            SimulatorController.increaseZtreamyErrors();
            LOG.log(Level.SEVERE, "sendDataSectionToZtreamy() - Error I/O: {0} - Primera trama de la sección: {1} - Enviada a las: {2} - Errores: {3} / Total: {4}", new Object[]{ex.getMessage(), dataSection.getRoadSection().get(0).getTimeStamp(), Constants.dfISO8601.format(System.currentTimeMillis()), SimulatorController.getZtreamyErrors(), SimulatorController.getZtreamySends()});
            reconnectPublisher();
            // FIXME: ¿Qué hacemos con los que no se hayan podido mandar? ¿Los guardamos y los intentamos enviar después?
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "sendDataSectionToZtreamy() - Error desconocido: {0} - Primera trama de la sección: {1} - Enviada a las: {2} - Errores: {3} / Total: {4}", new Object[]{ex.getMessage(), dataSection.getRoadSection().get(0).getTimeStamp(), Constants.dfISO8601.format(System.currentTimeMillis()), SimulatorController.getZtreamyErrors(), SimulatorController.getZtreamySends()});
            // FIXME: ¿Qué hacemos con los que no se hayan podido mandar? ¿Los guardamos y los intentamos enviar después?
        }

        // Reiniciamos los acumulados.
        roadSectionList.clear();
        cummulativePositiveSpeeds = 0.0d;
        sectionDistance = 0.0d;
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

    private void reconnectPublisher() {
        publisher.close();
        try {
            this.publisher = new PublisherHC(new URL(URL), new JSONSerializer());
        } catch (MalformedURLException e) {
            // No puede pasar, porque habría pasado también en el constructor
            // y no lo ha hecho.
        }
        LOG.log(Level.INFO, "reconnectPublisher() - Publisher reconnected");
    }
}
