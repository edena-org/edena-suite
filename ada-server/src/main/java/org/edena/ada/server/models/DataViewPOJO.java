package org.edena.ada.server.models;

import java.sql.Timestamp;
import java.util.*;

public class DataViewPOJO {
    private String _id;
    private String name;
    private List<Object> filters = new ArrayList<>();
    private List<String> filterIds = new ArrayList<>();
    private List<String> filterOrIdTypes = new ArrayList<>(); // "filter" or "id" to preserve order
    private List<String> tableColumnNames = new ArrayList<>();
    private String initTableSortFieldName;
    private String initTableSortType;
    private List<Object> widgetSpecs = new ArrayList<>();
    private Integer elementGridWidth = 3;
    private Boolean default_ = false; // 'default' is a reserved keyword in Java
    private Boolean isPrivate = false;
    private String generationMethod;
    private String createdById;
    private Date timeCreated;

    public DataViewPOJO() {}
    
    public DataViewPOJO(String name) {
        this.name = name;
        this.timeCreated = new Date();
    }
    
    // Getters
    public String get_id() { return _id; }
    public String getName() { return name; }
    public List<Object> getFilters() { return filters; }
    public List<String> getFilterIds() { return filterIds; }
    public List<String> getFilterOrIdTypes() { return filterOrIdTypes; }
    public List<String> getTableColumnNames() { return tableColumnNames; }
    public String getInitTableSortFieldName() { return initTableSortFieldName; }
    public String getInitTableSortType() { return initTableSortType; }
    public List<Object> getWidgetSpecs() { return widgetSpecs; }
    public Integer getElementGridWidth() { return elementGridWidth; }
    public Boolean getDefault() { return default_; } // Map Java 'default_' to Scala 'default'
    public Boolean getIsPrivate() { return isPrivate; }
    public String getGenerationMethod() { return generationMethod; }
    public String getCreatedById() { return createdById; }
    public Date getTimeCreated() { return timeCreated; }

    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setName(String name) { this.name = name; }
    public void setFilters(List<Object> filters) { this.filters = filters; }
    public void setFilterIds(List<String> filterIds) { this.filterIds = filterIds; }
    public void setFilterOrIdTypes(List<String> filterOrIdTypes) { this.filterOrIdTypes = filterOrIdTypes; }
    public void setTableColumnNames(List<String> tableColumnNames) { this.tableColumnNames = tableColumnNames; }
    public void setInitTableSortFieldName(String initTableSortFieldName) { this.initTableSortFieldName = initTableSortFieldName; }
    public void setInitTableSortType(String initTableSortType) { this.initTableSortType = initTableSortType; }
    public void setWidgetSpecs(List<Object> widgetSpecs) { this.widgetSpecs = widgetSpecs; }
    public void setElementGridWidth(Integer elementGridWidth) { this.elementGridWidth = elementGridWidth; }
    public void setDefault(Boolean default_) { this.default_ = default_; } // Map Scala 'default' to Java 'default_'
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
    public void setGenerationMethod(String generationMethod) { this.generationMethod = generationMethod; }
    public void setCreatedById(String createdById) { this.createdById = createdById; }
    public void setTimeCreated(Date timeCreated) { this.timeCreated = timeCreated; }
    public void setTimeCreated(Timestamp timeCreated) { this.timeCreated = new Date(timeCreated.getTime()); }

    @Override
    public String toString() {
        return name != null ? name : "DataView";
    }
}