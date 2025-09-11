package org.edena.ada.server.models;

public class DataSetSettingPOJO {
    private String _id;
    private String dataSetId;
    private String keyFieldName;
    private String exportOrderByFieldName;
    private String defaultScatterXFieldName;
    private String defaultScatterYFieldName;
    private String defaultDistributionFieldName;
    private String defaultCumulativeCountFieldName;
    private String filterShowFieldStyle;
    private Boolean filterShowNonNullCount = false;
    private String displayItemName;
    private String storageType;
    private Boolean mongoAutoCreateIndexForProjection = false;
    private Boolean cacheDataSet = false;
    private String ownerId;
    private Boolean showSideCategoricalTree = true;
    private String customControllerClassName;
    private String description;
    private String widgetEngineClassName;
    private String customStorageCollectionName;

    // Original item to preserve complex fields (extraNavigationItems, extraExportActions)
    private DataSetSetting originalItem;

    public DataSetSettingPOJO() {}

    public DataSetSettingPOJO(String dataSetId, String storageType) {
        this.dataSetId = dataSetId;
        this.keyFieldName = "_id";
        this.storageType = storageType;
        this.filterShowNonNullCount = false;
        this.mongoAutoCreateIndexForProjection = false;
        this.cacheDataSet = false;
        this.showSideCategoricalTree = true;
    }

    // Getters
    public String get_id() { return _id; }
    public String getDataSetId() { return dataSetId; }
    public String getKeyFieldName() { return keyFieldName; }
    public String getExportOrderByFieldName() { return exportOrderByFieldName; }
    public String getDefaultScatterXFieldName() { return defaultScatterXFieldName; }
    public String getDefaultScatterYFieldName() { return defaultScatterYFieldName; }
    public String getDefaultDistributionFieldName() { return defaultDistributionFieldName; }
    public String getDefaultCumulativeCountFieldName() { return defaultCumulativeCountFieldName; }
    public String getFilterShowFieldStyle() { return filterShowFieldStyle; }
    public Boolean getFilterShowNonNullCount() { return filterShowNonNullCount; }
    public String getDisplayItemName() { return displayItemName; }
    public String getStorageType() { return storageType; }
    public Boolean getMongoAutoCreateIndexForProjection() { return mongoAutoCreateIndexForProjection; }
    public Boolean getCacheDataSet() { return cacheDataSet; }
    public String getOwnerId() { return ownerId; }
    public Boolean getShowSideCategoricalTree() { return showSideCategoricalTree; }
    public String getCustomControllerClassName() { return customControllerClassName; }
    public String getDescription() { return description; }
    public String getWidgetEngineClassName() { return widgetEngineClassName; }
    public String getCustomStorageCollectionName() { return customStorageCollectionName; }
    public DataSetSetting getOriginalItem() { return originalItem; }

    // Setters
    public void set_id(String _id) { this._id = _id; }
    public void setDataSetId(String dataSetId) { this.dataSetId = dataSetId; }
    public void setKeyFieldName(String keyFieldName) { this.keyFieldName = keyFieldName; }
    public void setExportOrderByFieldName(String exportOrderByFieldName) { this.exportOrderByFieldName = exportOrderByFieldName; }
    public void setDefaultScatterXFieldName(String defaultScatterXFieldName) { this.defaultScatterXFieldName = defaultScatterXFieldName; }
    public void setDefaultScatterYFieldName(String defaultScatterYFieldName) { this.defaultScatterYFieldName = defaultScatterYFieldName; }
    public void setDefaultDistributionFieldName(String defaultDistributionFieldName) { this.defaultDistributionFieldName = defaultDistributionFieldName; }
    public void setDefaultCumulativeCountFieldName(String defaultCumulativeCountFieldName) { this.defaultCumulativeCountFieldName = defaultCumulativeCountFieldName; }
    public void setFilterShowFieldStyle(String filterShowFieldStyle) { this.filterShowFieldStyle = filterShowFieldStyle; }
    public void setFilterShowNonNullCount(Boolean filterShowNonNullCount) { this.filterShowNonNullCount = filterShowNonNullCount; }
    public void setDisplayItemName(String displayItemName) { this.displayItemName = displayItemName; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public void setMongoAutoCreateIndexForProjection(Boolean mongoAutoCreateIndexForProjection) { this.mongoAutoCreateIndexForProjection = mongoAutoCreateIndexForProjection; }
    public void setCacheDataSet(Boolean cacheDataSet) { this.cacheDataSet = cacheDataSet; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setShowSideCategoricalTree(Boolean showSideCategoricalTree) { this.showSideCategoricalTree = showSideCategoricalTree; }
    public void setCustomControllerClassName(String customControllerClassName) { this.customControllerClassName = customControllerClassName; }
    public void setDescription(String description) { this.description = description; }
    public void setWidgetEngineClassName(String widgetEngineClassName) { this.widgetEngineClassName = widgetEngineClassName; }
    public void setCustomStorageCollectionName(String customStorageCollectionName) { this.customStorageCollectionName = customStorageCollectionName; }
    public void setOriginalItem(DataSetSetting originalItem) { this.originalItem = originalItem; }

    @Override
    public String toString() {
        return dataSetId != null ? dataSetId : "DataSetSetting";
    }
}