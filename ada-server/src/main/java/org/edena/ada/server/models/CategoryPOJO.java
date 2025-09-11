package org.edena.ada.server.models;

import java.util.*;

public class CategoryPOJO {
    private String _id;
    private String name;
    private String label;
    private String parentId;

    private Category originalItem;

    public CategoryPOJO() {}

    public CategoryPOJO(String name) {
        this.name = name;
    }

    // Getters
    public String get_id() { return _id; }
    public String getName() { return name; }
    public String getLabel() { return label; }
    public String getParentId() { return parentId; }
    public Category getOriginalItem() { return originalItem; }

    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setName(String name) { this.name = name; }
    public void setLabel(String label) { this.label = label; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public void setOriginalItem(Category originalItem) { this.originalItem = originalItem; }

    public String getLabelOrElseName() {
        return label != null ? label : name;
    }

    @Override
    public String toString() {
        return name;
    }
}