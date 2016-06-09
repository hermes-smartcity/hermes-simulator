package es.jyago.hermes.location.detail;

import es.jyago.hermes.location.LocationLog;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;


@Entity
@Table(name = "location_log_detail")
@XmlRootElement
@NamedQueries({
    @NamedQuery(name = "LocationLogDetail.findAll", query = "SELECT l FROM LocationLogDetail l"),
    @NamedQuery(name = "LocationLogDetail.findByLocationLogDetailId", query = "SELECT l FROM LocationLogDetail l WHERE l.locationLogDetailId = :locationLogDetailId"),
    @NamedQuery(name = "LocationLogDetail.findByTimeLog", query = "SELECT l FROM LocationLogDetail l WHERE l.timeLog = :timeLog"),
    @NamedQuery(name = "LocationLogDetail.findByLatitude", query = "SELECT l FROM LocationLogDetail l WHERE l.latitude = :latitude"),
    @NamedQuery(name = "LocationLogDetail.findByLongitude", query = "SELECT l FROM LocationLogDetail l WHERE l.longitude = :longitude"),
    @NamedQuery(name = "LocationLogDetail.findBySpeed", query = "SELECT l FROM LocationLogDetail l WHERE l.speed = :speed"),
    @NamedQuery(name = "LocationLogDetail.findByHeartRate", query = "SELECT l FROM LocationLogDetail l WHERE l.heartRate = :heartRate")})
public class LocationLogDetail implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "location_log_detail_id")
    private Integer locationLogDetailId;
    @Basic(optional = false)
    @NotNull
    @Column(name = "time_log")
    @Temporal(TemporalType.TIME)
    private Date timeLog;
    @Basic(optional = false)
    @NotNull
    @Column(name = "latitude")
    private double latitude;
    @Basic(optional = false)
    @NotNull
    @Column(name = "longitude")
    private double longitude;
    // @Max(value=?)  @Min(value=?)//if you know range of your decimal fields consider using these annotations to enforce field validation
    @Basic(optional = false)
    @NotNull
    @Column(name = "speed")
    private double speed;
    @Basic(optional = false)
    @NotNull
    @Column(name = "heart_rate")
    private int heartRate;
    @Basic(optional = true)
    @Column(name = "rr_time")
    private int rrTime;
    @JoinColumn(name = "location_log_id", referencedColumnName = "location_log_id")
    @ManyToOne(optional = false)
    private LocationLog locationLog;

    public LocationLogDetail() {
        this.timeLog = null;
        this.latitude = 0.0d;
        this.longitude = 0.0d;
        this.speed = 0.0d;
        this.heartRate = 0;
        this.rrTime = 0;
    }

    public LocationLogDetail(Integer locationLogDetailId) {
        this.locationLogDetailId = locationLogDetailId;
    }

    public LocationLogDetail(Integer locationLogDetailId, Date timeLog, double latitude, double longitude, double speed, int heartRate) {
        this.locationLogDetailId = locationLogDetailId;
        this.timeLog = timeLog;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.heartRate = heartRate;
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

}
