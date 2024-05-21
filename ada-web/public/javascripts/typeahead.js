
function stringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter, string) {
    var string2 = string;
    if (nonWhitespaceDelimiter) {
        string2 = string.split(nonWhitespaceDelimiter).join(" ")
    }
    var strings = Bloodhound.tokenizers.whitespace(string2);
    if (searchAsContainsFlag) {
        $.each(strings, function (k, v) {
            var i = 1;
            while ((i + 1) < v.length) {
                strings.push(v.substr(i, v.length));
                i++;
            }
        })
    }
    return strings;
}

function createStringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter) {
    return stringDatumTokenizer.bind(null, searchAsContainsFlag).bind(null, nonWhitespaceDelimiter)
}

function populateStringTypeahead({element, data, searchAsContainsFlag, nonWhitespaceDelimiter, updateValueElement}) {
    var datumTokenizer = createStringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter)
    var source = createBloodhoundSource(data, datumTokenizer)
    populateTypeahead({element, source, updateValueElement})
}

function createBloodhoundSource(data, datumTokenizer) {
    var dataSource = new Bloodhound({
        datumTokenizer: datumTokenizer,
        queryTokenizer: Bloodhound.tokenizers.whitespace,
        local: data
    });

    dataSource.initialize();

    // Setting of minlength to 0 does not work. To show ALL items if nothing entered this function needs to be introduced
    function listSearchWithAll(q, sync) {
        if (q == '')
            sync(dataSource.all());
        else
            dataSource.search(q, sync);
    }

    return listSearchWithAll;
}

function populateTypeahead({element, source, displayFun, suggestionFun, updateValueElement, minLength}) {
    element.typeahead({
        hint: true,
        highlight: true,
        minLength: typeof minLength === 'undefined' ? 0 : minLength
    }, {
        source: source,
        display: displayFun,
        templates: {
            suggestion: suggestionFun
        },
        limit: 1000
    });

    element.on("focus", function () {
        var value = element.val();
        element.typeahead('val', '_');
        element.typeahead('val', value);
        return true
    });

    element.on('typeahead:select', function (e, datum) {
        if (updateValueElement)
            updateValueElement(datum);
    });

    element.on('typeahead:cursorchanged', function (e, datum) {
        if (updateValueElement)
            updateValueElement(datum);
    });

    element.on('keyup', this, function(e) {
        if(e.keyCode != 8 && e.keyCode != 46) {
            selectFirstSuggestion(element)
        }
    })
}

function selectFirstSuggestion(element) {
    var suggestions = element.parent().find('.tt-suggestion')
    if (suggestions.length == 1) {
        element.typeahead('select', suggestions.first())
    }
}

function selectShortestSuggestion(element) {
    var suggestions = element.parent().find('.tt-suggestion')
    if (suggestions.length > 0) {
        var min = Number.MAX_SAFE_INTEGER
        var minSuggestion = null
        $.each(suggestions, function (index, suggestion) {
            if (suggestion.innerText.length < min) {
                minSuggestion = suggestion;
                min = suggestion.innerText.length
            }
        });
        element.typeahead('select', minSuggestion)
    }
}

function createFieldBloodhoundSource(fieldNameAndLabels, showOption) {
    var fullFieldNameAndLabels = fieldNameAndLabels.map( function(field, index) {

        var nameItem = (Array.isArray(field)) ?
            {key: field[0], value: field[0]} :
            {key: field.name, value: field.name};

        var labelItem = (Array.isArray(field)) ?
            {key: field[0], value: field[1], isLabel: true} :
            {key: field.name, value: field.label, isLabel: true};

        switch (showOption) {
            case 0: return [nameItem];
            case 1: return (labelItem.value != null) ? [labelItem] : [];
            case 2: return (labelItem.value != null) ? [labelItem] : [nameItem];
            case 3: return (labelItem.value != null) ? [nameItem, labelItem] : [nameItem];
        }
    });

    var stringDatumTokenizer = createStringDatumTokenizer(true, false);

    var compareValues = function (a, b) {
        if (a.value < b.value)
            return -1;
        else if (a.value == b.value)
            return 0;
        else
            return 1;
    };

    return createBloodhoundSource(
        [].concat.apply([], fullFieldNameAndLabels).sort(compareValues),
        function (item) {
            return stringDatumTokenizer(item.value);
        }
    )
}

function populateFieldTypeaheads({typeaheadElements,
                                     fieldNameElements,
                                     fieldNameAndLabels,
                                     showOption,
                                     initSelectByNameElement,
                                     minLength}) {
    var source = createFieldBloodhoundSource(fieldNameAndLabels, showOption)

    for(var i = 0; i < typeaheadElements.length; i++){
        var typeaheadElement = typeaheadElements[i]
        var fieldNameElement = fieldNameElements[i]

        populateFieldTypeaheadAux({typeaheadElement, fieldNameElement, source, showOption, minLength})

        if (initSelectByNameElement) {
            selectByNameElement(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption)
        }
    }
}

/**
 *
 * @param typeaheadElement
 * @param fieldNameElement
 * @param fieldNameAndLabels
 * @param showOption 0 - show field names only, 1 - show field labels only,
 *                   2 - show field labels, and field names if no label defined, 3 - show both, field names and labels
 */
function populateFieldTypeahead({typeaheadElement,
                                    fieldNameElement,
                                    fieldNameAndLabels,
                                    showOption,
                                    initSelectByNameElement,
                                    minLength}) {
    var source = createFieldBloodhoundSource(fieldNameAndLabels, showOption)

    populateFieldTypeaheadAux({typeaheadElement, fieldNameElement, source, showOption, minLength})

    if (initSelectByNameElement) {
        selectByNameElement(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption)
    }
}

function selectByNameElement(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption) {
    var fieldName = fieldNameElement.val();

    var matchedField = $.grep(fieldNameAndLabels, function (field) {
        var name = (Array.isArray(field)) ? field[0] : field.name;
        return name == fieldName
    });

    if (matchedField.length > 0) {
        var name = (Array.isArray(matchedField[0])) ? matchedField[0][0] : matchedField[0].name;
        var label = (Array.isArray(matchedField[0])) ? matchedField[0][1] : matchedField[0].label;

        var selectElement = null;

        switch (showOption) {
            case 0: selectElement = name; break;
            case 1: selectElement = label; break;
            case 2: selectElement = (label != null) ? label : name; break;
            case 3: selectElement = (label != null) ? label : name; break;
        }

        if (selectElement) {
            typeaheadElement.typeahead('val', selectElement);
            selectShortestSuggestion(typeaheadElement)
        }
    }
}

function populateFieldTypeaheadAux({typeaheadElement, fieldNameElement, source, showOption, minLength}) {
    populateTypeahead({
        element: typeaheadElement,
        source,
        displayFun: function(item) {
            return item.value;
        },
        suggestionFun: function(item) {
            var nameBadge = '';
            var labelBadge = '';
            switch (showOption) {
                case 0:
                    nameBadge = '';
                    break;
                case 1:
                    nameBadge = '';
                    break;
                case 2:
                    nameBadge = '<span class="label label-info label-filter">name</span>';
                    break;
                case 3:
                    nameBadge = '<span class="label label-info label-filter">name</span>';
                    break;
            }
            switch (showOption) {
                case 0:
                    labelBadge = '';
                    break;
                case 1:
                    labelBadge = '';
                    break;
                case 2:
                    labelBadge = '<span class="label label-success label-filter">label</span>';
                    break;
                case 3:
                    labelBadge = '<span class="label label-success label-filter">label</span>';
                    break;
            }
            if (item.isLabel)
                return '<div><span>' + item.value + '</span>' + labelBadge + '</div>';
            else
                return '<div><span>' + item.value + '</span>' + nameBadge + '</div>';
        },
        updateValueElement: function(item) {
            if (item) {
                fieldNameElement.val(item.key);
            }
        },
        minLength
    });
}

/**
 *
 * @param typeaheadElement
 * @param fieldNameElement
 * @param url
 * @param showOption 0 - show field names only, 1 - show field labels only,
 *                   2 - show field labels, and field names if no label defined, 3 - show both, field names and labels
 */
function populateFieldTypeaheadFromUrl({typeaheadElement, fieldNameElement, url, showOption, postFunction, initSelectByNameElement, minLength}) {
    $.ajax({
        url: url,
        success: function (fieldNameAndLabels) {
            populateFieldTypeahead({
                typeaheadElement,
                fieldNameElement,
                fieldNameAndLabels,
                showOption,
                initSelectByNameElement,
                minLength
            });
            if (postFunction) {
                postFunction()
            }
        },
        error: showErrorResponse
    });
}

function populateFieldTypeaheadsFromUrl({typeaheadElements, fieldNameElements, url, showOption, initSelectByNameElement, postFunction}) {
    $.ajax({
        url: url,
        success: function (fieldNameAndLabels) {
            populateFieldTypeaheads({
                typeaheadElements,
                fieldNameElements,
                fieldNameAndLabels,
                showOption,
                initSelectByNameElement
            });
            if (postFunction) {
                postFunction()
            }
        },
        error: showErrorResponse
    });
}

function populateIdNameTypeaheadFromUrl({typeaheadElement, idElement, url, initSelectByNameElement}) {
    $.ajax({
        url: url,
        success: function (data) {
            populateIdNameTypeahead({
                typeaheadElement,
                idElement,
                idNames: data,
                initSelectByNameElement
            });
        },
        error: function(data){
            showErrorResponse(data)
        }
    });
}

function populateIdNameTypeahead({typeaheadElement, idElement, idNames, initSelectByNameElement, minLength}) {
    var typeaheadData = idNames.map(function (item, index) {
        return {name: item._id.$oid, label: item.name};
    });
    populateFieldTypeahead({
        typeaheadElement,
        fieldNameElement: idElement,
        fieldNameAndLabels: typeaheadData,
        showOption: 1,
        initSelectByNameElement,
        minLength
    });
}

function populateIdNameTypeaheadsFromUrl({typeaheadElements, idElements, url, initSelectByNameElement}) {
    $.ajax({
        url: url,
        success: function (data) {
            var fieldNameAndLabels = data.map(function (item, index) {
                return {name: item._id.$oid, label: item.name};
            });
            populateFieldTypeaheads({
                typeaheadElements,
                fieldNameElements: idElements,
                fieldNameAndLabels,
                showOption: 1,
                initSelectByNameElement
            });
        },
        error: showErrorResponse
    });
}