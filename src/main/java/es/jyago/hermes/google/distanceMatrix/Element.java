
package es.jyago.hermes.google.distanceMatrix;

import es.jyago.hermes.google.directions.Distance;
import es.jyago.hermes.google.directions.Duration;

public class Element {

    private Distance distance;
    private Duration duration;

    /**
     * 
     * @return
     *     The distance
     */
    public Distance getDistance() {
        return distance;
    }

    /**
     * 
     * @param distance
     *     The distance
     */
    public void setDistance(Distance distance) {
        this.distance = distance;
    }

    /**
     * 
     * @return
     *     The duration
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * 
     * @param duration
     *     The duration
     */
    public void setDuration(Duration duration) {
        this.duration = duration;
    }
}
