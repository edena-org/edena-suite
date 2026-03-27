function _callMultiFilter(filterElement, method, ...args) {
    $(filterElement).multiFilter(method, ...args);
}

class WidgetEngine {

    // Main function of this class
    plot(widget, filterElement) {
        const widgetId = this._elementId(widget)
        this.plotForElement(widgetId, widget, filterElement)
    }

    plotForElement(widgetElementId, widget, filterElement) {
        if (widget.displayOptions.isTextualForm)
            switch (widget.concreteClass) {
                case "org.edena.ada.web.models.CategoricalCountWidget":
                    this._categoricalTableWidget(widgetElementId, widget);
                    break;
                case "org.edena.ada.web.models.NumericalCountWidget":
                    this._numericalTableWidget(widgetElementId, widget);
                    break;
                case "org.edena.ada.web.models.BasicStatsWidget":
                    this._basicStatsWidget(widgetElementId, widget);
                    break;
                default:
                    console.log(widget.concreteClass + " does not have a textual representation.")
            }
        else
            switch (widget.concreteClass) {
                case "org.edena.ada.web.models.CategoricalCountWidget":
                    this._categoricalCountWidget(widgetElementId, widget, filterElement);
                    break;
                case "org.edena.ada.web.models.NumericalCountWidget":
                    this._numericalCountWidget(widgetElementId, widget, filterElement);
                    break;
                case "org.edena.ada.web.models.CategoricalCheckboxCountWidget":
                    this._categoricalCheckboxCountWidget(widgetElementId, widget, filterElement);
                    break;
                case "org.edena.ada.web.models.BoxWidget":
                    this._boxWidget(widgetElementId, widget);
                    break;
                case "org.edena.ada.web.models.ScatterWidget":
                    this._scatterWidget(widgetElementId, widget, filterElement);
                    break;
                case "org.edena.ada.web.models.ValueScatterWidget":
                    this._valueScatterWidget(widgetElementId, widget, filterElement);
                    break;
                case "org.edena.ada.web.models.HeatmapWidget":
                    this._heatmapWidget(widgetElementId, widget, filterElement);
                    break;
                case "org.edena.ada.web.models.HtmlWidget":
                    this._htmlWidget(widgetElementId, widget);
                    break;
                case 'org.edena.ada.web.models.LineWidget':
                    this._lineWidget(widgetElementId, widget, filterElement);
                    break;
                case "org.edena.ada.web.models.BasicStatsWidget":
                    this._basicStatsWidget(widgetElementId, widget);
                    break;
                case "org.edena.ada.web.models.IndependenceTestWidget":
                    this._independenceTestWidget(widgetElementId, widget);
                    break;
                default:
                    console.log("Widget type " + widget.concreteClass + " unrecognized.")
            }
    }

    _elementId(widget) {
        return widget._id.$oid + "Widget"
    }

    // creates a div for a widget
    widgetDiv(widget, defaultGridWidth, enforceWidth) {
        var elementIdVal = this._elementId(widget)

        if (enforceWidth)
            return this._widgetDivAux(elementIdVal, defaultGridWidth);
        else {
            var gridWidth = widget.displayOptions.gridWidth || defaultGridWidth;
            var gridOffset = widget.displayOptions.gridOffset;

            return this._widgetDivAux(elementIdVal, gridWidth, gridOffset);
        }
    }

    _widgetDivAux(elementIdVal, gridWidth, gridOffset) {
        var gridWidthElement = "col-md-" + gridWidth
        var gridOffsetElement = gridOffset ? "col-md-offset-" + gridOffset : ""

        var outerDiv = document.createElement('div')
        outerDiv.className = gridWidthElement + " " + gridOffsetElement

        var innerDiv = document.createElement('div')
        innerDiv.id = elementIdVal
        innerDiv.className = 'chart-holder'

        outerDiv.appendChild(innerDiv)
        return outerDiv
    }

    ////////////////////////////////////////////////////
    // Impls of "textual" widgets, mostly table based //
    ////////////////////////////////////////////////////

    _categoricalTableWidget(elementId, widget) {
        var allCategories = widget.data.map(function (series) {
            return series[1].map(function (count) {
                return count.value
            })
        });
        var categories = removeDuplicates([].concat.apply([], allCategories))

        var groups = widget.data.map(function (series) {
            return shorten(series[0], 15)
        });
        var fieldLabel = shorten(widget.fieldLabel, 15)

        var dataMap = widget.data.map(function (series) {
            var map = {}
            series[1].forEach(function (count) {
                map[count.value] = count.count
            })
            return map
        });

        var rowData = categories.map(function (categoryName) {
            var sum = 0;
            var data = dataMap.map(function (map) {
                var count = map[categoryName] || 0
                sum += count
                return count
            })
            var result = [categoryName].concat(data)
            if (groups.length > 1) {
                result.push(sum)
            }
            return result
        })

        if (categories.length > 1) {
            var counts = widget.data.map(function (series) {
                var sum = 0
                series[1].forEach(function (count) {
                    sum += count.count
                })
                return sum
            });

            var totalCount = counts.reduce(function (a, b) {
                return a + b
            })

            var countRow = ["<b>Total</b>"].concat(counts)
            if (groups.length > 1) {
                countRow.push(totalCount)
            }
            rowData = rowData.concat([countRow])
        }

        var columnNames = [fieldLabel].concat(groups)
        if (groups.length > 1) {
            columnNames.push("Total")
        }

        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = document.createElement('div')
        div.style.cssText = 'position: relative; overflow: auto; height:' + height + 'px; text-align: left; line-height: normal; z-index: 0;'

        var table = createTable(columnNames, rowData)

        div.insertAdjacentHTML('beforeend', caption)
        div.appendChild(table[0])

        var el = document.getElementById(elementId)
        el.innerHTML = ''
        el.appendChild(div)
    }

    _numericalTableWidget(elementId, widget) {
        var isDate = widget.fieldType == "Date"

        var groups = widget.data.map(function (series) {
            return shorten(series[0], 15)
        });
        var fieldLabel = shorten(widget.fieldLabel, 15)
        var valueLength = widget.data[0][1].length

        var rowData = Array.from(Array(valueLength).keys()).map(function (index) {
            var row = widget.data.map(function (series) {
                var item = series[1]
                var value = item[index].value
                if (isDate) {
                    value = new Date(value).toISOString()
                }
                return [value, item[index].count]
            })
            return [].concat.apply([], row)
        })

        var columnNames = groups.map(function (group) {
            return [fieldLabel, group]
        })
        var columnNames = [].concat.apply([], columnNames)

        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = document.createElement('div')
        div.style.cssText = 'position: relative; overflow: auto; height:' + height + 'px; text-align: left; line-height: normal; z-index: 0;'

        var table = createTable(columnNames, rowData)

        div.insertAdjacentHTML('beforeend', caption)

        var centerWrapper = document.createElement('table')
        centerWrapper.setAttribute('align', 'center')
        var tr = document.createElement('tr')
        tr.className = 'vertical-divider'
        tr.setAttribute('valign', 'top')
        var td = document.createElement('td')

        td.appendChild(table[0])
        tr.appendChild(td)
        centerWrapper.appendChild(tr)
        div.appendChild(centerWrapper)

        var el = document.getElementById(elementId)
        el.innerHTML = ''
        el.appendChild(div)
    }

    _categoricalCheckboxCountWidget(elementId, widget, filterElement) {
        var widgetElement = document.getElementById(elementId)

        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = document.createElement('div')
        div.style.cssText = 'position: relative; overflow: auto; height:' + height + 'px; text-align: left; line-height: normal; z-index: 0;'

        var jumbotron = document.createElement('div')
        jumbotron.className = 'alert alert-very-light'
        jumbotron.setAttribute('role', 'alert')

        var rowData = widget.data.map(function (checkCount) {
            var checked = checkCount[0]
            var count = checkCount[1]

            var checkedAttr = checked ? " checked" : ""

            var key = count.key

            var checkbox = '<input type="checkbox" data-key="' + key + '"' + checkedAttr + '/>';

            if (!key) {
                checkbox = ""
            }

            var value = count.value;
            if (checked) {
                value = '<b>' + value + '</b>';
            }
            var count = (checked || !key) ? '(' + count.count + ')' : '---'
            return [checkbox, value, count]
        })

        var checkboxTable = createTable(null, rowData, true)

        jumbotron.appendChild(checkboxTable[0])

        div.insertAdjacentHTML('beforeend', caption)
        div.appendChild(jumbotron)

        widgetElement.innerHTML = ''
        widgetElement.appendChild(div)

        // add a filter support

        function findCheckedKeys() {
            var keys = []
            widgetElement.querySelectorAll('input[type="checkbox"]').forEach(function (cb) {
                if (cb.checked) {
                    keys.push(cb.dataset.key)
                }
            });
            return keys;
        }

        widgetElement.querySelectorAll('input[type="checkbox"]').forEach(function (cb) {
            cb.addEventListener('change', function () {
                var selectedKeys = findCheckedKeys();

                if (selectedKeys.length > 0) {
                    var condition = {fieldName: widget.fieldName, conditionType: "in", value: selectedKeys}

                    _callMultiFilter(filterElement, 'replaceWithConditionAndSubmit', condition);
                } else
                    showError("At least one checkbox must be selected in the widget '" + widget.title + "'.")
            });
        });
    }

    _basicStatsWidget(elementId, widget) {
        var caption = "<h4 align='center'>" + widget.title + "</h4>"
        var columnNames = ["Stats", "Value"]

        function roundOrInt(value) {
            return Number.isInteger(value) ? value : value.toFixed(3)
        }

        var data = [
            ["Min", roundOrInt(widget.data.min)],
            ["Max", roundOrInt(widget.data.max)],
            ["Sum", roundOrInt(widget.data.sum)],
            ["Mean", roundOrInt(widget.data.mean)],
            ["Variance", roundOrInt(widget.data.variance)],
            ["STD", roundOrInt(widget.data.standardDeviation)],
            ["# Defined", widget.data.definedCount],
            ["# Undefined", widget.data.undefinedCount]
        ]

        var height = widget.displayOptions.height || 400
        var div = document.createElement('div')
        div.style.cssText = 'position: relative; overflow: hidden; height:' + height + 'px; text-align: left; line-height: normal; z-index: 0;'

        var table = createTable(columnNames, data)

        div.insertAdjacentHTML('beforeend', caption)

        var centerWrapper = document.createElement('table')
        centerWrapper.setAttribute('align', 'center')
        var tr = document.createElement('tr')
        tr.className = 'vertical-divider'
        tr.setAttribute('valign', 'top')
        var td = document.createElement('td')

        td.appendChild(table[0])
        tr.appendChild(td)
        centerWrapper.appendChild(tr)
        div.appendChild(centerWrapper)

        var el = document.getElementById(elementId)
        el.innerHTML = ''
        el.appendChild(div)
    }

    _independenceTestWidget(elementId, widget) {
        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = document.createElement('div')
        div.style.cssText = 'position: relative; overflow: hidden; height:' + height + 'px; text-align: left; line-height: normal; z-index: 0;'

        var table = createIndependenceTestTable(widget.data)

        div.insertAdjacentHTML('beforeend', caption)

        var centerWrapper = document.createElement('table')
        centerWrapper.setAttribute('align', 'center')
        var tr = document.createElement('tr')
        tr.className = 'vertical-divider'
        tr.setAttribute('valign', 'top')
        var td = document.createElement('td')

        td.appendChild(table[0])
        tr.appendChild(td)
        centerWrapper.appendChild(tr)
        div.appendChild(centerWrapper)

        var el = document.getElementById(elementId)
        el.innerHTML = ''
        el.appendChild(div)
    }

    _htmlWidget(elementId, widget) {
        document.getElementById(elementId).innerHTML = widget.content
    }

    _agg(series, widget) {
        var counts = series.map(function (item) {
            return item.count;
        });

        if (widget.isCumulative) {
            var max = counts.reduce(function (a, b) {
                return Math.max(a, b);
            });

            return max
        } else {
            var sum = counts.reduce(function (a, b) {
                return a + b;
            });

            return sum
        }
    }

    _combineSVGs(charts, exportFun, callback) {
        const padding = 50;

        var top = padding;
        var width = 0;
        var svgArr = [];

        function adjustSVG(svgres) {
            // Grab width/height from exported chart
            const svgWidth = getSVGWidth(svgres), svgHeight = getSVGHeight(svgres)

            // Offset the position of this chart in the final SVG
            var svg = svgres.replace('<svg', '<g transform="translate(' + padding + ',' + top + ')" ');
            svg = svg.replace('</svg>', '</g>');

            top += svgHeight;
            width = Math.max(width, svgWidth);
            return svg;
        }

        function exportChart(i) {
            if (i == charts.length) {
                const finalResult = '<svg height="' + (top + padding) + '" width="' + (width + 2 * padding) + '" version="1.1" xmlns="http://www.w3.org/2000/svg">' + svgArr.join('') + '</svg>'
                return callback(finalResult);
            }

            exportFun(charts[i], function (svg) {
                if (svg) svgArr.push(adjustSVG(svg));

                return exportChart(i + 1); // continue
            })
        }

        exportChart(0);
    }

    ///////////////////////////////////////////////////////////////////
    // Impl hooks of visual widgets (to be overridden in subclasses) //
    ///////////////////////////////////////////////////////////////////

    _categoricalCountWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _numericalCountWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _boxWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }

    _scatterWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _valueScatterWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _heatmapWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _lineWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    refresh() {
        throw "no fun impl. provided"
    }

    export(chartIds, format, filename) {
        throw "no fun impl. provided"
    }
}
