class ApexChartsWidgetEngine extends WidgetEngine {

    _charts = {}

    _catPalette = [
        "rgb(124,181,236)",
        "rgb(67,67,72)",
        "rgb(144,237,125)",
        "rgb(247,163,92)",
        "rgb(128,133,233)",
        "rgb(241,92,128)",
        "rgb(228,211,84)",
        "rgb(43,144,143)",
        "#f45b5b",
        "rgb(145,232,225)"
    ]

    _catPaletteSize = this._catPalette.length

    // Helper: destroy old chart, create new one, track it
    _renderChart(elementId, options) {
        if (this._charts[elementId]) {
            this._charts[elementId].destroy()
            delete this._charts[elementId]
        }
        var el = document.getElementById(elementId)
        el.innerHTML = ''
        var chart = new ApexCharts(el, options)
        chart.render()
        this._charts[elementId] = chart
        return chart
    }

    _addChartTypeMenu(elementId) {
        var el = document.getElementById(elementId)

        // Create a wrapper around the chart element so the menu lives outside ApexCharts' DOM
        var wrapper = el.parentNode
        if (!wrapper || !wrapper.classList.contains('chart-type-wrapper')) {
            wrapper = document.createElement('div')
            wrapper.classList.add('chart-type-wrapper')
            wrapper.style.position = 'relative'
            el.parentNode.insertBefore(wrapper, el)
            wrapper.appendChild(el)
        }

        // Only add menu once per wrapper
        if (wrapper.querySelector('.chart-type-menu')) return

        var menuHtml =
            '<div class="chart-type-menu dropdown" style="position: absolute; left: 15px; top: 0px; z-index: 10">' +
                '<button class="btn btn-sm dropdown-toggle" style="background-color:transparent" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">' +
                    '<span class="dot" aria-hidden="true"></span>' +
                '</button>' +
                '<ul class="dropdown-menu">' +
                    '<li><a class="chart-type-menu-item" data-chart-type="Pie" href="#">Pie</a></li>' +
                    '<li><a class="chart-type-menu-item" data-chart-type="Column" href="#">Column</a></li>' +
                    '<li><a class="chart-type-menu-item" data-chart-type="Bar" href="#">Bar</a></li>' +
                    '<li><a class="chart-type-menu-item" data-chart-type="Line" href="#">Line</a></li>' +
                    '<li><a class="chart-type-menu-item" data-chart-type="Spline" href="#">Spline</a></li>' +
                    '<li><a class="chart-type-menu-item" data-chart-type="Polar" href="#">Polar</a></li>' +
                '</ul>' +
            '</div>'

        wrapper.insertAdjacentHTML('afterbegin', menuHtml)
        wrapper.querySelectorAll('.chart-type-menu-item').forEach(function (item) {
            item.addEventListener('click', function (event) {
                var chartType = event.target.dataset.chartType
                document.getElementById(elementId).dispatchEvent(new CustomEvent("chartTypeChanged", {detail: chartType}))
            })
        })
    }

    _commonOptions() {
        return {
            chart: {
                animations: { enabled: true, speed: 400 },
                toolbar: { show: true, tools: { download: true, selection: true, zoom: true, zoomin: true, zoomout: true, reset: true } },
                zoom: { allowMouseWheelZoom: false },
                fontFamily: 'Helvetica, Arial, sans-serif'
            },
            colors: this._catPalette,
            noData: { text: 'No data available' }
        }
    }

    _formatValue(value, isDate, isDouble, floatingPoints) {
        if (isDate) return msOrDateToStandardDateString(value)
        if (isDouble) return (floatingPoints != null) ? Number(value).toFixed(floatingPoints) : value
        return value
    }

    // impl
    _categoricalCountWidget(elementId, widget, filterElement) {
        var that = this

        var datas = widget.data.map(function (nameSeries) {
            var name = nameSeries[0]
            var series = nameSeries[1]

            var sum = that._agg(series, widget)
            var data = series.map(function (item) {
                var label = shorten(item.value, 50)
                var count = item.count
                var key = item.key

                var percent = 100 * count / sum
                var value = (widget.useRelativeValues) ? percent : count
                var displayPercent = percent.toFixed(1) + "%"
                var text = (widget.useRelativeValues) ? displayPercent : count + " (" + displayPercent + ")"
                return {x: label, y: value, text: text, key: key}
            })

            return [name, data]
        })

        var seriesSize = datas.length
        var height = widget.displayOptions.height || 400
        var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            that._categoricalWidgetAux({
                chartType: chartType,
                datas: datas,
                seriesSize: seriesSize,
                title: widget.title,
                yAxisCaption: yAxisCaption,
                chartElementId: elementId,
                showLabels: widget.showLabels,
                showLegend: widget.showLegend,
                height: height,
                useRelativeValues: widget.useRelativeValues
            })

            if (filterElement) {
                that._addPointClicked(elementId, filterElement, widget.fieldName, datas)
            }
        }

        document.getElementById(elementId).addEventListener('chartTypeChanged', function (event) {
            plot(event.detail)
        })

        plot(widget.displayOptions.chartType)

        this._addChartTypeMenu(elementId)
    }

    _categoricalWidgetAux({
        chartType,
        datas,
        seriesSize,
        title,
        yAxisCaption,
        chartElementId,
        showLabels,
        showLegend,
        height,
        useRelativeValues
    }) {
        var that = this
        var common = this._commonOptions()
        var options

        switch (chartType) {
            case 'Pie':
                if (seriesSize === 1) {
                    var d = datas[0][1]
                    options = {
                        chart: Object.assign({}, common.chart, { type: 'pie', height: height }),
                        title: { text: title, align: 'center', style: { fontSize: '14px' } },
                        labels: d.map(function (item) { return item.x }),
                        series: d.map(function (item) { return item.y }),
                        colors: common.colors,
                        legend: { show: true, position: 'bottom' },
                        dataLabels: { enabled: showLabels !== false },
                        tooltip: {
                            y: {
                                formatter: function (val, opts) {
                                    var item = d[opts.seriesIndex]
                                    return item ? item.text : val
                                }
                            }
                        },
                        noData: common.noData
                    }
                } else {
                    // Multiple series: use donut for each
                    var allLabels = datas[0][1].map(function (item) { return item.x })
                    var allSeries = datas.map(function (nameSeries) {
                        return nameSeries[1].map(function (item) { return item.y })
                    })

                    // ApexCharts doesn't support multiple pie series natively,
                    // so fall back to donut with the first series
                    var d = datas[0][1]
                    options = {
                        chart: Object.assign({}, common.chart, { type: 'donut', height: height }),
                        title: { text: title, align: 'center', style: { fontSize: '14px' } },
                        labels: allLabels,
                        series: d.map(function (item) { return item.y }),
                        colors: common.colors,
                        legend: { show: true, position: 'bottom' },
                        dataLabels: { enabled: showLabels !== false },
                        plotOptions: { pie: { donut: { size: '40%' } } },
                        tooltip: {
                            y: {
                                formatter: function (val, opts) {
                                    var item = d[opts.seriesIndex]
                                    return item ? item.text : val
                                }
                            }
                        },
                        noData: common.noData
                    }
                }
                break

            case 'Column':
                options = this._barOptions({
                    datas: datas, seriesSize: seriesSize, title: title,
                    xAxisCaption: '', yAxisCaption: yAxisCaption,
                    height: height, horizontal: false, useRelativeValues: useRelativeValues,
                    colorByPoint: seriesSize === 1
                })
                break

            case 'Bar':
                options = this._barOptions({
                    datas: datas, seriesSize: seriesSize, title: title,
                    xAxisCaption: yAxisCaption, yAxisCaption: '',
                    height: height, horizontal: true, useRelativeValues: useRelativeValues,
                    colorByPoint: seriesSize === 1
                })
                break

            case 'Line':
                options = this._lineOptions({
                    datas: datas, title: title,
                    xAxisCaption: '', yAxisCaption: yAxisCaption,
                    height: height, smooth: false, seriesSize: seriesSize
                })
                break

            case 'Spline':
                options = this._lineOptions({
                    datas: datas, title: title,
                    xAxisCaption: '', yAxisCaption: yAxisCaption,
                    height: height, smooth: true, seriesSize: seriesSize
                })
                break

            case 'Polar':
                options = this._polarOptions({
                    datas: datas, title: title, height: height, seriesSize: seriesSize
                })
                break
        }

        if (options) {
            this._renderChart(chartElementId, options)
        }
    }

    // impl
    _numericalCountWidget(elementId, widget, filterElement) {
        var that = this

        var isDate = widget.fieldType == "Date"
        var isDouble = widget.fieldType == "Double"

        var datas = widget.data.map(function (nameSeries) {
            var name = nameSeries[0]
            var series = nameSeries[1]

            var sum = that._agg(series, widget)
            var data = series.map(function (item) {
                var count = item.count

                var percent = 100 * count / sum
                var value = (widget.useRelativeValues) ? percent : count
                var displayPercent = percent.toFixed(1) + "%"
                var text = (widget.useRelativeValues) ? displayPercent : count + " (" + displayPercent + ")"
                return {x: item.value, y: value, text: text}
            })

            return [name, data]
        })

        var seriesSize = datas.length
        var height = widget.displayOptions.height || 400

        var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            that._numericalWidgetAux({
                chartType: chartType,
                datas: datas,
                seriesSize: seriesSize,
                title: widget.title,
                xAxisCaption: widget.fieldLabel,
                yAxisCaption: yAxisCaption,
                chartElementId: elementId,
                height: height,
                useRelativeValues: widget.useRelativeValues,
                isDate: isDate,
                isDouble: isDouble
            })

            if (filterElement) {
                that._addXAxisZoomed(elementId, filterElement, widget.fieldName, isDouble, isDate)
            }
        }

        document.getElementById(elementId).addEventListener('chartTypeChanged', function (event) {
            plot(event.detail)
        })

        plot(widget.displayOptions.chartType)

        this._addChartTypeMenu(elementId)
    }

    _numericalWidgetAux({
        chartType,
        datas,
        seriesSize,
        title,
        xAxisCaption,
        yAxisCaption,
        chartElementId,
        height,
        useRelativeValues,
        isDate,
        isDouble
    }) {
        var that = this
        var options

        switch (chartType) {
            case 'Pie':
                // For pie, format x values as labels
                var d = datas[0][1]
                var labels = d.map(function (item) {
                    return that._formatValue(item.x, isDate, isDouble, 2)
                })

                options = {
                    chart: Object.assign({}, that._commonOptions().chart, { type: 'pie', height: height }),
                    title: { text: title, align: 'center', style: { fontSize: '14px' } },
                    labels: labels,
                    series: d.map(function (item) { return item.y }),
                    colors: that._catPalette,
                    legend: { show: false },
                    dataLabels: { enabled: false },
                    tooltip: {
                        y: {
                            formatter: function (val, opts) {
                                var item = d[opts.seriesIndex]
                                return item ? item.text : val
                            }
                        }
                    },
                    noData: { text: 'No data available' }
                }
                break

            case 'Column':
                options = this._barOptions({
                    datas: datas, seriesSize: seriesSize, title: title,
                    xAxisCaption: xAxisCaption, yAxisCaption: yAxisCaption,
                    height: height, horizontal: false, useRelativeValues: useRelativeValues,
                    colorByPoint: false, isDate: isDate, isDouble: isDouble, isNumerical: true
                })
                break

            case 'Bar':
                options = this._barOptions({
                    datas: datas, seriesSize: seriesSize, title: title,
                    xAxisCaption: yAxisCaption, yAxisCaption: xAxisCaption,
                    height: height, horizontal: true, useRelativeValues: useRelativeValues,
                    colorByPoint: false, isDate: isDate, isDouble: isDouble, isNumerical: true
                })
                break

            case 'Line':
                options = this._numericalLineOptions({
                    datas: datas, title: title,
                    xAxisCaption: xAxisCaption, yAxisCaption: yAxisCaption,
                    height: height, smooth: false, seriesSize: seriesSize,
                    isDate: isDate, isDouble: isDouble
                })
                break

            case 'Spline':
                options = this._numericalLineOptions({
                    datas: datas, title: title,
                    xAxisCaption: xAxisCaption, yAxisCaption: yAxisCaption,
                    height: height, smooth: true, seriesSize: seriesSize,
                    isDate: isDate, isDouble: isDouble
                })
                break

            case 'Polar':
                options = this._polarOptions({
                    datas: datas, title: title, height: height, seriesSize: seriesSize
                })
                break
        }

        if (options) {
            this._renderChart(chartElementId, options)
        }
    }

    // impl
    _boxWidget(elementId, widget) {
        var that = this
        var common = this._commonOptions()
        var height = widget.displayOptions.height || 400

        var seriesData = widget.data.map(function (namedQuartiles) {
            var name = namedQuartiles[0]
            var q = namedQuartiles[1]
            return { x: name, y: [q.lowerWhisker, q.lowerQuantile, q.median, q.upperQuantile, q.upperWhisker] }
        })

        // Compute y-axis range from whisker values with padding
        var yMin = widget.min
        var yMax = widget.max
        if (yMin == null || yMax == null) {
            var allLower = widget.data.map(function (nq) { return nq[1].lowerWhisker })
            var allUpper = widget.data.map(function (nq) { return nq[1].upperWhisker })
            var dataMin = Math.min.apply(null, allLower)
            var dataMax = Math.max.apply(null, allUpper)
            var range = dataMax - dataMin
            var padding = range * 0.1
            if (yMin == null) yMin = dataMin - padding
            if (yMax == null) yMax = dataMax + padding
        }

        var options = {
            chart: Object.assign({}, common.chart, { type: 'boxPlot', height: height }),
            title: { text: widget.title, align: 'center', style: { fontSize: '14px' } },
            plotOptions: {
                boxPlot: {
                    colors: {
                        upper: '#aab4c8',
                        lower: '#d8dce6'
                    }
                }
            },
            xaxis: {
                title: { text: widget.xAxisCaption || '' }
            },
            yaxis: {
                title: { text: widget.yAxisCaption || '' },
                min: yMin,
                max: yMax
            },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var data = w.globals.seriesCandleO[seriesIndex][dataPointIndex]
                    if (!data) return ''
                    var point = seriesData[dataPointIndex]
                    if (!point) return ''
                    var q = widget.data[dataPointIndex][1]
                    return '<div style="padding: 8px">' +
                        '<b>' + point.x + '</b><br>' +
                        '- Upper 1.5 IQR: ' + q.upperWhisker + '<br>' +
                        '- Q3: ' + q.upperQuantile + '<br>' +
                        '- Median: ' + q.median + '<br>' +
                        '- Q1: ' + q.lowerQuantile + '<br>' +
                        '- Lower 1.5 IQR: ' + q.lowerWhisker +
                        '</div>'
                }
            },
            noData: common.noData,
            series: [{ data: seriesData }]
        }

        this._renderChart(elementId, options)
    }

    // impl
    _scatterWidget(elementId, widget, filterElement) {
        var that = this
        var common = this._commonOptions()
        var height = widget.displayOptions.height || 400

        var isXDate = widget.xFieldType == "Date"
        var isXDouble = widget.xFieldType == "Double"
        var isYDate = widget.yFieldType == "Date"
        var isYDouble = widget.yFieldType == "Double"

        var series = widget.data.map(function (nameSeries, index) {
            return {
                name: shorten(nameSeries[0]),
                data: nameSeries[1].map(function (pairs) {
                    return { x: pairs[0], y: pairs[1] }
                })
            }
        })

        var options = {
            chart: Object.assign({}, common.chart, {
                type: 'scatter',
                height: height,
                zoom: { enabled: true, type: 'xy', allowMouseWheelZoom: false },
                events: {}
            }),
            title: { text: widget.title, align: 'center', style: { fontSize: '14px' } },
            colors: common.colors,
            xaxis: {
                title: { text: widget.xAxisCaption || '' },
                type: isXDate ? 'datetime' : 'numeric',
                labels: isXDate ? { datetimeFormatter: { year: 'yyyy', month: "MMM 'yy", day: 'dd MMM' } } : {}
            },
            yaxis: {
                title: { text: widget.yAxisCaption || '' },
                type: isYDate ? 'datetime' : undefined,
                labels: isYDate ? { datetimeFormatter: { year: 'yyyy', month: "MMM 'yy", day: 'dd MMM' } } : {}
            },
            markers: { size: 6 },
            legend: { show: series.length > 1, position: 'right' },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var point = w.config.series[seriesIndex].data[dataPointIndex]
                    if (!point) return ''
                    var xVal = that._formatValue(point.x, isXDate, isXDouble, 2)
                    var yVal = that._formatValue(point.y, isYDate, isYDouble, 2)
                    var header = (series.length > 1) ? '<span style="font-size:11px">' + w.config.series[seriesIndex].name + '</span><br>' : ''
                    return '<div style="padding: 8px">' + header + xVal + ', ' + yVal + '</div>'
                }
            },
            noData: common.noData,
            series: series
        }

        if (filterElement) {
            options.chart.events.zoomed = function (chartContext, opts) {
                if (opts.xaxis && opts.xaxis.min != null) {
                    var xMinOut = asTypedStringValue(opts.xaxis.min, isXDouble, isXDate, true)
                    var xMaxOut = asTypedStringValue(opts.xaxis.max, isXDouble, isXDate, false)
                    var yAxis = Array.isArray(opts.yaxis) ? opts.yaxis[0] : opts.yaxis
                    var yMinOut = asTypedStringValue(yAxis.min, isYDouble, isYDate, true)
                    var yMaxOut = asTypedStringValue(yAxis.max, isYDouble, isYDate, false)

                    var conditions = [
                        {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                        {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut},
                        {fieldName: widget.yFieldName, conditionType: ">=", value: yMinOut},
                        {fieldName: widget.yFieldName, conditionType: "<=", value: yMaxOut}
                    ]

                    _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
                }
            }
        }

        this._renderChart(elementId, options)
    }

    // impl
    _valueScatterWidget(elementId, widget, filterElement) {
        var that = this
        var common = this._commonOptions()
        var height = widget.displayOptions.height || 400

        var isXDate = widget.xFieldType == "Date"
        var isXDouble = widget.xFieldType == "Double"
        var isYDate = widget.yFieldType == "Date"
        var isYDouble = widget.yFieldType == "Double"

        var zs = widget.data.map(function (point) { return point[2] })
        var zMin = Math.min.apply(null, zs)
        var zMax = Math.max.apply(null, zs)

        var colors = widget.data.map(function (point) {
            var zColor = (1 - Math.abs((point[2] - zMin) / (zMax - zMin))) * 210
            return 'rgba(255, ' + Math.round(zColor) + ',' + Math.round(zColor) + ', 0.8)'
        })

        var data = widget.data.map(function (point) {
            return { x: point[0], y: point[1] }
        })

        var options = {
            chart: Object.assign({}, common.chart, {
                type: 'scatter',
                height: height,
                zoom: { enabled: true, type: 'xy', allowMouseWheelZoom: false },
                events: {}
            }),
            title: { text: widget.title, align: 'center', style: { fontSize: '14px' } },
            xaxis: {
                title: { text: widget.xAxisCaption || '' },
                type: isXDate ? 'datetime' : 'numeric'
            },
            yaxis: {
                title: { text: widget.yAxisCaption || '' }
            },
            markers: {
                size: 6,
                discrete: widget.data.map(function (point, i) {
                    return { seriesIndex: 0, dataPointIndex: i, fillColor: colors[i], strokeColor: colors[i], size: 6 }
                })
            },
            legend: { show: false },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var point = widget.data[dataPointIndex]
                    if (!point) return ''
                    var xVal = that._formatValue(point[0], isXDate, isXDouble, 2)
                    var yVal = that._formatValue(point[1], isYDate, isYDouble, 2)
                    return '<div style="padding: 8px">' + xVal + ', ' + yVal + ' (' + point[2] + ')</div>'
                }
            },
            noData: common.noData,
            series: [{ name: 'Values', data: data }]
        }

        if (filterElement) {
            options.chart.events.zoomed = function (chartContext, opts) {
                if (opts.xaxis && opts.xaxis.min != null) {
                    var xMinOut = asTypedStringValue(opts.xaxis.min, isXDouble, isXDate, true)
                    var xMaxOut = asTypedStringValue(opts.xaxis.max, isXDouble, isXDate, false)
                    var yAxis = Array.isArray(opts.yaxis) ? opts.yaxis[0] : opts.yaxis
                    var yMinOut = asTypedStringValue(yAxis.min, isYDouble, isYDate, true)
                    var yMaxOut = asTypedStringValue(yAxis.max, isYDouble, isYDate, false)

                    var conditions = [
                        {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                        {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut},
                        {fieldName: widget.yFieldName, conditionType: ">=", value: yMinOut},
                        {fieldName: widget.yFieldName, conditionType: "<=", value: yMaxOut}
                    ]

                    _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
                }
            }
        }

        this._renderChart(elementId, options)
    }

    // impl
    _heatmapWidget(elementId, widget, filterElement) {
        var that = this
        var common = this._commonOptions()
        var height = widget.displayOptions.height || 400

        var xCategories = widget.xCategories
        var yCategories = widget.yCategories

        // ApexCharts heatmap: series[].name = row (y), data[].x = column (x), data[].y = value
        // widget.data is a 2D array: data[i][j] where i = x-index, j = y-index
        var series = yCategories.map(function (ycat, j) {
            return {
                name: ycat,
                data: xCategories.map(function (xcat, i) {
                    return { x: xcat, y: widget.data[i] ? (widget.data[i][j] != null ? widget.data[i][j] : null) : null }
                })
            }
        }).reverse() // reverse so first y-category is at bottom

        var colorStops = (widget.twoColors) ?
            [
                { from: widget.min || -1, to: 0, color: that._catPalette[5], name: 'negative' },
                { from: 0, to: widget.max || 1, color: that._catPalette[0], name: 'positive' }
            ] : undefined

        var options = {
            chart: Object.assign({}, common.chart, { type: 'heatmap', height: height }),
            title: { text: widget.title, align: 'center', style: { fontSize: '14px' } },
            plotOptions: {
                heatmap: {
                    shadeIntensity: 0.5,
                    colorScale: {
                        ranges: colorStops,
                        min: widget.min,
                        max: widget.max
                    }
                }
            },
            colors: widget.twoColors ? undefined : [that._catPalette[0]],
            xaxis: {
                title: { text: widget.xAxisCaption || '' },
                labels: {
                    formatter: function (val) { return shorten(val, 10) }
                }
            },
            yaxis: {
                title: { text: widget.yAxisCaption || '' },
                labels: {
                    formatter: function (val) { return shorten(val, 10) }
                }
            },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var sName = w.config.series[seriesIndex].name
                    var point = w.config.series[seriesIndex].data[dataPointIndex]
                    if (!point) return ''
                    var val = (point.y != null) ? Number(point.y).toFixed(3) : 'Undefined'
                    return '<div style="padding: 8px"><b>' + point.x + '</b><br><b>' + sName + '</b><br>' + val + '</div>'
                }
            },
            dataLabels: { enabled: false },
            noData: common.noData,
            series: series
        }

        this._renderChart(elementId, options)
    }

    // impl
    _lineWidget(elementId, widget, filterElement) {
        var that = this
        var common = this._commonOptions()
        var height = widget.displayOptions.height || 400

        var isXDate = widget.xFieldType == "Date"
        var isXDouble = widget.xFieldType == "Double"
        var isYDate = widget.yFieldType == "Date"
        var isYDouble = widget.yFieldType == "Double"

        var showLegend = widget.data.length > 1

        var series = widget.data.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                data: nameSeries[1].map(function (pairs) {
                    return { x: pairs[0], y: pairs[1] }
                })
            }
        })

        var options = {
            chart: Object.assign({}, common.chart, {
                type: 'line',
                height: height,
                zoom: { enabled: true, type: 'x', allowMouseWheelZoom: false },
                events: {}
            }),
            title: { text: widget.title, align: 'center', style: { fontSize: '14px' } },
            colors: common.colors,
            stroke: { curve: 'straight', width: 2 },
            markers: { size: 4 },
            xaxis: {
                title: { text: widget.xAxisCaption || '' },
                type: isXDate ? 'datetime' : 'numeric',
                min: widget.xMin,
                max: widget.xMax
            },
            yaxis: {
                title: { text: widget.yAxisCaption || '' },
                min: widget.yMin,
                max: widget.yMax
            },
            legend: { show: showLegend, position: 'right' },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var point = w.config.series[seriesIndex].data[dataPointIndex]
                    if (!point) return ''
                    var xVal = that._formatValue(point.x, isXDate, isXDouble, 3)
                    var yVal = that._formatValue(point.y, isYDate, isYDouble, 3)
                    var header = showLegend ? '<span style="font-size:11px">' + w.config.series[seriesIndex].name + '</span><br>' : ''
                    return '<div style="padding: 8px">' + header + xVal + ': <b>' + yVal + '</b></div>'
                }
            },
            noData: common.noData,
            series: series
        }

        if (filterElement) {
            options.chart.events.zoomed = function (chartContext, opts) {
                if (opts.xaxis && opts.xaxis.min != null) {
                    var xMinOut = asTypedStringValue(opts.xaxis.min, isXDouble, isXDate, true)
                    var xMaxOut = asTypedStringValue(opts.xaxis.max, isXDouble, isXDate, false)

                    var conditions = [
                        {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                        {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut}
                    ]

                    _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
                }
            }
        }

        this._renderChart(elementId, options)
    }

    // Shared helper for Column/Bar chart options
    _barOptions({
        datas, seriesSize, title, xAxisCaption, yAxisCaption,
        height, horizontal, useRelativeValues, colorByPoint,
        isDate, isDouble, isNumerical
    }) {
        var that = this
        var common = this._commonOptions()

        var tooltipTexts = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.text })
        })

        // For numerical bar charts, use {x, y} data with numeric axis to enable zoom/selection
        if (isNumerical) {
            var series = datas.map(function (nameSeries) {
                return {
                    name: nameSeries[0],
                    data: nameSeries[1].map(function (item) {
                        return { x: item.x, y: item.y }
                    })
                }
            })

            return {
                chart: Object.assign({}, common.chart, {
                    type: 'bar',
                    height: height,
                    zoom: { enabled: true, type: 'x', allowMouseWheelZoom: false }
                }),
                title: { text: title, align: 'center', style: { fontSize: '14px' } },
                plotOptions: {
                    bar: {
                        horizontal: horizontal,
                        columnWidth: '90%',
                        borderRadius: 2
                    }
                },
                colors: common.colors,
                xaxis: {
                    title: { text: horizontal ? yAxisCaption : xAxisCaption },
                    type: isDate ? 'datetime' : 'numeric',
                    labels: {
                        formatter: function (val) { return that._formatValue(val, isDate, isDouble, 2) }
                    }
                },
                yaxis: {
                    title: { text: horizontal ? xAxisCaption : yAxisCaption }
                },
                legend: { show: seriesSize > 1 },
                dataLabels: { enabled: false },
                tooltip: {
                    custom: function ({ seriesIndex, dataPointIndex, w }) {
                        var point = w.config.series[seriesIndex].data[dataPointIndex]
                        if (!point) return ''
                        var xVal = that._formatValue(point.x, isDate, isDouble, 2)
                        var text = tooltipTexts[seriesIndex] ? tooltipTexts[seriesIndex][dataPointIndex] : ''
                        var header = (seriesSize > 1) ? '<span style="font-size:11px">' + w.config.series[seriesIndex].name + '</span><br>' : ''
                        return '<div style="padding: 8px">' + header + xVal + ': <b>' + text + '</b></div>'
                    }
                },
                noData: common.noData,
                series: series
            }
        }

        // Categorical bar charts: use categories on x-axis
        var categories = datas[0][1].map(function (item) { return item.x })

        var series = datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                data: nameSeries[1].map(function (item) { return item.y })
            }
        })

        var colors = common.colors
        if (colorByPoint && seriesSize === 1) {
            var size = datas[0][1].length
            colors = []
            for (var i = 0; i < size; i++) {
                colors.push(that._catPalette[i % that._catPaletteSize])
            }
        }

        var options = {
            chart: Object.assign({}, common.chart, { type: 'bar', height: height }),
            title: { text: title, align: 'center', style: { fontSize: '14px' } },
            plotOptions: {
                bar: {
                    horizontal: horizontal,
                    distributed: colorByPoint && seriesSize === 1,
                    columnWidth: '70%',
                    borderRadius: 2
                }
            },
            colors: colors,
            xaxis: {
                categories: categories,
                title: { text: horizontal ? yAxisCaption : xAxisCaption }
            },
            yaxis: {
                title: { text: horizontal ? xAxisCaption : yAxisCaption }
            },
            legend: { show: seriesSize > 1 },
            dataLabels: { enabled: false },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var catName = categories[dataPointIndex]
                    var text = tooltipTexts[seriesIndex] ? tooltipTexts[seriesIndex][dataPointIndex] : ''
                    var header = (seriesSize > 1) ? '<span style="font-size:11px">' + w.config.series[seriesIndex].name + '</span><br>' : ''
                    return '<div style="padding: 8px">' + header + catName + ': <b>' + text + '</b></div>'
                }
            },
            noData: common.noData,
            series: series
        }

        return options
    }

    // Shared helper for categorical Line/Spline options
    _lineOptions({ datas, title, xAxisCaption, yAxisCaption, height, smooth, seriesSize }) {
        var that = this
        var common = this._commonOptions()

        var categories = datas[0][1].map(function (item) { return item.x })

        var series = datas.map(function (nameSeries) {
            return {
                name: nameSeries[0],
                data: nameSeries[1].map(function (item) { return item.y })
            }
        })

        var tooltipTexts = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.text })
        })

        return {
            chart: Object.assign({}, common.chart, { type: 'line', height: height }),
            title: { text: title, align: 'center', style: { fontSize: '14px' } },
            colors: common.colors,
            stroke: { curve: smooth ? 'smooth' : 'straight', width: 2 },
            markers: { size: 4 },
            xaxis: {
                categories: categories,
                title: { text: xAxisCaption || '' }
            },
            yaxis: {
                title: { text: yAxisCaption || '' }
            },
            legend: { show: seriesSize > 1 },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var catName = categories[dataPointIndex]
                    var text = tooltipTexts[seriesIndex] ? tooltipTexts[seriesIndex][dataPointIndex] : ''
                    var header = (seriesSize > 1) ? '<span style="font-size:11px">' + w.config.series[seriesIndex].name + '</span><br>' : ''
                    return '<div style="padding: 8px">' + header + catName + ': <b>' + text + '</b></div>'
                }
            },
            noData: common.noData,
            series: series
        }
    }

    // Numerical line/spline helper (x-values are numeric, not categories)
    _numericalLineOptions({ datas, title, xAxisCaption, yAxisCaption, height, smooth, seriesSize, isDate, isDouble }) {
        var that = this
        var common = this._commonOptions()

        var series = datas.map(function (nameSeries) {
            return {
                name: nameSeries[0],
                data: nameSeries[1].map(function (item) {
                    return { x: item.x, y: item.y }
                })
            }
        })

        var tooltipTexts = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.text })
        })

        return {
            chart: Object.assign({}, common.chart, { type: 'line', height: height }),
            title: { text: title, align: 'center', style: { fontSize: '14px' } },
            colors: common.colors,
            stroke: { curve: smooth ? 'smooth' : 'straight', width: 2 },
            markers: { size: 4 },
            xaxis: {
                title: { text: xAxisCaption || '' },
                type: isDate ? 'datetime' : 'numeric'
            },
            yaxis: {
                title: { text: yAxisCaption || '' }
            },
            legend: { show: seriesSize > 1 },
            tooltip: {
                custom: function ({ seriesIndex, dataPointIndex, w }) {
                    var point = w.config.series[seriesIndex].data[dataPointIndex]
                    if (!point) return ''
                    var xVal = that._formatValue(point.x, isDate, isDouble, 2)
                    var text = tooltipTexts[seriesIndex] ? tooltipTexts[seriesIndex][dataPointIndex] : ''
                    var header = (seriesSize > 1) ? '<span style="font-size:11px">' + w.config.series[seriesIndex].name + '</span><br>' : ''
                    return '<div style="padding: 8px">' + header + xVal + ': <b>' + text + '</b></div>'
                }
            },
            noData: { text: 'No data available' },
            series: series
        }
    }

    // Polar (radar) chart helper
    _polarOptions({ datas, title, height, seriesSize }) {
        var common = this._commonOptions()

        var categories = datas[0][1].map(function (item) { return item.x })

        var series = datas.map(function (nameSeries) {
            return {
                name: nameSeries[0],
                data: nameSeries[1].map(function (item) { return item.y })
            }
        })

        return {
            chart: Object.assign({}, common.chart, { type: 'radar', height: height }),
            title: { text: title, align: 'center', style: { fontSize: '14px' } },
            colors: common.colors,
            xaxis: { categories: categories },
            yaxis: { show: true },
            legend: { show: seriesSize > 1 },
            stroke: { width: 2 },
            fill: { opacity: 0.2 },
            markers: { size: 3 },
            noData: { text: 'No data available' },
            series: series
        }
    }

    // Filter integration helpers

    _addPointClicked(elementId, filterElement, fieldName, datas) {
        var chart = this._charts[elementId]
        if (!chart) return

        // Build a lookup from dataPointIndex to key using the original datas
        // datas = [[name, [{x, y, text, key}, ...]], ...]
        var keysBySeriesIndex = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.key })
        })

        chart.updateOptions({
            chart: {
                events: {
                    dataPointSelection: function (event, chartContext, config) {
                        var seriesIndex = config.seriesIndex
                        var dataPointIndex = config.dataPointIndex

                        // For pie/donut, seriesIndex is -1 and dataPointIndex is the slice index
                        var si = (seriesIndex >= 0) ? seriesIndex : 0
                        var key = (keysBySeriesIndex[si] && keysBySeriesIndex[si][dataPointIndex] != null)
                            ? keysBySeriesIndex[si][dataPointIndex]
                            : null

                        if (key != null) {
                            var condition = {fieldName: fieldName, conditionType: "=", value: key}
                            _callMultiFilter(filterElement, 'replaceWithConditionAndSubmit', condition)
                        }
                    }
                }
            },
            states: {
                active: { filter: { type: 'darken', value: 0.65 } }
            }
        }, false, false)
    }

    _addXAxisZoomed(elementId, filterElement, fieldName, isDouble, isDate) {
        var chart = this._charts[elementId]
        if (!chart) return

        chart.updateOptions({
            chart: {
                events: {
                    zoomed: function (chartContext, opts) {
                        if (opts.xaxis && opts.xaxis.min != null) {
                            var xMinOut = asTypedStringValue(opts.xaxis.min, isDouble, isDate, true)
                            var xMaxOut = asTypedStringValue(opts.xaxis.max, isDouble, isDate, false)

                            var conditions = [
                                {fieldName: fieldName, conditionType: ">=", value: xMinOut},
                                {fieldName: fieldName, conditionType: "<=", value: xMaxOut}
                            ]

                            _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
                        }
                    }
                },
                zoom: { enabled: true, type: 'x', allowMouseWheelZoom: false }
            }
        }, false, false)
    }

    refresh() {
        var that = this
        Object.keys(this._charts).forEach(function (key) {
            var chart = that._charts[key]
            if (chart) {
                try {
                    chart.updateOptions({}, false, false)
                } catch (e) {
                    // chart may have been removed from DOM
                }
            }
        })
    }

    export(charts, format, filename) {
        var that = this

        function exportFun(chartId, svgCallback) {
            var el = document.getElementById(chartId)
            var svgEl = el ? el.querySelector('.apexcharts-svg') : null
            if (svgEl) {
                svgCallback(svgEl.outerHTML)
            } else {
                svgCallback(null)
            }
        }

        function downloadAsCanvas(srcURL, width, height, type) {
            var img = new Image()
            img.src = srcURL
            img.onload = function () {
                var canvas = document.createElement('canvas')
                canvas.width = width
                canvas.height = height
                var ctx = canvas.getContext('2d')
                ctx.drawImage(img, 0, 0)

                var canvasUrl = canvas.toDataURL(type)
                downloadFile(canvasUrl, filename)
            }
        }

        function downloadAsPdf(svg) {
            var svgContainer = document.createElement('div')
            svgContainer.innerHTML = svg

            var svgElement = svgContainer.firstChild
            var margin = 0

            var widthx = svgElement.width.baseVal.value + 2 * margin
            var heightx = svgElement.height.baseVal.value + 2 * margin

            var pdf = new jsPDF('l', 'pt', [widthx + 2 * margin, heightx + 2 * margin])
            svg2pdf(svgElement, pdf, { removeInvalid: true })

            var pdfUri = pdf.output('datauristring')
            downloadFile(pdfUri, filename)
        }

        this._combineSVGs(charts, exportFun, function (svg) {
            var svgWidth = getSVGWidth(svg)
            var svgHeight = getSVGHeight(svg)

            var svgDataURL = svgToDataUrl(svg)

            switch (format) {
                case 'image/svg+xml':
                    downloadFile(svgDataURL, filename)
                    break
                case 'image/jpeg':
                    downloadAsCanvas(svgDataURL, svgWidth, svgHeight, 'image/jpeg')
                    break
                case 'image/png':
                    downloadAsCanvas(svgDataURL, svgWidth, svgHeight, 'image/png')
                    break
                case 'application/pdf':
                    downloadAsPdf(svg)
                    break
                default:
                    throw "Export for the format " + format + " is not implemented."
            }
        })
    }
}
