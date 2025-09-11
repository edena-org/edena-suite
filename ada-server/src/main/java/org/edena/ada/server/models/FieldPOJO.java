package org.edena.ada.server.models;

import java.util.*;

public class FieldPOJO {
    private String name;
    private String label;
    private String fieldType = "String";
    private Boolean isArray = false;
    private Map<String, String> enumValues = new HashMap<>();
    private Integer displayDecimalPlaces;
    private String displayTrueValue;
    private String displayFalseValue;
    private String displayAsURLType;
    private List<String> aliases = new ArrayList<>();
    private String categoryId;

    // Original item to preserve complex fields (category)
    private Field originalItem;
    
    public FieldPOJO() {}
    
    public FieldPOJO(String name) {
        this.name = name;
    }
    
    // Getters
    public String getName() { return name; }
    public String getLabel() { return label; }
    public String getFieldType() { return fieldType; }
    public Boolean getIsArray() { return isArray; }
    public Map<String, String> getEnumValues() { return enumValues; }
    public Integer getDisplayDecimalPlaces() { return displayDecimalPlaces; }
    public String getDisplayTrueValue() { return displayTrueValue; }
    public String getDisplayFalseValue() { return displayFalseValue; }
    public String getDisplayAsURLType() { return displayAsURLType; }
    public List<String> getAliases() { return aliases; }
    public String getCategoryId() { return categoryId; }
    public Field getOriginalItem() { return originalItem; }
    
    // Setters
    public void setName(String name) { this.name = name; }
    public void setLabel(String label) { this.label = label; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public void setIsArray(Boolean isArray) { this.isArray = isArray; }
    public void setEnumValues(Map<String, String> enumValues) { this.enumValues = enumValues; }
    public void setDisplayDecimalPlaces(Integer displayDecimalPlaces) { this.displayDecimalPlaces = displayDecimalPlaces; }
    public void setDisplayTrueValue(String displayTrueValue) { this.displayTrueValue = displayTrueValue; }
    public void setDisplayFalseValue(String displayFalseValue) { this.displayFalseValue = displayFalseValue; }
    public void setDisplayAsURLType(String displayAsURLType) { this.displayAsURLType = displayAsURLType; }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public void setOriginalItem(Field originalItem) { this.originalItem = originalItem; }
    
    public String getLabelOrElseName() {
        return label != null ? label : name;
    }
}
