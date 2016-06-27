package es.jyago.hermes.location;

import es.jyago.hermes.location.detail.LocationLogDetail;
import es.jyago.hermes.person.Person;
import es.jyago.hermes.util.Constants;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import javax.xml.bind.annotation.XmlTransient;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class LocationLog implements Serializable {

    private static final long serialVersionUID = 1L;
    private Integer locationLogId;
    private Date dateLog;
    private Person person;
    private List<LocationLogDetail> locationLogDetailList;
    private String filename;
    private LocationLogDetail maximumSpeedLocation;
    private LocationLogDetail minimumHeartRateLocation;
    private LocationLogDetail maximumHeartRateLocation;
    private double avgHeartRate;
    private int duration;
    private double distance;

    public LocationLog() {
        locationLogDetailList = new ArrayList<>();
        maximumSpeedLocation = new LocationLogDetail();
        maximumHeartRateLocation = new LocationLogDetail();
        minimumHeartRateLocation = new LocationLogDetail();
    }

    public Integer getLocationLogId() {
        return locationLogId;
    }

    public void setLocationLogId(Integer locationLogId) {
        this.locationLogId = locationLogId;
    }

    public Date getDateLog() {
        return dateLog;
    }

    public void setDateLog(Date dateLog) {
        this.dateLog = dateLog;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public double getAvgHeartRate() {
        return avgHeartRate;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @XmlTransient
    public List<LocationLogDetail> getLocationLogDetailList() {
        return locationLogDetailList;
    }

    public void setLocationLogDetailList(List<LocationLogDetail> locationLogDetailList) {
        this.locationLogDetailList = locationLogDetailList;
    }

    public LocationLogDetail getMaximumHeartRateLocation() {
        return maximumHeartRateLocation;
    }

    public LocationLogDetail getMinimumHeartRateLocation() {
        return minimumHeartRateLocation;
    }

    public LocationLogDetail getMaximumSpeedLocation() {
        return maximumSpeedLocation;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    private void findMaxMinAvgValues() {

        DescriptiveStatistics hearRateStats = new DescriptiveStatistics();

        if (locationLogDetailList != null && !locationLogDetailList.isEmpty()) {
            // Para descartar los valores 0 en el ritmo cardíaco que llegan de la aplicación de SmartDriver,
            // establecemos unas pulsaciones mínimas imposibles para un ser humano.
            minimumHeartRateLocation = locationLogDetailList.get(0);
            maximumHeartRateLocation = locationLogDetailList.get(0);
            maximumSpeedLocation = locationLogDetailList.get(0);

            for (LocationLogDetail detail : locationLogDetailList) {
                // Debemos descartar los valores 0 en el ritmo cardíaco que llegan de la aplicación de SmartDriver.
                if (detail.getHeartRate() > 0) {
                    hearRateStats.addValue(detail.getHeartRate());
                    if (detail.getHeartRate() < minimumHeartRateLocation.getHeartRate()) {
                        minimumHeartRateLocation = detail;
                    }
                }
                if (detail.getHeartRate() > maximumHeartRateLocation.getHeartRate()) {
                    maximumHeartRateLocation = detail;
                }
                if (detail.getSpeed() > maximumSpeedLocation.getSpeed()) {
                    maximumSpeedLocation = detail;
                }
            }

            avgHeartRate = hearRateStats.getMean();
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(19, 29).
                append(dateLog).
                toHashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof LocationLog)) {
            return false;
        }
        LocationLog other = (LocationLog) object;

        // Dos elementos serán iguales si tienen el mismo id.
        return new EqualsBuilder().
                append(this.locationLogId, other.locationLogId).
                isEquals();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[")
                .append(Constants.df.format(this.dateLog))
                .append(", ")
                .append(this.filename)
                .append(" ->");
        if (maximumHeartRateLocation != null) {
            sb.append(" ")
                    .append(ResourceBundle.getBundle("/Bundle").getString("MaximumHeartRate"))
                    .append(": ")
                    .append(maximumHeartRateLocation.getHeartRate());
        }
        if (minimumHeartRateLocation != null) {
            sb.append(" ")
                    .append(ResourceBundle.getBundle("/Bundle").getString("MinimumHeartRate"))
                    .append(": ")
                    .append(minimumHeartRateLocation.getHeartRate());
        }
        if (maximumSpeedLocation != null) {
            sb.append(" ")
                    .append(ResourceBundle.getBundle("/Bundle").getString("MaximumSpeed"))
                    .append(": ")
                    .append(maximumSpeedLocation.getHeartRate());
        }
        sb.append("]");

        return sb.toString();
    }

    public String getDateTimeStart() {
        StringBuilder sb = new StringBuilder();
        if (dateLog != null) {
            sb.append(Constants.df.format(dateLog));
            if (locationLogDetailList != null && !locationLogDetailList.isEmpty()) {
                sb.append(" - ").append(Constants.dfTime.format(locationLogDetailList.get(0).getTimeLog()));
            }
        }
        return sb.toString();
    }
}
