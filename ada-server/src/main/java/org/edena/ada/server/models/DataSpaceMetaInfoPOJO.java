package org.edena.ada.server.models;

import java.sql.Timestamp;
import java.util.Date;

public class DataSpaceMetaInfoPOJO {
    private String _id;
    private String name;
    private Integer sortOrder;
    private Date timeCreated;
    private String parentId;

    // Original item to preserve complex fields (dataSetMetaInfos, children)
    private DataSpaceMetaInfo originalItem;

    public DataSpaceMetaInfoPOJO() {
        this.timeCreated = new Date();
    }

    public DataSpaceMetaInfoPOJO(String name, Integer sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.timeCreated = new Date();
    }

    // Getters
    public String get_id() { return _id; }
    public String getName() { return name; }
    public Integer getSortOrder() { return sortOrder; }
    public Date getTimeCreated() { return timeCreated; }
    public String getParentId() { return parentId; }
    public DataSpaceMetaInfo getOriginalItem() { return originalItem; }

    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setName(String name) { this.name = name; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void setTimeCreated(Date timeCreated) { this.timeCreated = timeCreated; }
    public void setTimeCreated(Timestamp timeCreated) { this.timeCreated = new Date(timeCreated.getTime()); }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public void setOriginalItem(DataSpaceMetaInfo originalItem) { this.originalItem = originalItem; }

    @Override
    public String toString() {
        return name != null ? name : "DataSpaceMetaInfo";
    }
}