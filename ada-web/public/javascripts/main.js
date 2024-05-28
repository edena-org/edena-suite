// submit synchronously to a provided URL (action) and parameters.
function submit(method, action, parameters) {
  var form = $('<form></form>');

  form.attr("method", method);
  form.attr("action", action);

  if (parameters)
    addParams(form, parameters)

  const csfrToken = $('input[name="csrfToken"]').val()

  if (csfrToken)
    addParams(form, {"csrfToken" : csfrToken})

  $(document.body).append(form);
  form.submit();
}

function addParams(form, parameters) {
  $.each(parameters, function(key, value) {
    function addField(val) {
      var field = $('<input></input>');

      field.attr("type", "hidden");
      field.attr("name", key);
      field.attr("value", val);

      form.append(field);
    }

    if (Array.isArray(value)) {
      $.each(value, function(index, val) {
        addField(val);
      });
    } else {
      addField(value);
    }
  });
}

function getQueryParams(qs) {
  qs = qs.split('+').join(' ');
  qs = qs.split("?").slice(1).join("?");

  var params = {},
    tokens,
    re = /[?&]?([^=]+)=([^&]*)/g;


  while (tokens = re.exec(qs)) {
    var paramName = decodeURIComponent(tokens[1]).replace("amp;", "");
    var value = decodeURIComponent(tokens[2]);
    var existingValue = params[paramName];

    if (existingValue) {
      if (Array.isArray(existingValue)) {
        existingValue.push(value)
        params[paramName] = existingValue;
      } else {
        params[paramName] = [existingValue, value]
      }
    } else
      params[paramName] = value;
  }

  return params;
}

function addUrlParm(url, name, value) {
  var re = new RegExp("([?&]" + name + "=)[^&]+", "");

  function add(sep) {
    url += sep + name + "=" + encodeURIComponent(value);
  }

  function change() {
    url = url.replace(re, "$1" + encodeURIComponent(value));
  }
  if (url.indexOf("?") === -1) {
    add("?");
  } else {
    if (re.test(url)) {
      change();
    } else {
      add("&");
    }
  }

  return url;
}

function getCoreURL(url) {
  var index = url.indexOf("?")
  if (url.indexOf("?") != -1) {
    return url.substring(0, index)
  }
  return url
}

function registerMessageEventSource(url) {
//  if (!!window.EventSource) {
  if (!window.messageSource)
    window.messageSource = new self.EventSource(url, { withCredentials: true });
  window.messageSource.onmessage = function (e) {
    if (e.data) {
      var json = $.parseJSON(e.data);
      prependTrollboxJsonMessage(json, true);
      $("#trollmessagebox").scrollTop($(document).height());
    }
  };

  window.messageSource.addEventListener('error', function (e) {
    if (e.eventPhase == EventSource.CLOSED) {
      console.log("Connection was closed on error: ");
      console.log(e)
    } else {
      console.log("Error occurred while streaming: ");
      console.log(e)
    }
  }, false);
  //setTimeout(function() {
  //    console.log("Closing source");
  //    source.close()
  //}, 3000)
  //} else {
  //  console.log("No support for HTML-5 Event Source")
  //  prependTrollboxMessage("", "", "Sorry. This browser doesn't seem to support HTML5-based messaging. Check <a href='http://html5test.com/compare/feature/communication-eventSource.html'>html5test</a> for browser compatibility.");
  //}
}

function prependTrollboxJsonMessage(jsonMessage, isAdmin, fadeIn) {
  var createdBy = jsonMessage.createdByUser
  var isUserAdmin = jsonMessage.isUserAdmin
  var timeCreated = jsonMessage.timeCreated
  var content = Autolinker.link(jsonMessage.content);
  var date = new Date(timeCreated);
  prependTrollboxMessage(createdBy, date.toISOString(), content, isUserAdmin, fadeIn);
}

function prependTrollboxMessage(author, timeCreated, text, isAdmin, fadeIn) {
  var messageBlock = null
  if(author) {
    if(isAdmin) {
      messageBlock = $('<div class="alert alert-dismissable alert-success" data-toggle="tooltip" data-placement="top" title="Published at: ' + timeCreated + '">')
    } else {
      messageBlock = $('<div class="alert alert-dismissable" data-toggle="tooltip" data-placement="top" title="Published at: ' + timeCreated + '">')
    }
    messageBlock.append('<span class="glyphicon glyphicon-user"></span>&nbsp;')
    messageBlock.append('<strong>' + author + ':</strong> &nbsp;')
  } else {
    messageBlock = $('<div class="alert alert-dismissable alert-info" data-toggle="tooltip" data-placement="top" title="Published at: ' + timeCreated + '">')
    messageBlock.append('<span class="glyphicon glyphicon-king"></span>&nbsp;')
    messageBlock.append('<strong>Ada:</strong> &nbsp;')
  }
  messageBlock.append(text)
  if(fadeIn) {
    messageBlock.hide();
  }
  $('#trollmessagebox').append(messageBlock);
  if(fadeIn) {
    messageBlock.fadeIn('2000');
  }
}

function showHideMessageBox() {
  if ($("#contentDiv").hasClass("col-md-8-25")) {
    $("#contentDiv").removeClass("col-md-8-25").addClass("col-md-10")
    $("#messageBoxDiv").hide();
    $("#showHideMessageBoxSpan").html("&#8612;")

    if (typeof widgetEngine !== "undefined")
      widgetEngine.refresh();
  } else {
    $("#contentDiv").removeClass("col-md-10").addClass("col-md-8-25")
    $("#messageBoxDiv").show();
    $("#showHideMessageBoxSpan").html("&#8614;")

    if (typeof widgetEngine !== "undefined")
      widgetEngine.refresh();
  }
}

function showMessage(text, showWellDone = true) {
  $('#messageDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var messageBlock = $('<div class="alert alert-dismissable alert-success">')
  messageBlock.append(closeX)
  if (showWellDone) {
    messageBlock.append('<strong>Well done!</strong> ')
  }
  messageBlock.append(text.replaceAll("\n", "<br>"))
  $('#messageDiv').html(messageBlock);
  $('#messageDiv').fadeIn('2000');

  addMessageDividerIfNeeded();
  registerMessageDividerRemoval();
}

function showErrorResponse(data) {
  if (data.responseText) {
    showError(data.responseText)
  } else {
    if (data.status == 401)
      showError("Access denied! We're sorry, but you are not authorized to perform the requested operation.")
    else
      showError(data.status + ": " + data.statusText)
  }
}

function showError(message) {
  $('#errorDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var innerDiv = $('<div class="alert alert-dismissable alert-danger">');
  innerDiv.append(closeX);
  innerDiv.append(message);
  $('#errorDiv').html(innerDiv);
  $('#errorDiv').fadeIn('2000');
  addMessageDividerIfNeeded();
  registerMessageDividerRemoval();
}

function hideMessages() {
  $('#messageDiv').fadeOut('2000');
  $('#messageDiv').html('');
}

function showErrors(errors) {
  if (errors.length > 0) {
    var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
    $('#errorDiv').hide();
    $('#errorDiv').html("");
    $.each(errors, function(index, error) {
      var innerDiv = $('<div class="alert alert-dismissable alert-danger">');
      innerDiv.append(closeX);
      innerDiv.append(error.message);
      $('#errorDiv').append(innerDiv);
    });
    $('#errorDiv').fadeIn('2000');
    addMessageDividerIfNeeded();
    registerMessageDividerRemoval();
  }
}

function addMessageDividerIfNeeded() {
  var messagesCount = $('#messageContainer').find('.alert-dismissable').length
  if ($('#messageContainer .messageDivider').length == 0 && messagesCount > 0) {
    $('#messageContainer').append('<hr class="messageDivider"/>')
  }
}

function registerMessageDividerRemoval() {
  $('#messageContainer .alert-dismissable .close').click(function () {
    var messagesCount = $('#messageContainer').find('.alert-dismissable').length
    if (messagesCount == 1) {
      $('#messageContainer .messageDivider').remove();
    }
  });
}

function hideErrors() {
  $('#errorDiv').fadeOut('2000');
  $('#errorDiv').html('');
}

function getCheckedTableIds(tableId, objectIdName) {
  var ids = []
  $('#' + tableId + ' tbody tr').each(function() {
    var id = getRowId($(this), objectIdName)
    var checked = $(this).find("td input.table-selection[type=checkbox]").is(':checked');
    if (checked) {
      ids.push(id)
    }
  });
  return ids
};

function getNearestRowId(element, objectIdName) {
  var row = element.closest('tr');
  return getRowId(row, objectIdName)
}

function getRowId(rowElement, objectIdName) {
  var idElement = rowElement.find('#' + objectIdName)
  var id = idElement.text().trim();
  if (!id) {
    id = idElement.val().trim();
  }
  return id
}

function handleModalButtonEnterPressed(modalName, action, hideOnEnter) {
  $("#" + modalName).keypress(function (e) {
    if (e.keyCode == 13) {
      e.preventDefault();
      action()
      if(hideOnEnter) {
        $("#" + modalName).modal("hide")
      }
    }
  });

  $("#" + modalName + " .btn-primary").click(action);
}

function loadNewContent(url, elementId, data, callType) {
  $.ajax({
    url: url,
    data: data,
    type: (callType) ? callType : "GET",
    success: function (html) {
      $("#" + elementId).html(html);
    },
    error: function(data) {
      showErrorResponse(data)
    }
  });
}

function loadNewTableContent(element, url, data, callType) {
  $.ajax({
    url: url,
    data: data,
    type: (callType) ? callType : "GET",
    success: function(content) {
      var tableDiv = element.closest(".table-div")
      $(tableDiv).html(content);
    },
    error: showErrorResponse
  });
}

function activateRowClickable() {
  $(function() {
    $(".clickable-row").click(function () {
      window.document.location = $(this).data("href");
    });
    $(".no-rowClicked").click(function (event) {
      event.stopPropagation();
    });
  });
}

function preventEventPropagation(event) {
  event.stopPropagation();
}

function getModalValues(modalElementId) {
  var values = {};
  $('#' + modalElementId +' input, #' + modalElementId +' select, #' + modalElementId +' textarea').each(function () {
    const id = (this.id) || (this.name)
    if (id) {
      const value = ($(this).attr('type') != "checkbox") ? $(this).val() : $(this).is(':checked')

      if (id.endsWith("[]")) {
        // is array
        var array = values[id]
        if (array == null) {
          array = []
          values[id] = array
        }
        array.push(value)
      } else {
        values[id] = value
      }
    }
  })

  return values;
}

function showMLOutput(evalRates) {
  $("#outputDiv").html("");

  var header = ["Metrics", "Training", "Test"]
  var showReplicationRates = evalRates[0].replicationEvalRate != null
  if (showReplicationRates)
    header = header.concat("Replication")

  function float3(value) {
    return (value) ? value.toFixed(3) : ""
  }

  var rowData = evalRates.map(function(item) {
    var data = [item.metricName, float3(item.trainEvalRate), float3(item.testEvalRate)]
    if (showReplicationRates)
      data = data.concat(float3(item.replicationEvalRate))
    return data
  });

  var table = createTable(header, rowData);
  $("#outputDiv").html(table);
  $('#outputDiv').fadeIn('2000');
}

function getCookie(name) {
  match = document.cookie.match(new RegExp(name + '=([^;]+)'));
  if (match) return match[1];
}

function flatten(data) {
  var result = {};
  function recurse (cur, prop) {
    if (Object(cur) !== cur) {
      result[prop] = cur;
    } else if (Array.isArray(cur)) {
      for(var i=0, l=cur.length; i<l; i++)
        recurse(cur[i], prop + "[" + i + "]");
      if (l == 0)
        result[prop] = [];
    } else {
      var isEmpty = true;
      for (var p in cur) {
        isEmpty = false;
        recurse(cur[p], prop ? prop+"."+p : p);
      }
      if (isEmpty && prop)
        result[prop] = {};
    }
  }
  recurse(data, "");
  return result;
}

function removeDuplicates(array) {
  return array.reduce(function(a,b){
    if (a.indexOf(b) < 0 ) a.push(b);
    return a;
  },[]);
}

function getRowValue(row, elementId) {
  var element = row.find('#' + elementId);
  var value = null;
  if (element.length > 0) {
    value = element.val().trim()
    if (!value)
      value = null
  }
  return value;
}

function createTable(columnNames, rows, nonStriped) {
  var clazz = nonStriped ? "" : "table-striped"
  var table = $("<table class='table " + clazz + "'>")

  // head
  if (columnNames) {
    var thead = $("<thead>")
    var theadTr = $("<tr>")

    $.each(columnNames, function (index, columnName) {
      var th = "<th class='col header'>" + columnName + "</th>"
      theadTr.append(th)
    })
    thead.append(theadTr)
    table.append(thead)
  }

  // body
  var tbody = $("<tbody>")

  $.each(rows, function(index, row) {
    var tr = $("<tr>")
    $.each(row, function(index, item) {
      var td = "<td>" + item + "</td>"
      tr.append(td)
    })
    tbody.append(tr)
  })
  table.append(tbody)

  return table
}

function initJsTree(treeElementId, data, typesSetting) {
  $('#' + treeElementId).jstree({
    "core" : {
      "animation" : 0,
      "check_callback" : true,
      "themes" : {
        'responsive' : false,
        'variant' : 'large',
        "stripes" : true
      },
      'data' : data
    },
    "types" : typesSetting,
    "search": {
      "case_insensitive": true,
      "show_only_matches" : true,
      "search_leaves_only": true
    },

    "plugins" : [
      "search", "sort", "state", "types", "wholerow" // "contextmenu", "dnd",
    ]
  });

  $('#' + treeElementId).jstree("deselect_all");
}

function moveModalRight(modalId) {
  $('#' + modalId).one('hidden.bs.modal', function () {
//            $(this).data('bs.modal', null);
    var modalDialog = $('#' + modalId + ' .modal-dialog:first')
    var isRight = modalDialog.hasClass("modal-right")
    var isLeft = modalDialog.hasClass("modal-left")

    if (isRight) {
      modalDialog.removeClass("modal-right")
      modalDialog.addClass("modal-left")
    } else if (isLeft) {
      modalDialog.removeClass("modal-left")
    } else {
      modalDialog.addClass("modal-right")
    }

    $('#' + modalId).modal('show');
  });
}

function addSpinner(element, style) {
  if (style)
    element.append("<div class='spinner' style='margin: auto; " + style + "'></div>")
  else
    element.append("<div class='spinner' style='margin: auto;'></div>")
}

function updateFilterValueElement(filterElement, data) {
  var fieldType = (data.isArray) ? data.fieldType + " Array" : data.fieldType
  filterElement.find("#fieldInfo").html("Field type: " + fieldType)
  var conditionTypeElement = filterElement.find("#conditionType")

  var conditionType = conditionTypeElement.val();
  var isInNinType = (conditionType == "in" || conditionType == "nin") ? "multiple" : ""

  var newValueElement = null;
  if (data.allowedValues.length > 0) {
    conditionTypeElement.change(function() {
      var valueElement = $(this).parent().parent().find("#value")

      if (this.value == "in" || this.value == "nin") {
        valueElement.prop('multiple', 'multiple');
        var emptyOption = valueElement.find('option[value=""]');
        emptyOption.remove()
      } else {
        valueElement.removeProp('multiple');
        if (valueElement.find('option[value=""]').length == 0) {
          var firstOption = valueElement.find('option').first();
          firstOption.before('<option value="">[undefined]</option>');
        }
      }

      valueElement.selectpicker('destroy');
      valueElement.selectpicker();
    })

    var multiple = (isInNinType) ? "multiple" : ""
    newValueElement = $("<select id='value' " + multiple + " class='selectpicker float-left show-menu-arrow form-control conditionValue'>")
    if (!isInNinType)
      newValueElement.append("<option value=''>[undefined]</option>")
    $.each(data.allowedValues, function (index, keyValue) {
      newValueElement.append("<option value='" + keyValue[0] + "'>" + keyValue[1] + "</option>")
    });
  } else {
    newValueElement = $("<input id='value' class='float-left conditionValue' placeholder='Condition'/>")
  }
  var oldValueElement = filterElement.find("#addEditConditionModal .conditionValue")
  var oldValue = oldValueElement.val()
  oldValueElement.selectpicker('destroy');
  oldValueElement.replaceWith(newValueElement);

  if (data.allowedValues.length > 0) {
    if (isInNinType) {
      oldValue = oldValue.split(",");
    }
    newValueElement.val(oldValue);
    newValueElement.selectpicker();
  } else {
    newValueElement.val(oldValue);
  }
}

function updatePlusMinusIcon(element) {
  var iconPlus = element.find("span.glyphicon-plus:first");
  var iconMinus = element.find("span.glyphicon-minus:first");
  if (iconPlus.length) {
    iconPlus.removeClass("glyphicon-plus");
    iconPlus.addClass("glyphicon-minus");
  } else {
    iconMinus.removeClass("glyphicon-minus");
    iconMinus.addClass("glyphicon-plus");
  }
}

function scrollToAnchor(id, offset){
  var tag = $("#" + id)
  if (!offset)
    offset = 0
  $('html,body').animate({scrollTop: tag.offset().top + offset},'slow');
}

function createIndependenceTestTable(results, withTestType) {
  var header = ["Field", "p-Value", "Degree of Freedom", "Stats/F-Value"]
  if (withTestType)
    header.push("Test Type")

  var rowData = results.map(function(item) {
    var fieldLabel = item[0]
    var result = item[1]

    var color =
      (result.pValue >= 0.05) ? "black":
        (result.pValue >= 0.01) ? "forestGreen":
          (result.pValue >= 0.001) ? "green" : "darkGreen"

    var significantStyleDivStart = (result.pValue < 0.05) ? ("<div style='color: " + color + "; font-weight: bold'>") : "<div>"
    var isAnovaTest = (result.FValue != null)
    var rowStart = [fieldLabel,  significantStyleDivStart + result.pValue.toExponential(3).replace("e", " E") + "</div>"]

    var rowEnd = (isAnovaTest) ?
      [result.dfbg, result.FValue.toFixed(2)]
      :
      [result.degreeOfFreedom, result.statistics.toFixed(2)]

    var testTypeText = (isAnovaTest) ? "ANOVA" : "Chi-Square"

    if (withTestType)
      rowEnd.push(testTypeText)

    return rowStart.concat(rowEnd)
  });

  return createTable(header, rowData);
}

function msOrDateToStandardDateString(ms) {
  const date = new Date(ms)
  return dateToStandardString(date);
}

function dateToStandardString(date) {
  return date.getFullYear() + '-' +('0' + (date.getMonth()+1)).slice(-2)+ '-' + date.getDate() + ' ' + date.getHours() + ':'+('0' + (date.getMinutes())).slice(-2)+ ':' + date.getSeconds();
}

function asTypedStringValue(value, isDouble, isDate, ceiling) {
  function intValue() { return (ceiling) ? Math.ceil(value) : Math.floor(value) }

  return (isDate) ? msOrDateToStandardDateString(value) :
      (isDouble) ? value.toString() :
          intValue().toString()
}

function addFilterModelBeforeModalSubmit(modalId, filterElement, filterParamName) {
  $('#' + modalId + ' form').submit(function(event) {
    event.preventDefault();

    // remove the old filter
    $(this).find("input[name='" + filterParamName + "']").remove();

    // add a new one
    var filterModel = $(filterElement).multiFilter("getModel")

    var params = {}
    params[filterParamName] = JSON.stringify(filterModel)
    addParams($(this), params)

    // submit
    this.submit();
  });
}

function submitModalOnEnter(event, element) {
  if (event.which == 13) {
    var modalFooter = $(element).closest(".modal-body").parent().find(".modal-footer")
    modalFooter.find("#submitButton").trigger("click");
    return false;
  }
}

function getSelectedRowIds(tableElement) {
  var ids = []

  $(tableElement).find('tbody').find('tr').each(function() {
    var checked = $(this).find("td input.table-selection[type=checkbox]").is(':checked');
    var id = $(this).find("#_id").val()

    if (checked) {
      ids.push(id);
    }
  });

  return ids;
}

function activateTableAllSelection() {
  $(".table-selection-all").change(function() {
    var rows = $(this).closest("table").find(".table-selection")
    var checked = $(this).is(':checked')
    $.each(rows, function(i, row) {
      $(row).prop("checked", checked)
    })
  });
}

function enableFieldDragover(fieldNameElement, fieldTypeaheadElement, execFun, acceptedTypes) {
  fieldTypeaheadElement.on('dragover', false).on('drop', function (ev) {
    $(this).removeClass("dragged-over")
    ev.preventDefault();
    var transfer = ev.originalEvent.dataTransfer;
    var id = transfer.getData("id");
    var text = transfer.getData("text");
    var type = transfer.getData("type");

    if (id && (!acceptedTypes || acceptedTypes.includes(type))) {
      $(fieldNameElement).val(id)
      $(fieldTypeaheadElement).val(text)
      execFun();
    }
  }).on("dragover", function (ev) {
    var transfer = ev.originalEvent.dataTransfer;
    var type = transfer.getData("type");

    if (type.startsWith("field")) {
      $(this).addClass("dragged-over")
    }
  }).on("dragleave", function () {
    $(this).removeClass("dragged-over")
  })
}

function enableFieldTableDragover(fieldTableElement, execFun, acceptedTypes) {
  fieldTableElement.on('dragover', false).on('drop', function (ev) {
    $(this).removeClass("dragged-over")
    ev.preventDefault();
    var transfer = ev.originalEvent.dataTransfer;
    var id = transfer.getData("id");
    var text = transfer.getData("text");
    var type = transfer.getData("type");

    if (id && (!acceptedTypes || acceptedTypes.includes(type))) {
      var values = {};
      values["fieldName"] = id;
      values["fieldTypeahead"] = text ? text : id;

      fieldTableElement.dynamicTable('addTableRow', values)

      if (execFun) execFun();
    }
  }).on("dragover", function (ev) {
    var transfer = ev.originalEvent.dataTransfer;
    var type = transfer.getData("type");

    if (type.startsWith("field")) {
      $(this).addClass("dragged-over")
    }
  }).on("dragleave", function () {
    $(this).removeClass("dragged-over")
  })
}

function shorten(string, length) {
  var initLength = length || 25
  return (string.length > initLength) ? string.substring(0, initLength) + ".." : string
}

function svgToDataUrl(svg) {
  try {
    return URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml;charset=UTF-8,' }));
  } catch (e) {
    return 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(svg);
  }
}

function getSVGWidth(svgres) {
  return +svgres.match(/^<svg[^>]*width\s*=\s*\"?(\d+)\"?[^>]*>/)[1]
}

function getSVGHeight(svgres) {
  return +svgres.match(/^<svg[^>]*height\s*=\s*\"?(\d+)\"?[^>]*>/)[1]
}

function downloadFile(dataURL, filename) {
  const a = document.createElement('a');
  a.href = dataURL;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}