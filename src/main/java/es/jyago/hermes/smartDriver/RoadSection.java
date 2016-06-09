package es.jyago.hermes.smartDriver;

import es.jyago.hermes.util.Constants;
import java.io.Serializable;

public class RoadSection implements Serializable {

    private Double latitude;
    private transient long time; // Para que no la serialice el GSON.
    private Double speed;
    private Double longitude;
    private Integer accuracy;
    
    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public String getTimeStamp() {
        return Constants.dfISO8601.format(time);
    }

    public void setTime(long time) {
        this.time = time;
    }
    
    public long getTime() {
        return time;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Integer accuracy) {
        this.accuracy = accuracy;
    }

}
