package es.jyago.hermes.openStreetMap;

import com.google.gson.annotations.SerializedName;

public class GeomWaySteps {

    @SerializedName("linkId")
    private Integer linkId;
    @SerializedName("maxSpeed")
    private Integer maxSpeed;
    @SerializedName("linkName")
    private String linkName;
    @SerializedName("linkType")
    private String linkType;
    @SerializedName("length")
    private Double length;
    @SerializedName("cost")
    private Double cost;
    @SerializedName("geom_way")
    private GeomWay geomWay;

    public Integer getLinkId() {
        return linkId;
    }

    public void setLinkId(Integer linkId) {
        this.linkId = linkId;
    }

    public Integer getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(Integer maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public String getLinkName() {
        return linkName;
    }

    public void setLinkName(String linkName) {
        this.linkName = linkName;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public Double getLength() {
        return length;
    }

    public void setLength(Double length) {
        this.length = length;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public GeomWay getGeomWay() {
        return geomWay;
    }

    public void setGeomWay(GeomWay geomWay) {
        this.geomWay = geomWay;
    }

}
