package es.jyago.hermes.simulator;

public class TrackInfo {

    private final SectionInfo summary;
    private int totalLocations;
    private double averageLocationsDistance;
    private double maximumLocationsDistance;

    public TrackInfo() {
        summary = new SectionInfo();
        totalLocations = 0;
        averageLocationsDistance = 0.0f;
        maximumLocationsDistance = 0.0f;
    }

    public SectionInfo getSummary() {
        return summary;
    }

    public int getTotalLocations() {
        return totalLocations;
    }

    public void setTotalLocations(int totalLocations) {
        this.totalLocations = totalLocations;
    }

    public double getAverageLocationsDistance() {
        return averageLocationsDistance;
    }

    public void setAverageLocationsDistance(double averageLocationsDistance) {
        this.averageLocationsDistance = averageLocationsDistance;
    }

    public double getMaximumLocationsDistance() {
        return maximumLocationsDistance;
    }

    public void setMaximumLocationsDistance(double maximumLocationsDistance) {
        this.maximumLocationsDistance = maximumLocationsDistance;
    }
}
