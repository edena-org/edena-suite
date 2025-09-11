package org.edena.ada.server.models;

import java.sql.Timestamp;
import java.util.*;

public class DataSetMetaInfoPOJO {
    private String _id;
    private String id;
    private String name;
    private String description;
    private Integer sortOrder = 0;
    private Boolean hide = false;
    private String dataSpaceId;
    private Date timeCreated;
    private String sourceDataSetId;
    
    public DataSetMetaInfoPOJO() {
        this.timeCreated = new Date();
    }
    
    public DataSetMetaInfoPOJO(String id, String name) {
        this();
        this.id = id;
        this.name = name;
    }
    
    // Getters
    public String get_id() { return _id; }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Integer getSortOrder() { return sortOrder; }
    public Boolean getHide() { return hide; }
    public String getDataSpaceId() { return dataSpaceId; }
    public Date getTimeCreated() { return timeCreated; }
    public String getSourceDataSetId() { return sourceDataSetId; }
    
    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void setHide(Boolean hide) { this.hide = hide; }
    public void setDataSpaceId(String dataSpaceId) { this.dataSpaceId = dataSpaceId; }
    public void setTimeCreated(Date timeCreated) { this.timeCreated = timeCreated; }
    public void setTimeCreated(Timestamp timeCreated) { this.timeCreated = new Date(timeCreated.getTime()); }
    public void setSourceDataSetId(String sourceDataSetId) { this.sourceDataSetId = sourceDataSetId; }
    
    @Override
    public String toString() {
        return name != null ? name : "DataSetMetaInfo";
    }
}