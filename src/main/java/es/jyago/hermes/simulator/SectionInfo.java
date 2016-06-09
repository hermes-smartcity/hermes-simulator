package es.jyago.hermes.simulator;

import org.apache.commons.lang.time.DurationFormatUtils;

public class SectionInfo {

    public SectionInfo() {
    }

    private int distance;
    private int duration;
    private LocationInfo startLocation;
    private LocationInfo endLocation;
    private String travelMode;
    private String startAddress;
    private String endAddress;

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public LocationInfo getEndLocation() {
        return endLocation;
    }

    public void setEndLocation(LocationInfo endLocation) {
        this.endLocation = endLocation;
    }

    public LocationInfo getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(LocationInfo startLocation) {
        this.startLocation = startLocation;
    }

    public String getTravelMode() {
        return travelMode;
    }

    public void setTravelMode(String travelMode) {
        this.travelMode = travelMode;
    }

    public String getStartAddress() {
        return startAddress;
    }

    public void setStartAddress(String startAddress) {
        this.startAddress = startAddress;
    }

    public String getEndAddress() {
        return endAddress;
    }

    public void setEndAddress(String endAddress) {
        this.endAddress = endAddress;
    }

    public String getFormattedTime() {
        return DurationFormatUtils.formatDuration(duration * 1000, "HH:mm:ss", true);
    }

    public String getFormattedDistance() {
        return String.format("%.2f", (distance / 1000.0f)) + " Km.";
    }
}
