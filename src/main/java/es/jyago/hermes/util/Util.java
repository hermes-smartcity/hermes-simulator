package es.jyago.hermes.util;

import java.util.concurrent.TimeUnit;

public class Util {

    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
            + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    private static final String ALPHANUMERIC = "^[a-zA-Z0-9]*$";

    public static String minutesToTimeString(int minutes) {

        long hours = TimeUnit.MINUTES.toHours(minutes);
        long remainMinutes = minutes - TimeUnit.HOURS.toMinutes(hours);
        return String.format("%02d:%02d", hours, remainMinutes);
    }

    public static boolean isValidEmail(String email) {
        if (email == null || email.length() == 0) {
            return false;
        }

        return email.matches(EMAIL_PATTERN);
    }

    public static boolean isAlphaNumeric(String s) {
        return s.matches(ALPHANUMERIC);
    }

    /**
     * Implementación de la Fórmula de Haversine.
     * https://es.wikipedia.org/wiki/Fórmula_del_Haversine
     *
     * @param lat1 Latitud inicial.
     * @param lng1 Longitud inicial.
     * @param lat2 Latitud final.
     * @param lng2 Longitud final.
     * @return Distancia en metros entre los 2 puntos.
     */
    public static double distanceHaversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);
        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        // El radio de la Tierra es, aproximadamente, 6.371 Km, es decir, 6.371.000 metros.
        double dist = 6371000.0d * c;

        return dist;
    }
}
