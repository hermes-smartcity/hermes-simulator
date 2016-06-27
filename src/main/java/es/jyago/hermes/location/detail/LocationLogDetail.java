package es.jyago.hermes.location.detail;

import es.jyago.hermes.location.LocationLog;
import es.jyago.hermes.util.Constants;
import java.io.Serializable;
import java.util.Date;
import java.util.ResourceBundle;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

public class LocationLogDetail implements Serializable {

    private static final long serialVersionUID = 1L;
    private Integer locationLogDetailId;
    private Date timeLog;
    private double latitude;
    private double longitude;
    private double speed;
    private int heartRate;
    private int rrTime;
    private LocationLog locationLog;
    private int secondsToBeHere;

    public LocationLogDetail() {
        this.timeLog = null;
        this.latitude = 0.0d;
        this.longitude = 0.0d;
        this.speed = 0.0d;
        this.heartRate = 0;
        this.rrTime = 0;
        this.secondsToBeHere = 0;
    }

    public LocationLogDetail(Integer locationLogDetailId) {
        this.locationLogDetailId = locationLogDetailId;
    }

    public LocationLogDetail(double latitude, double longitude, double speed, int heartRate, int secondsToBeHere) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.heartRate = heartRate;
        this.secondsToBeHere = secondsToBeHere;
    }

    public Integer getLocationLogDetailId() {
        return locationLogDetailId;
    }

    public void setLocationLogDetailId(Integer locationLogDetailId) {
        this.locationLogDetailId = locationLogDetailId;
    }

    public Date getTimeLog() {
        return timeLog;
    }

    public void setTimeLog(Date timeLog) {
        this.timeLog = timeLog;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }

    public LocationLog getLocationLog() {
        return locationLog;
    }

    public void setLocationLog(LocationLog locationLog) {
        this.locationLog = locationLog;
    }

    public int getRrTime() {
        return rrTime;
    }

    public void setRrTime(int rrTime) {
        this.rrTime = rrTime;
    }

    public int getSecondsToBeHere() {
        return secondsToBeHere;
    }

    public void setSecondsToBeHere(int secondsToBeHere) {
        this.secondsToBeHere = secondsToBeHere;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(19, 29).
                append(timeLog).
                toHashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof LocationLogDetail)) {
            return false;
        }
        LocationLogDetail other = (LocationLogDetail) object;

        // Dos elementos serán iguales si tienen el mismo id.
        return new EqualsBuilder().
                append(this.locationLogDetailId, other.locationLogDetailId).
                isEquals();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[")
                .append(this.latitude)
                .append(", ")
                .append(this.longitude)
                .append(" -> HR: ")
                .append(this.heartRate)
                .append(", S: ")
                .append(this.speed)
                .append("]");

        return sb.toString();
    }

    public String getMarkerTitle() {
        StringBuilder sb = new StringBuilder();
        sb.append(ResourceBundle.getBundle("/Bundle").getString("Time")).append(": ").append(Constants.dfTime.format(getTimeLog()));
        sb.append(" ");
        sb.append(ResourceBundle.getBundle("/Bundle").getString("HeartRate")).append(": ").append(Integer.toString(getHeartRate()));
        sb.append(" ");
        sb.append(ResourceBundle.getBundle("/Bundle").getString("Speed")).append(": ").append(Constants.df2Decimals.format(getSpeed())).append(" Km/h");
        sb.append(" (").append(getLatitude()).append(", ").append(getLongitude()).append(")");

        return sb.toString();
    }
}
