// filterId: is not an element id but a persisted id of the filter if any
function activateDataSetFilter(filterElement, jsonConditions, filterId, submitAjaxFun, getFieldsUrl, listFiltersUrl) {
    var saveFilterAjaxFun = function(filter) {
        var filterJson = JSON.stringify(filter)
        filterJsRoutes.org.edena.ada.web.controllers.dataset.FilterDispatcher.saveAjax(filterJson).ajax( {
            success: function(data) {
                showMessage("Filter '" + filter.name + "' successfully saved.");
            },
            error: showErrorResponse
        });
    }

    $(filterElement).multiCategoryFilter({
        jsonConditions: jsonConditions,
        getFieldsUrl: getFieldsUrl,
        submitAjaxFun: submitAjaxFun,
        listFiltersUrl: listFiltersUrl,
        saveFilterAjaxFun: saveFilterAjaxFun,
        filterSubmitParamName: "filterOrId",
        filterId: filterId,
        typeaheadMinLength: 2
    })

    addAllowedValuesUpdateForFilter(filterElement)
    addDragAndDropSupportForFilter(filterElement)
}

function addDragAndDropSupportForFilter(filterElement) {
    $(filterElement).find(".filter-part").on('dragover', false).on('drop', function (ev) {
        $(filterElement).find("#conditionPanel").removeClass("dragged-over")

        ev.preventDefault();
        var transfer = ev.originalEvent.dataTransfer;
        var id = transfer.getData("id");
        var text = transfer.getData("text");
        var type = transfer.getData("type");

        if (type.startsWith("field")) {
            $(filterElement).multiFilter("showAddConditionModalForField", id, text)
        }
    }).on("dragover", function (ev) {
        var transfer = ev.originalEvent.dataTransfer;
        var type = transfer.getData("type");

        if (type.startsWith("field")) {
            $(filterElement).find("#conditionPanel").addClass("dragged-over")
        }
    }).on("dragleave", function () {
        $(filterElement).find("#conditionPanel").removeClass("dragged-over")
    })
}

function saveFilterToView(viewId) {
    var filterElements = $("#filtersTr").find(".filter-div").toArray();

    var filterOrIds = filterElements.map(function(filterElement, index) {
        return $(filterElement).multiFilter('getIdOrModel');
    });

    dataViewJsRoutes.org.edena.ada.web.controllers.dataset.DataViewDispatcher.saveFilter(
        viewId,
        JSON.stringify(filterOrIds)
    ).ajax( {
        success: function() {
            showMessage("Filter successfully added to the view.");
        },
        error: showErrorResponse
    });
}

function refreshViewForFilter(widgetEngine, viewId, filterOrId, filterElement, widgetGridElementWidth, enforceWidth, tableSelection) {
    var index = $("#filtersTr").find(".filter-div").index(filterElement);

    var counts = $("#filtersTr").find(".count-hidden").map(function(index, element) {
        return parseInt($(element).val());
    }).toArray();

    // add the old count to the params
    var totalCount = counts.reduce(function (a, b) {return a + b;}, 0);
    var oldCountDiff = totalCount - counts[index];

    dataSetJsRoutes.org.edena.ada.web.controllers.dataset.DataSetDispatcher.getViewElementsAndWidgetsCallback(viewId, "", filterOrId, oldCountDiff, tableSelection).ajax( {
        success: function(data) {
            hideErrors();

            // filter
            filterElement.multiFilter("replaceModelAndPanel", data.filterModel, data.conditionPanel);
            addDragAndDropSupportForFilter(filterElement)

            // display count
            var countDisplayElement = filterElement.closest(".row").parent().find(".count-div")
            countDisplayElement.html("<h3>" + data.count + "</h3>");

            // (hidden) count
            var countHiddenElement = filterElement.parent().find(".count-hidden")
            countHiddenElement.val(data.count);

            // page header
            $(".page-header").html("<h3>" + data.pageHeader + "</h3>");

            // table
            var tableElement = $("#tablesTr").find(".table-div:eq(" + index + ")")
            tableElement.html(data.table);

            // widgets
            var widgetsDiv = $("#widgetsTr > td:eq(" + index + ")")
            updateWidgetsFromCallback(widgetEngine, data.widgetsCallbackId, widgetsDiv, filterElement, widgetGridElementWidth, enforceWidth)
        },
        error: function(data) {
            showErrorResponse(data)
            filterElement.multiFilter("rollbackModelOnError");
        }
    });
}

function addNewViewColumn(widgetEngine, viewId, widgetGridElementWidth, enforceWidth, activateFilter) {
    // total count
    var totalCount = getViewTotalCount();

    dataSetJsRoutes.org.edena.ada.web.controllers.dataset.DataSetDispatcher.getNewFilterViewElementsAndWidgetsCallback(viewId, "", totalCount).ajax( {
        success: function(data) {
            // filter
            var filterTd = $("<td style='padding-left: 10px; vertical-align:top'>")
            filterTd.html(data.countFilter)
            $("#filtersTr").append(filterTd)
            var filterElement = filterTd.find(".filter-div")
            activateFilter(filterElement, []);

            // page header
            $(".page-header").html("<h3>" + data.pageHeader + "</h3>");

            // widgets
            var widgetTd = $("<td style='vertical-align:top'>")
            $("#widgetsTr").append(widgetTd)

            widgetEngine.refresh();

            // table
            var tableTd = $("<td style='padding-left: 10px; padding-right: 10px; vertical-align:top'>")
            var tableDiv = $("<div class='table-div'>")
            tableTd.append(tableDiv)
            $("#tablesTr").append(tableTd)
            $(tableDiv).html(data.table);

            // get widgets from callback
            updateWidgetsFromCallback(widgetEngine, data.widgetsCallbackId, widgetTd, filterElement, widgetGridElementWidth, enforceWidth)

            showMessage("New column/filter successfully added to the view.")
        },
        error: showErrorResponse
    });
}

function getViewTotalCount() {
    var counts = $("#filtersTr").find(".count-hidden").map(function(index, element) {
        return parseInt($(element).val());
    }).toArray();

    // total count
    return counts.reduce(function (a, b) {return a + b;}, 0);
}

function addAllowedValuesUpdateForFilter(filterElement) {
    $(filterElement).find("#fieldNameTypeahead").on('typeahead:select', function (e, field) {
        dataSetJsRoutes2.org.edena.ada.web.controllers.dataset.DataSetDispatcher.getFieldTypeWithAllowedValues(field.key).ajax({
            success: function (data) {
                updateFilterValueElement($(filterElement), data)
            },
            error: showErrorResponse
        });
    });
}

/////////////
// Widgets //
/////////////

function updateWidgetsFromCallback(widgetEngine, callbackId, widgetsDiv, filterElement, defaultElementWidth, enforceWidth, successMessage) {
    widgetsDiv.html("")
    addSpinner(widgetsDiv, "margin-bottom: 20px;")

    dataSetJsRoutes.org.edena.ada.web.controllers.dataset.DataSetDispatcher.getWidgets().ajax( {
        data: {
            "callbackId": callbackId
        },
        success: function(data) {
            if (successMessage) showMessage(successMessage)

            var widgets = data[0]

            var row = $("<div class='row'>")
            $.each(widgets, function (j, widget) {
                row.append(widgetEngine.widgetDiv(widget, defaultElementWidth, enforceWidth))
            })
            widgetsDiv.html(row)
            $.each(widgets, function (j, widget) {
                widgetEngine.plot(widget, filterElement)
            })
        },
        error: function(data) {
            widgetsDiv.html("")
            hideMessages();
            showErrorResponse(data)
        }
    });
}

function updateAllWidgetsFromCallback(widgetEngine, callbackId, defaultElementWidth) {
    dataSetJsRoutes.org.edena.ada.web.controllers.dataset.DataSetDispatcher.getWidgets().ajax( {
        data: {
            "callbackId": callbackId
        },
        success: function(data) {
            $("#widgetsTr").html("")
            $.each(data, function (i, widgets) {
                var td = $("<td style='vertical-align:top'>")
                var row = $("<div class='row'>")
                $.each(widgets, function (j, widget) {
                    row.append(widgetEngine.widgetDiv(widget, defaultElementWidth))
                })
                td.append(row)
                $("#widgetsTr").append(td)
            })
            var filterElements = $("#filtersTr").find(".filter-div").toArray();
            $.each(data, function (i, widgets) {
                var filterElement = filterElements[i];
                $.each(widgets, function (j, widget) {
                    widgetEngine.plot(widget, filterElement)
                })
            })
        },
        error: function(data){
            $("#widgetsTr").html("")
            showErrorResponse(data)
        }
    });
}

///////////
// Table //
///////////

function generateTable(parentTableDiv, filterElement, fieldNames, showSuccessMessage) {
    var filterOrId = filterElement.multiFilter('getIdOrModel')
    var filterOrIdJson = JSON.stringify(filterOrId);

    if (fieldNames.length == 0) {
        showError("Table cannot be generated. No fields specified.");
    } else if (fieldNames.length > 50) {
        showError("Table cannot be generated. The maximum number of fields allowed is 50 but " + fieldNames.length + " were given.");
    } else {
        dataSetJsRoutes.org.edena.ada.web.controllers.dataset.DataSetDispatcher.generateTableWithFilter(0, "", fieldNames, filterOrIdJson).ajax({
            success: function (data) {
                // filter
                filterElement.multiFilter("replaceModelAndPanel", data.filterModel, data.conditionPanel);

                // table
                var tableDiv = parentTableDiv.find(".table-div")
                if (tableDiv.length == 0) {
                    parentTableDiv.html("<div class='table-div'></div>")
                    tableDiv = parentTableDiv.find(".table-div")
                }
                tableDiv.html(data.table)
                if (showSuccessMessage) {
                    showMessage("Table generation finished.")
                }
            },
            error: function (data) {
                parentTableDiv.html("")
                showErrorResponse(data);
            }
        })

        hideErrors();
        if (showSuccessMessage) {
            showMessage("Table generation for " + fieldNames.length + " fields launched.")
        }
    }
}

function showJsonFieldValueAux(event) {
    event.preventDefault();
    event.stopPropagation();

    const link = $(event.target).parent()

    const rowId = link.attr("data-row-id")
    const fieldName = link.attr("data-field-name")
    const fieldLabel = link.attr("data-field-label")
    const isArray = link.attr("data-field-is_array") == "true"

    showJsonFieldValue(rowId, fieldName, fieldLabel, isArray)
}

function showJsonFieldValue(id, fieldName, fieldLabel, isArray) {
    dataSetJsRoutes.org.edena.ada.web.controllers.dataset.DataSetDispatcher.getFieldValue(id, fieldName).ajax( {
        success: function(data) {
            var title =  fieldLabel
            var size = 0
            var content = "<p>No data to show.</p>"

            if (data) {
                content = JSON.stringify(data, null, "\t").replace(/\t/g, '&nbsp;&nbsp;&nbsp;&nbsp;').replace(/\n/g, '</br>')
                size = data.length
            }

            if (isArray)
                title = title + ": Array (Size " + size + ")"

            $('#jsonModal #jsonModalBody').html(content)
            $('#jsonModal .modal-title').html(title)
            $('#jsonModal').modal();
        },
        error: showErrorResponse
    });
}

function showArrayFieldChartAux(widgetEngine, event) {
    event.preventDefault();
    event.stopPropagation();

    const link = $(event.target).parent()

    const rowId = link.attr("data-row-id")
    const fieldName = link.attr("data-field-name")
    const fieldLabel = link.attr("data-field-label")

    showArrayFieldChart(widgetEngine, rowId, fieldName, fieldLabel)
}

function showArrayFieldChart(widgetEngine, id, fieldName, fieldLabel) {
    dataSetJsRoutes.org.edena.ada.web.controllers.dataset.DataSetDispatcher.getFieldValue(id, fieldName).ajax( {
        success: function(data) {
            $("#lineChartDiv").html("")

            var series = []
            // TODO: do we want to use a special point format?
            const pointFormat = '<span style="color:{point.color}">{point.x:.2f}</span>: <b>{point.y:.2f}</b><br/>'

            if (data && data.length > 0) {
                const flattenData = $.map(data, function (item, i) {
                    return flatten(item)
                });

                const firstItem = flattenData[0]

                const numericKeys = Object.keys(firstItem).filter(function(key) {
                    return !isNaN(firstItem[key])
                });

                series = $.map(numericKeys, function (key, j) {
                    const seriesData = $.map(flattenData, function (item, i) {
                        return [[i, item[key]]]
                    });
                    return [[key, seriesData]]
                });
            }

            $('#lineChartArrayModal').modal("show");

            $('#lineChartArrayModal').on('shown.bs.modal', function (e) {
                const widget = {
                    concreteClass: "org.edena.ada.web.models.LineWidget",
                    title: fieldLabel,
                    xAxisCaption: "Point",
                    yAxisCaption: "Value",
                    data: series,
                    displayOptions: {
                        gridWidth: 12,
                        height: 450
                    }
                }

                widgetEngine.plotForElement("lineChartDiv", widget)
            })
        },
        error: showErrorResponse
    });
}

function followUrlLink(event, method, url, params) {
    event.preventDefault();
    event.stopPropagation();

    submit(method, url, params);
}