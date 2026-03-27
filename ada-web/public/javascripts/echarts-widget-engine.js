class EChartsWidgetEngine extends WidgetEngine {

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

    // Helper: dispose old chart, create new SVG-rendered instance, track it
    _renderChart(elementId, options, height) {
        var el = document.getElementById(elementId)
        if (!el) return null

        if (this._charts[elementId]) {
            if (!this._charts[elementId].isDisposed()) {
                this._charts[elementId].dispose()
            }
            delete this._charts[elementId]
        }

        var h = height || 400
        el.innerHTML = ''
        el.style.height = h + 'px'
        var chart = echarts.init(el, null, {renderer: 'svg', height: h})
        chart.setOption(options, true)
        this._charts[elementId] = chart
        return chart
    }

    _addChartTypeMenu(elementId) {
        var el = document.getElementById(elementId)

        // Create a wrapper around the chart element so the menu lives outside ECharts' DOM
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

    _commonOptions(height) {
        return {
            title: {
                textStyle: { fontSize: 14, fontFamily: 'Helvetica, Arial, sans-serif' },
                left: 'center'
            },
            color: this._catPalette,
            textStyle: { fontFamily: 'Helvetica, Arial, sans-serif' },
            grid: { left: 60, right: 30, top: 50, bottom: 50, containLabel: true },
            animation: true,
            animationDuration: 400
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

                var percent = (sum === 0) ? 0 : 100 * count / sum
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
        var options

        switch (chartType) {
            case 'Pie':
                options = this._pieOptions({
                    datas: datas, seriesSize: seriesSize, title: title,
                    height: height, showLabels: showLabels, useRelativeValues: useRelativeValues
                })
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
                options = this._catLineOptions({
                    datas: datas, title: title,
                    xAxisCaption: '', yAxisCaption: yAxisCaption,
                    height: height, smooth: false, seriesSize: seriesSize
                })
                break
            case 'Spline':
                options = this._catLineOptions({
                    datas: datas, title: title,
                    xAxisCaption: '', yAxisCaption: yAxisCaption,
                    height: height, smooth: true, seriesSize: seriesSize
                })
                break
            case 'Polar':
                options = this._radarOptions({
                    datas: datas, title: title, height: height, seriesSize: seriesSize
                })
                break
        }

        if (options) {
            this._renderChart(chartElementId, options, height)
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

                var percent = (sum === 0) ? 0 : 100 * count / sum
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
                // Format x values as labels for pie
                var formattedDatas = datas.map(function (nameSeries) {
                    var name = nameSeries[0]
                    var data = nameSeries[1].map(function (item) {
                        var itemNew = Object.assign({}, item)
                        itemNew.x = that._formatValue(itemNew.x, isDate, isDouble, 2)
                        return itemNew
                    })
                    return [name, data]
                })

                options = this._pieOptions({
                    datas: formattedDatas, seriesSize: seriesSize, title: title,
                    height: height, showLabels: false, useRelativeValues: useRelativeValues
                })
                break

            case 'Column':
                options = this._numBarOptions({
                    datas: datas, seriesSize: seriesSize, title: title,
                    xAxisCaption: xAxisCaption, yAxisCaption: yAxisCaption,
                    height: height, horizontal: false, isDate: isDate, isDouble: isDouble
                })
                break

            case 'Bar':
                options = this._numBarOptions({
                    datas: datas, seriesSize: seriesSize, title: title,
                    xAxisCaption: yAxisCaption, yAxisCaption: xAxisCaption,
                    height: height, horizontal: true, isDate: isDate, isDouble: isDouble
                })
                break

            case 'Line':
                options = this._numLineOptions({
                    datas: datas, title: title,
                    xAxisCaption: xAxisCaption, yAxisCaption: yAxisCaption,
                    height: height, smooth: false, seriesSize: seriesSize,
                    isDate: isDate, isDouble: isDouble
                })
                break

            case 'Spline':
                options = this._numLineOptions({
                    datas: datas, title: title,
                    xAxisCaption: xAxisCaption, yAxisCaption: yAxisCaption,
                    height: height, smooth: true, seriesSize: seriesSize,
                    isDate: isDate, isDouble: isDouble
                })
                break

            case 'Polar':
                options = this._radarOptions({
                    datas: datas, title: title, height: height, seriesSize: seriesSize
                })
                break
        }

        if (options) {
            var chart = this._renderChart(chartElementId, options, height)
            if (chart && chartType !== 'Pie' && chartType !== 'Polar') {
                chart.dispatchAction({
                    type: 'takeGlobalCursor',
                    key: 'dataZoomSelect',
                    dataZoomSelectActive: true
                })
            }
        }
    }

    // impl
    _boxWidget(elementId, widget) {
        var that = this
        var common = this._commonOptions()
        var height = widget.displayOptions.height || 400

        var categories = widget.data.map(function (nq) { return nq[0] })

        // ECharts boxplot: data as [min, Q1, median, Q3, max]
        var boxData = widget.data.map(function (nq) {
            var q = nq[1]
            return [q.lowerWhisker, q.lowerQuantile, q.median, q.upperQuantile, q.upperWhisker]
        })

        // Compute y-axis range
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
            title: Object.assign({}, common.title, {text: widget.title}),
            color: common.color,
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var idx = params.dataIndex
                    var q = widget.data[idx][1]
                    return '<b>' + categories[idx] + '</b><br>' +
                        '- Upper 1.5 IQR: ' + q.upperWhisker + '<br>' +
                        '- Q3: ' + q.upperQuantile + '<br>' +
                        '- Median: ' + q.median + '<br>' +
                        '- Q1: ' + q.lowerQuantile + '<br>' +
                        '- Lower 1.5 IQR: ' + q.lowerWhisker
                }
            },
            xAxis: {
                type: 'category',
                data: categories,
                name: widget.xAxisCaption || ''
            },
            yAxis: {
                type: 'value',
                name: widget.yAxisCaption || '',
                min: yMin,
                max: yMax
            },
            series: [{
                type: 'boxplot',
                data: boxData,
                itemStyle: {
                    color: '#d8dce6',
                    borderColor: that._catPalette[0]
                }
            }]
        }

        this._renderChart(elementId, options, height)
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

        var seriesArr = widget.data.map(function (nameSeries, index) {
            return {
                name: shorten(nameSeries[0]),
                type: 'scatter',
                data: nameSeries[1].map(function (pairs) {
                    return [pairs[0], pairs[1]]
                }),
                symbolSize: 8,
                itemStyle: {
                    color: that._catPalette[index % that._catPaletteSize]
                }
            }
        })

        var showLegend = seriesArr.length > 1

        var options = {
            title: Object.assign({}, common.title, {text: widget.title}),
            color: common.color,
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var xVal = that._formatValue(params.value[0], isXDate, isXDouble, 2)
                    var yVal = that._formatValue(params.value[1], isYDate, isYDouble, 2)
                    var header = showLegend ? '<span style="font-size:11px">' + params.seriesName + '</span><br>' : ''
                    return header + xVal + ', ' + yVal
                }
            },
            xAxis: {
                type: isXDate ? 'time' : 'value',
                name: widget.xAxisCaption || '',
                nameLocation: 'center',
                nameGap: 30
            },
            yAxis: {
                type: isYDate ? 'time' : 'value',
                name: widget.yAxisCaption || ''
            },
            legend: { show: showLegend, bottom: 0 },
            dataZoom: [
                {type: 'inside', xAxisIndex: 0, zoomOnMouseWheel: false},
                {type: 'inside', yAxisIndex: 0, zoomOnMouseWheel: false}
            ],
            toolbox: {
                feature: {
                    dataZoom: {yAxisIndex: 'none'},
                    restore: {}
                },
                right: 20
            },
            series: seriesArr
        }

        var chart = this._renderChart(elementId, options, height)

        if (filterElement && chart) {
            chart.on('datazoom', this._debounce(function () {
                var xAxisModel = chart.getModel().getComponent('xAxis', 0)
                var yAxisModel = chart.getModel().getComponent('yAxis', 0)
                if (!xAxisModel || !yAxisModel) return

                var xExtent = xAxisModel.axis.scale.getExtent()
                var yExtent = yAxisModel.axis.scale.getExtent()

                var xMinOut = asTypedStringValue(xExtent[0], isXDouble, isXDate, true)
                var xMaxOut = asTypedStringValue(xExtent[1], isXDouble, isXDate, false)
                var yMinOut = asTypedStringValue(yExtent[0], isYDouble, isYDate, true)
                var yMaxOut = asTypedStringValue(yExtent[1], isYDouble, isYDate, false)

                var conditions = [
                    {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                    {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut},
                    {fieldName: widget.yFieldName, conditionType: ">=", value: yMinOut},
                    {fieldName: widget.yFieldName, conditionType: "<=", value: yMaxOut}
                ]

                _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
            }, 300))
        }
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

        var data = widget.data.map(function (point) {
            return [point[0], point[1], point[2]]
        })

        var options = {
            title: Object.assign({}, common.title, {text: widget.title}),
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var xVal = that._formatValue(params.value[0], isXDate, isXDouble, 2)
                    var yVal = that._formatValue(params.value[1], isYDate, isYDouble, 2)
                    return xVal + ', ' + yVal + ' (' + params.value[2] + ')'
                }
            },
            xAxis: {
                type: isXDate ? 'time' : 'value',
                name: widget.xAxisCaption || '',
                nameLocation: 'center',
                nameGap: 30
            },
            yAxis: {
                type: isYDate ? 'time' : 'value',
                name: widget.yAxisCaption || ''
            },
            visualMap: {
                min: zMin,
                max: zMax,
                dimension: 2,
                inRange: {
                    color: ['#ffffff', '#ff0000']
                },
                calculable: true,
                orient: 'vertical',
                right: 10,
                top: 'center'
            },
            dataZoom: [
                {type: 'inside', xAxisIndex: 0, zoomOnMouseWheel: false},
                {type: 'inside', yAxisIndex: 0, zoomOnMouseWheel: false}
            ],
            toolbox: {
                feature: {
                    dataZoom: {yAxisIndex: 'none'},
                    restore: {}
                },
                right: 60
            },
            series: [{
                type: 'scatter',
                data: data,
                symbolSize: 8
            }]
        }

        var chart = this._renderChart(elementId, options, height)

        if (filterElement && chart) {
            chart.on('datazoom', this._debounce(function () {
                var xAxisModel = chart.getModel().getComponent('xAxis', 0)
                var yAxisModel = chart.getModel().getComponent('yAxis', 0)
                if (!xAxisModel || !yAxisModel) return

                var xExtent = xAxisModel.axis.scale.getExtent()
                var yExtent = yAxisModel.axis.scale.getExtent()

                var xMinOut = asTypedStringValue(xExtent[0], isXDouble, isXDate, true)
                var xMaxOut = asTypedStringValue(xExtent[1], isXDouble, isXDate, false)
                var yMinOut = asTypedStringValue(yExtent[0], isYDouble, isYDate, true)
                var yMaxOut = asTypedStringValue(yExtent[1], isYDouble, isYDate, false)

                var conditions = [
                    {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                    {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut},
                    {fieldName: widget.yFieldName, conditionType: ">=", value: yMinOut},
                    {fieldName: widget.yFieldName, conditionType: "<=", value: yMaxOut}
                ]

                _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
            }, 300))
        }
    }

    // impl
    _heatmapWidget(elementId, widget, filterElement) {
        var that = this
        var common = this._commonOptions()
        var height = widget.displayOptions.height || 400

        var xCategories = widget.xCategories
        var yCategories = widget.yCategories

        // Transform 2D array widget.data[xIdx][yIdx] to flat [[xIdx, yIdx, value], ...]
        var flatData = []
        for (var i = 0; i < xCategories.length; i++) {
            for (var j = 0; j < yCategories.length; j++) {
                var val = (widget.data[i] && widget.data[i][j] != null) ? widget.data[i][j] : null
                flatData.push([i, j, val])
            }
        }

        var colorStops
        if (widget.twoColors) {
            colorStops = [
                {offset: 0, color: that._catPalette[5]},
                {offset: 0.5, color: '#FFFFFF'},
                {offset: 1, color: that._catPalette[0]}
            ]
        } else {
            colorStops = [
                {offset: 0, color: '#FFFFFF'},
                {offset: 1, color: that._catPalette[0]}
            ]
        }

        var options = {
            title: Object.assign({}, common.title, {text: widget.title}),
            textStyle: common.textStyle,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var xName = xCategories[params.value[0]] || ''
                    var yName = yCategories[params.value[1]] || ''
                    var val = (params.value[2] != null) ? Number(params.value[2]).toFixed(3) : 'Undefined'
                    return '<b>' + xName + '</b><br><b>' + yName + '</b><br>' + val
                }
            },
            grid: {
                left: 80,
                right: 80,
                top: 50,
                bottom: 60,
                containLabel: true
            },
            xAxis: {
                type: 'category',
                data: xCategories.map(function (c) { return shorten(c, 10) }),
                name: widget.xAxisCaption || '',
                splitArea: {show: true},
                axisLabel: {
                    rotate: xCategories.length > 10 ? 45 : 0
                }
            },
            yAxis: {
                type: 'category',
                data: yCategories.map(function (c) { return shorten(c, 10) }),
                name: widget.yAxisCaption || '',
                splitArea: {show: true}
            },
            visualMap: {
                min: widget.min != null ? widget.min : 0,
                max: widget.max != null ? widget.max : 1,
                calculable: true,
                orient: 'vertical',
                right: 10,
                top: 'center',
                inRange: {
                    color: colorStops.map(function (s) { return s.color })
                }
            },
            series: [{
                type: 'heatmap',
                data: flatData.filter(function (d) { return d[2] != null }),
                label: {show: false},
                emphasis: {
                    itemStyle: {
                        shadowBlur: 10,
                        shadowColor: 'rgba(0, 0, 0, 0.5)'
                    }
                }
            }]
        }

        this._renderChart(elementId, options, height)
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

        var seriesArr = widget.data.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                type: 'line',
                data: nameSeries[1].map(function (pairs) {
                    return [pairs[0], pairs[1]]
                }),
                symbolSize: 6,
                itemStyle: {
                    color: that._catPalette[index % that._catPaletteSize]
                }
            }
        })

        var options = {
            title: Object.assign({}, common.title, {text: widget.title}),
            color: common.color,
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var xVal = that._formatValue(params.value[0], isXDate, isXDouble, 3)
                    var yVal = that._formatValue(params.value[1], isYDate, isYDouble, 3)
                    var header = showLegend ? '<span style="font-size:11px">' + params.seriesName + '</span><br>' : ''
                    return header + xVal + ': <b>' + yVal + '</b>'
                }
            },
            xAxis: {
                type: isXDate ? 'time' : 'value',
                name: widget.xAxisCaption || '',
                nameLocation: 'center',
                nameGap: 30,
                min: widget.xMin,
                max: widget.xMax
            },
            yAxis: {
                type: isYDate ? 'time' : 'value',
                name: widget.yAxisCaption || '',
                min: widget.yMin,
                max: widget.yMax
            },
            legend: { show: showLegend, bottom: 0 },
            dataZoom: [
                {type: 'inside', xAxisIndex: 0, zoomOnMouseWheel: false}
            ],
            toolbox: {
                feature: {
                    dataZoom: {yAxisIndex: 'none'},
                    restore: {}
                },
                right: 20
            },
            series: seriesArr
        }

        var chart = this._renderChart(elementId, options, height)

        if (filterElement && chart) {
            chart.on('datazoom', this._debounce(function () {
                var xAxisModel = chart.getModel().getComponent('xAxis', 0)
                if (!xAxisModel) return

                var xExtent = xAxisModel.axis.scale.getExtent()

                var xMinOut = asTypedStringValue(xExtent[0], isXDouble, isXDate, true)
                var xMaxOut = asTypedStringValue(xExtent[1], isXDouble, isXDate, false)

                var conditions = [
                    {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                    {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut}
                ]

                _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
            }, 300))
        }
    }

    //////////////////////////
    // Chart Option Helpers //
    //////////////////////////

    // Pie chart options (for both categorical and numerical)
    _pieOptions({ datas, seriesSize, title, height, showLabels, useRelativeValues }) {
        var that = this
        var common = this._commonOptions()

        if (seriesSize === 1) {
            var d = datas[0][1]
            return {
                title: Object.assign({}, common.title, {text: title}),
                color: common.color,
                textStyle: common.textStyle,
                animation: common.animation,
                tooltip: {
                    trigger: 'item',
                    formatter: function (params) {
                        var idx = params.dataIndex
                        var item = d[idx]
                        return item ? item.x + ': <b>' + item.text + '</b>' : ''
                    }
                },
                legend: {
                    show: true,
                    bottom: 0,
                    type: 'scroll'
                },
                series: [{
                    type: 'pie',
                    radius: '60%',
                    center: ['50%', '50%'],
                    data: d.map(function (item, i) {
                        return {
                            name: item.x,
                            value: item.y,
                            _key: item.key
                        }
                    }),
                    label: {
                        show: showLabels !== false,
                        formatter: function (params) {
                            return useRelativeValues ? params.percent.toFixed(1) + '%' : params.value
                        }
                    },
                    emphasis: {
                        itemStyle: {
                            shadowBlur: 10,
                            shadowOffsetX: 0,
                            shadowColor: 'rgba(0, 0, 0, 0.5)'
                        }
                    }
                }]
            }
        } else {
            // Multiple series: concentric rings
            var ringStep = 50 / seriesSize
            var seriesArr = datas.map(function (nameSeries, index) {
                var d = nameSeries[1]
                var innerRadius = (10 + index * ringStep) + '%'
                var outerRadius = (10 + (index + 1) * ringStep - 2) + '%'
                return {
                    type: 'pie',
                    radius: [innerRadius, outerRadius],
                    center: ['50%', '50%'],
                    name: nameSeries[0],
                    data: d.map(function (item) {
                        return {
                            name: item.x,
                            value: item.y,
                            _key: item.key
                        }
                    }),
                    label: {
                        show: index === seriesSize - 1 && showLabels !== false,
                        position: 'outside'
                    }
                }
            })

            return {
                title: Object.assign({}, common.title, {text: title}),
                color: common.color,
                textStyle: common.textStyle,
                animation: common.animation,
                tooltip: {
                    trigger: 'item',
                    formatter: function (params) {
                        return params.seriesName + '<br>' + params.name + ': <b>' + params.value + '</b>'
                    }
                },
                legend: {
                    show: true,
                    bottom: 0,
                    type: 'scroll'
                },
                series: seriesArr
            }
        }
    }

    // Categorical bar (column/horizontal bar) options
    _barOptions({
        datas, seriesSize, title, xAxisCaption, yAxisCaption,
        height, horizontal, useRelativeValues, colorByPoint
    }) {
        var that = this
        var common = this._commonOptions()

        var categories = datas[0][1].map(function (item) { return item.x })

        var tooltipTexts = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.text })
        })

        var seriesArr = datas.map(function (nameSeries, index) {
            var d = nameSeries[1]
            var itemColors = null
            if (colorByPoint && seriesSize === 1) {
                itemColors = d.map(function (item, i) {
                    return that._catPalette[i % that._catPaletteSize]
                })
            }

            return {
                name: nameSeries[0],
                type: 'bar',
                data: d.map(function (item, i) {
                    var entry = {
                        value: item.y,
                        _key: item.key
                    }
                    if (itemColors) {
                        entry.itemStyle = {color: itemColors[i]}
                    }
                    return entry
                }),
                itemStyle: (colorByPoint && seriesSize === 1) ? {} : {
                    color: that._catPalette[index % that._catPaletteSize]
                },
                barMaxWidth: '70%'
            }
        })

        var maxLabelLen = horizontal ? 30 : 15
        var catAxis = {
            type: 'category',
            data: categories,
            name: horizontal ? yAxisCaption : xAxisCaption,
            axisLabel: {
                rotate: (!horizontal && categories.length > 10) ? 45 : 0,
                formatter: function (value) {
                    if (value && value.length > maxLabelLen) {
                        return value.substring(0, maxLabelLen) + '\u2026'
                    }
                    return value
                }
            }
        }
        var valAxis = {
            type: 'value',
            name: horizontal ? xAxisCaption : yAxisCaption
        }

        return {
            title: Object.assign({}, common.title, {text: title}),
            color: common.color,
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'axis',
                axisPointer: { type: 'shadow' },
                formatter: function (params) {
                    if (!params || params.length === 0) return ''
                    var catName = categories[params[0].dataIndex]
                    var lines = params.map(function (p) {
                        var text = tooltipTexts[p.seriesIndex] ? tooltipTexts[p.seriesIndex][p.dataIndex] : ''
                        if (seriesSize > 1) {
                            return '<span style="font-size:11px">' + p.seriesName + '</span>: <b>' + text + '</b>'
                        }
                        return '<b>' + text + '</b>'
                    })
                    return catName + '<br>' + lines.join('<br>')
                }
            },
            xAxis: horizontal ? valAxis : catAxis,
            yAxis: horizontal ? catAxis : valAxis,
            legend: { show: seriesSize > 1, bottom: 0 },
            series: seriesArr
        }
    }

    // Numerical bar (column/horizontal bar) options — numeric x-axis with zoom
    _numBarOptions({
        datas, seriesSize, title, xAxisCaption, yAxisCaption,
        height, horizontal, isDate, isDouble
    }) {
        var that = this
        var common = this._commonOptions()

        var tooltipTexts = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.text })
        })

        var seriesArr = datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                type: 'bar',
                data: nameSeries[1].map(function (item) {
                    return [item.x, item.y]
                }),
                itemStyle: {
                    color: that._catPalette[index % that._catPaletteSize]
                },
                barMaxWidth: '90%'
            }
        })

        var numAxis = {
            type: isDate ? 'time' : 'value',
            name: horizontal ? yAxisCaption : xAxisCaption,
            min: 'dataMin',
            axisLabel: {
                formatter: function (val) { return that._formatValue(val, isDate, isDouble, 2) }
            }
        }
        var valAxis = {
            type: 'value',
            name: horizontal ? xAxisCaption : yAxisCaption
        }

        return {
            title: Object.assign({}, common.title, {text: title}),
            color: common.color,
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var xVal = that._formatValue(params.value[0], isDate, isDouble, 2)
                    var text = tooltipTexts[params.seriesIndex] ? tooltipTexts[params.seriesIndex][params.dataIndex] : ''
                    var header = (seriesSize > 1) ? '<span style="font-size:11px">' + params.seriesName + '</span><br>' : ''
                    return header + xVal + ': <b>' + text + '</b>'
                }
            },
            xAxis: horizontal ? valAxis : numAxis,
            yAxis: horizontal ? numAxis : valAxis,
            legend: { show: seriesSize > 1, bottom: 0 },
            dataZoom: [
                {type: 'inside', xAxisIndex: 0, zoomOnMouseWheel: false}
            ],
            toolbox: {
                feature: {
                    dataZoom: {yAxisIndex: 'none'},
                    restore: {}
                },
                right: 20
            },
            series: seriesArr
        }
    }

    // Categorical line/spline options
    _catLineOptions({ datas, title, xAxisCaption, yAxisCaption, height, smooth, seriesSize }) {
        var that = this
        var common = this._commonOptions()

        var categories = datas[0][1].map(function (item) { return item.x })

        var tooltipTexts = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.text })
        })

        var seriesArr = datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                type: 'line',
                smooth: smooth,
                data: nameSeries[1].map(function (item) { return item.y }),
                symbolSize: 6,
                itemStyle: {
                    color: that._catPalette[index % that._catPaletteSize]
                }
            }
        })

        return {
            title: Object.assign({}, common.title, {text: title}),
            color: common.color,
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var catName = categories[params.dataIndex]
                    var text = tooltipTexts[params.seriesIndex] ? tooltipTexts[params.seriesIndex][params.dataIndex] : ''
                    var header = (seriesSize > 1) ? '<span style="font-size:11px">' + params.seriesName + '</span><br>' : ''
                    return header + catName + ': <b>' + text + '</b>'
                }
            },
            xAxis: {
                type: 'category',
                data: categories,
                name: xAxisCaption || '',
                axisLabel: {
                    rotate: categories.length > 10 ? 45 : 0,
                    formatter: function (value) {
                        if (value && value.length > 15) {
                            return value.substring(0, 15) + '\u2026'
                        }
                        return value
                    }
                }
            },
            yAxis: {
                type: 'value',
                name: yAxisCaption || ''
            },
            legend: { show: seriesSize > 1, bottom: 0 },
            series: seriesArr
        }
    }

    // Numerical line/spline options (numeric x-axis)
    _numLineOptions({ datas, title, xAxisCaption, yAxisCaption, height, smooth, seriesSize, isDate, isDouble }) {
        var that = this
        var common = this._commonOptions()

        var tooltipTexts = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.text })
        })

        var seriesArr = datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                type: 'line',
                smooth: smooth,
                data: nameSeries[1].map(function (item) {
                    return [item.x, item.y]
                }),
                symbolSize: 6,
                itemStyle: {
                    color: that._catPalette[index % that._catPaletteSize]
                }
            }
        })

        return {
            title: Object.assign({}, common.title, {text: title}),
            color: common.color,
            textStyle: common.textStyle,
            grid: common.grid,
            animation: common.animation,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    var xVal = that._formatValue(params.value[0], isDate, isDouble, 2)
                    var text = tooltipTexts[params.seriesIndex] ? tooltipTexts[params.seriesIndex][params.dataIndex] : ''
                    var header = (seriesSize > 1) ? '<span style="font-size:11px">' + params.seriesName + '</span><br>' : ''
                    return header + xVal + ': <b>' + text + '</b>'
                }
            },
            xAxis: {
                type: isDate ? 'time' : 'value',
                name: xAxisCaption || '',
                nameLocation: 'center',
                nameGap: 30,
                min: 'dataMin'
            },
            yAxis: {
                type: 'value',
                name: yAxisCaption || ''
            },
            legend: { show: seriesSize > 1, bottom: 0 },
            dataZoom: [
                {type: 'inside', xAxisIndex: 0, zoomOnMouseWheel: false}
            ],
            toolbox: {
                feature: {
                    dataZoom: {yAxisIndex: 'none'},
                    restore: {}
                },
                right: 20
            },
            series: seriesArr
        }
    }

    // Radar (polar) chart options
    _radarOptions({ datas, title, height, seriesSize }) {
        var that = this
        var common = this._commonOptions()

        var categories = datas[0][1].map(function (item) { return item.x })

        // Compute global max across all series for indicator scaling
        var maxVal = 0
        datas.forEach(function (nameSeries) {
            nameSeries[1].forEach(function (item) {
                if (item.y > maxVal) maxVal = item.y
            })
        })
        maxVal = Math.max(maxVal * 1.1, 1)

        var indicator = categories.map(function (cat) {
            return {name: cat, max: maxVal}
        })

        var seriesData = datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                value: nameSeries[1].map(function (item) { return item.y }),
                itemStyle: {
                    color: that._catPalette[index % that._catPaletteSize]
                },
                areaStyle: {opacity: 0.2}
            }
        })

        return {
            title: Object.assign({}, common.title, {text: title}),
            color: common.color,
            textStyle: common.textStyle,
            animation: common.animation,
            tooltip: {
                trigger: 'item'
            },
            legend: {
                show: seriesSize > 1,
                bottom: 0
            },
            radar: {
                indicator: indicator
            },
            series: [{
                type: 'radar',
                data: seriesData
            }]
        }
    }

    ////////////////////////////////
    // Filter Integration Helpers //
    ////////////////////////////////

    _addPointClicked(elementId, filterElement, fieldName, datas) {
        var chart = this._charts[elementId]
        if (!chart) return

        // Build a lookup from dataPointIndex to key
        var keysBySeriesIndex = datas.map(function (nameSeries) {
            return nameSeries[1].map(function (item) { return item.key })
        })

        chart.on('click', function (params) {
            var si = params.seriesIndex != null ? params.seriesIndex : 0
            var di = params.dataIndex

            // For pie charts the data has _key property
            var key = null
            if (params.data && params.data._key != null) {
                key = params.data._key
            } else if (keysBySeriesIndex[si] && keysBySeriesIndex[si][di] != null) {
                key = keysBySeriesIndex[si][di]
            }

            if (key != null) {
                var condition = {fieldName: fieldName, conditionType: "=", value: key}
                _callMultiFilter(filterElement, 'replaceWithConditionAndSubmit', condition)
            }
        })
    }

    _addXAxisZoomed(elementId, filterElement, fieldName, isDouble, isDate) {
        var chart = this._charts[elementId]
        if (!chart) return

        chart.on('datazoom', this._debounce(function () {
            var xAxisModel = chart.getModel().getComponent('xAxis', 0)
            if (!xAxisModel) return

            var xExtent = xAxisModel.axis.scale.getExtent()

            var xMinOut = asTypedStringValue(xExtent[0], isDouble, isDate, true)
            var xMaxOut = asTypedStringValue(xExtent[1], isDouble, isDate, false)

            var conditions = [
                {fieldName: fieldName, conditionType: ">=", value: xMinOut},
                {fieldName: fieldName, conditionType: "<=", value: xMaxOut}
            ]

            _callMultiFilter(filterElement, 'addConditionsAndSubmit', conditions)
        }, 300))
    }

    /////////////////////
    // Utility Helpers //
    /////////////////////

    _debounce(fn, delay) {
        var timer = null
        return function () {
            var context = this
            var args = arguments
            if (timer) clearTimeout(timer)
            timer = setTimeout(function () {
                fn.apply(context, args)
            }, delay)
        }
    }

    /////////////////////////
    // Lifecycle & Export   //
    /////////////////////////

    refresh() {
        var that = this
        Object.keys(this._charts).forEach(function (key) {
            var chart = that._charts[key]
            if (chart && !chart.isDisposed()) {
                try {
                    chart.resize()
                } catch (e) {
                    // chart may have been removed from DOM
                }
            }
        })
    }

    export(charts, format, filename) {
        var that = this

        function exportFun(chartId, svgCallback) {
            var chart = that._charts[chartId]
            if (chart && !chart.isDisposed()) {
                var svgStr = chart.renderToSVGString()
                svgCallback(svgStr)
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
