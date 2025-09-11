package org.edena.ada.server.models;

import java.sql.Timestamp;
import java.util.*;

public class FilterPOJO {
    private String _id;
    private String name;
    private List<Object> conditions = new ArrayList<>();
    private Boolean isPrivate = false;
    private String createdById;
    private Date timeCreated;

    public FilterPOJO() {}
    
    public FilterPOJO(String name) {
        this.name = name;
    }
    
    // Getters
    public String get_id() { return _id; }
    public String getName() { return name; }
    public List<Object> getConditions() { return conditions; }
    public Boolean getIsPrivate() { return isPrivate; }
    public String getCreatedById() { return createdById; }
    public Date getTimeCreated() { return timeCreated; }

    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setName(String name) { this.name = name; }
    public void setConditions(List<Object> conditions) { this.conditions = conditions; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }
    public void setTimeCreated(Date timeCreated) { this.timeCreated = timeCreated; }
    public void setTimeCreated(Timestamp timeCreated) { this.timeCreated = new Date(timeCreated.getTime()); }

    @Override
    public String toString() {
        return name != null ? name : "Filter";
    }
}