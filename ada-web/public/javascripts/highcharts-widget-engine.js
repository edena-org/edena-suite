class HighchartsWidgetEngine extends WidgetEngine {

    constructor() {
        super();

        // Set global default options for all charts
        Highcharts.setOptions({
            exporting: {
                fallbackToExportServer: false // Ensure the export happens on the client side or not at all
            }
        });
    };

    // impl
    _categoricalCountWidget(elementId, widget, filterElement) {
        var categories = (widget.data.length > 0) ?
            widget.data[0][1].map(function (count) {
                return count.value
            })
            : []

        const that = this

        const datas = widget.data.map(function (nameSeries) {
            const name = nameSeries[0]
            const series = nameSeries[1]

            const sum = that._agg(series, widget)
            const data = series.map(function (item) {
                const label = shorten(item.value)
                const count = item.count
                const key = item.key

                const value = (widget.useRelativeValues) ? 100 * count / sum : count
                return {name: label, y: value, key: key}
            })

            return {name: name, data: data}
        })

        const totalCounts = widget.data.map(function (nameSeries) {
            return that._agg(nameSeries[1], widget);
        })

        const seriesSize = datas.length
        const height = widget.displayOptions.height || 400

        const pointFormat = function () {
            return (widget.useRelativeValues) ? that._categoricalPercentPointFormat(this) : that._categoricalCountPointFormat(totalCounts, this);
        }
        const yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            that._categoricalWidgetAux({
                chartType,
                categories,
                datas,
                seriesSize,
                title: widget.title,
                yAxisCaption,
                chartElementId: elementId,
                showLabels: widget.showLabels,
                showLegend: widget.showLegend,
                height,
                pointFormat
            });
        }


        $('#' + elementId).on('chartTypeChanged', function (event, chartType) {
            plot(chartType);
        })

        plot(widget.displayOptions.chartType)

        if (filterElement) {
            $('#' + elementId).on('pointSelected', function (event, data) {
                var condition = {fieldName: widget.fieldName, conditionType: "=", value: data.key};

                $(filterElement).multiFilter('replaceWithConditionAndSubmit', condition);
            });
        }
    }

    _categoricalWidgetAux({
        chartType,
        categories,
        datas,
        seriesSize,
        title,
        yAxisCaption,
        chartElementId,
        showLabels,
        showLegend,
        height,
        pointFormat
    }) {
        var showLegendExp = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map(function (data, index) {
                    const size = (100 / seriesSize) * (index + 1)
                    var innerSize = 0
                    if (index > 0)
                        innerSize = (100 / seriesSize) * index + 1
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                this._pieChart({
                    title,
                    chartElementId,
                    series,
                    showLabels,
                    showLegend,
                    pointFormat,
                    height,
                    allowSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Column':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    inverted: false,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLabels: true,
                    showLegend: showLegendExp,
                    pointFormat,
                    height,
                    dataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Bar':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    inverted: true,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLabels: true,
                    showLegend: showLegendExp,
                    pointFormat,
                    height,
                    dataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Line':
                var series = datas

                this._lineChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLegend: showLegendExp,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: null,
                    yDataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Spline':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'spline'};
                });

                this._lineChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLegend: showLegendExp,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: null,
                    yDataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Polar':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                this._polarChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    showLegend: showLegendExp,
                    pointFormat,
                    height,
                    dataType: null,
                    allowSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
        }
    }

    // impl
    _numericalCountWidget(elementId, widget, filterElement) {
        var isDate = widget.fieldType == "Date"
        var isDouble = widget.fieldType == "Double"
        var dataType = (isDate) ? 'datetime' : null;

        const that = this

        var datas = widget.data.map(function (nameSeries) {
            var name = nameSeries[0]
            var series = nameSeries[1]

            var sum = that._agg(series, widget)
            var data = series.map(function (item) {
                var count = item.count

                var y = (widget.useRelativeValues) ? 100 * count / sum : count
                return [item.value, y]
            })

            return {name: name, data: data}
        })

        var totalCounts = widget.data.map(function (nameSeries) {
            return that._agg(nameSeries[1], widget);
        })

        var seriesSize = datas.length
        var height = widget.displayOptions.height || 400

        var pointFormat = function () {
            return (widget.useRelativeValues) ? that._numericalPercentPointFormat(isDate, isDouble, this) : that._numericalCountPointFormat(isDate, isDouble, totalCounts, this);
        }
        var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            that._numericalWidgetAux({
                chartType,
                datas,
                seriesSize,
                title: widget.title,
                xAxisCaption: widget.fieldLabel,
                yAxisCaption,
                chartElementId: elementId,
                height,
                pointFormat,
                dataType
            })
        }

        $('#' + elementId).on('chartTypeChanged', function (event, chartType) {
            plot(chartType)
        });

        plot(widget.displayOptions.chartType)

        if (filterElement) {
            this._addIntervalSelected($('#' + elementId), filterElement, widget.fieldName, isDouble, isDate)
        }
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
        pointFormat,
        dataType
    }) {
        var showLegend = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map(function (data, index) {
                    const size = (100 / seriesSize) * index
                    const innerSize = Math.max(0, (100 / seriesSize) * (index - 1) + 1)
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                this._pieChart({
                    title,
                    chartElementId,
                    series,
                    showLabels: false,
                    showLegend,
                    pointFormat,
                    height,
                    allowSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Column':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    inverted: false,
                    xAxisCaption,
                    yAxisCaption,
                    showLabels: false,
                    showLegend,
                    pointFormat,
                    height,
                    dataType,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Bar':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    inverted: true,
                    xAxisCaption,
                    yAxisCaption,
                    showLabels: false,
                    showLegend,
                    pointFormat,
                    height,
                    dataType,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Line':
                var series = datas

                this._lineChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    xAxisCaption,
                    yAxisCaption,
                    showLegend,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: dataType,
                    yDataType: null,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Spline':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'spline'};
                });

                this._lineChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    xAxisCaption,
                    yAxisCaption,
                    showLegend,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: dataType,
                    yDataType: null,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Polar':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                this._polarChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    showLegend,
                    pointFormat,
                    height,
                    dataType,
                    allowSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
        }
    }

    // impl
    _lineWidget(elementId, widget, filterElement) {
        const isDate = widget.xFieldType == "Date"
        const isDouble = widget.xFieldType == "Double"
        const xDataType = (isDate) ? 'datetime' : null;

        const isYDate = widget.yFieldType == "Date"
        const yDataType = (isYDate) ? 'datetime' : null;

        const datas = widget.data.map(function (nameSeries) {
            return {name: nameSeries[0], data: nameSeries[1]}
        })

        const showLegend = datas.length > 1

        const height = widget.displayOptions.height || 400

        const that = this
        const pointFormat = function () {
            return that._numericalPointFormat(isDate, isDouble, this, 3, 3);
        }

        // $('#' + elementId).on('chartTypeChanged', function(event, chartType) {
        //     plotNumericalChart(chartType, datas, seriesSize, widget.title, widget.xAxisCaption, yAxisCaption, elementId, height, pointFormat, dataType)
        // });

        this._lineChart({
            title: widget.title,
            chartElementId: elementId,
            categories: null,
            series: datas,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            showLegend,
            enableDataLabels: true,
            pointFormat,
            height,
            xDataType,
            yDataType,
            allowPointSelectionEvent: false,
            allowIntervalSelectionEvent: true,
            allowChartTypeChange: false,
            xMin: widget.xMin,
            xMax: widget.xMax,
            yMin: widget.yMin,
            yMax: widget.yMax
        });

        if (filterElement) {
            this._addIntervalSelected($('#' + elementId), filterElement, widget.xFieldName, isDouble, isDate)
        }
    }

    _addIntervalSelected(chartElement, filterElement, xFieldName, isDouble, isDate) {
        function toTypedStringValue(value, ceiling) {
            var intValue = (ceiling) ? Math.ceil(value) : Math.floor(value)

            return (isDate) ? msOrDateToStandardDateString(intValue) : (isDouble) ? value.toString() : intValue.toString()
        }

        chartElement.on('intervalSelected', function (event, data) {
            var xMin = toTypedStringValue(data.xMin, true);
            var xMax = toTypedStringValue(data.xMax, false);

            var conditions = [
                {fieldName: xFieldName, conditionType: ">=", value: xMin},
                {fieldName: xFieldName, conditionType: "<=", value: xMax}
            ]

            $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
        });
    }

    // impl
    _boxWidget(elementId, widget) {
        var isDate = widget.fieldType == "Date"
        var dataType = (isDate) ? 'datetime' : null;

        var datas = widget.data.map(function (namedQuartiles) {
            var quartiles = namedQuartiles[1]
            return [quartiles.lowerWhisker, quartiles.lowerQuantile, quartiles.median, quartiles.upperQuantile, quartiles.upperWhisker]
        })

        var categories = widget.data.map(function (namedQuartiles) {
            return namedQuartiles[0]
        })

        var min = widget.min
        var max = widget.max

        var pointFormat = (isDate) ?
            '- Upper 1.5 IQR: {point.high:%Y-%m-%d}<br/>' +
            '- Q3: {point.q3:%Y-%m-%d}<br/>' +
            '- Median: {point.median:%Y-%m-%d}<br/>' +
            '- Q1: {point.q1:%Y-%m-%d}<br/>' +
            '- Lower 1.5 IQR: {point.low:%Y-%m-%d}<br/>'
            :
            '- Upper 1.5 IQR: {point.high}<br/>' +
            '- Q3: {point.q3}<br/>' +
            '- Median: {point.median}<br/>' +
            '- Q1: {point.q1}<br/>' +
            '- Lower 1.5 IQR: {point.low}<br/>'

        var height = widget.displayOptions.height || 400

        this._boxPlot({
            title: widget.title,
            chartElementId: elementId,
            categories,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            data: datas,
            min,
            max,
            pointFormat,
            height,
            dataType
        })
    }

    // impl
    _scatterWidget(elementId, widget, filterElement) {
        const datas = widget.data.map(function (series) {
            return {name: shorten(series[0]), data: series[1]}
        })

        const height = widget.displayOptions.height || 400;

        const isXDate = widget.xFieldType == "Date"
        const xDataType = (isXDate) ? 'datetime' : null;

        const isYDate = widget.yFieldType == "Date"
        const yDataType = (isYDate) ? 'datetime' : null;

        if (filterElement) {
            this._addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate);
        }

        this._scatterChart({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            series: datas,
            showLegend: true,
            pointFormat: null,
            height,
            xDataType,
            yDataType,
            zoomIfDragged: true,
            allowSelectionEvent: filterElement != null
        })
    }

    _addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate) {
        $('#' + elementId).on('areaSelected', function (event, data) {
            const xMin = (isXDate) ? msOrDateToStandardDateString(data.xMin) : data.xMin.toString();
            const xMax = (isXDate) ? msOrDateToStandardDateString(data.xMax) : data.xMax.toString();
            const yMin = (isYDate) ? msOrDateToStandardDateString(data.yMin) : data.yMin.toString();
            const yMax = (isYDate) ? msOrDateToStandardDateString(data.yMax) : data.yMax.toString();

            const conditions = [
                {fieldName: widget.xFieldName, conditionType: ">=", value: xMin},
                {fieldName: widget.xFieldName, conditionType: "<=", value: xMax},
                {fieldName: widget.yFieldName, conditionType: ">=", value: yMin},
                {fieldName: widget.yFieldName, conditionType: "<=", value: yMax}
            ]

            $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
        });
    }

    // impl
    _valueScatterWidget(elementId, widget, filterElement) {
        var zs = widget.data.map(function (point) {
            return point[2];
        })

        var zMin = Math.min.apply(null, zs);
        var zMax = Math.max.apply(null, zs);

        var data = widget.data.map(function (point) {
            var zColor = (1 - Math.abs((point[2] - zMin) / zMax)) * 210;
            return {x: point[0], y: point[1], z: point[2], color: 'rgba(255, ' + zColor + ',' + zColor + ', 0.8)'};
        })

        var datas = [{data: data}];

        var height = widget.displayOptions.height || 400;

        var isXDate = widget.xFieldType == "Date";
        var xDataType = (isXDate) ? 'datetime' : null;

        var isYDate = widget.yFieldType == "Date";
        var yDataType = (isYDate) ? 'datetime' : null;

        if (filterElement) {
            this._addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate);
        }

        var pointFormatter = function () {
            var xPoint = (xDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.x) : this.point.x;
            var yPoint = (yDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.y) : this.point.y;
            var zPoint = this.point.z;

            return xPoint + ", " + yPoint + " (" + zPoint + ")";
        }

        this._scatterChart({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            series: datas,
            showLegend: false,
            pointFormat: pointFormatter,
            height,
            xDataType,
            yDataType,
            zoomIfDragged: true,
            allowSelectionEvent: filterElement != null
        })
    }

    // impl
    _heatmapWidget(elementId, widget, filterElement) { // TODO: filter not used but could be
        const xCategories = widget.xCategories
        const yCategories = widget.yCategories
        const data = widget.data.map(function (seq, i) {
            return seq.map(function (value, j) {
                return [i, j, value]
            })
        })

        const height = widget.displayOptions.height || 400

        this._heatmapChart({
            title: widget.title,
            chartElementId: elementId,
            xCategories,
            yCategories,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            data: [].concat.apply([], data),
            min: widget.min,
            max: widget.max,
            twoColors: widget.twoColors,
            height
        })
    };

    _pieChart({
        title,
        chartElementId,
        series,
        showLabels,
        showLegend,
        pointFormat,
        height,
        allowSelectionEvent,
        allowChartTypeChange
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)
        this._adjustExporting(chartElementId, exporting)

        var cursor = '';
        if (allowSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        $('#' + chartElementId).highcharts({
            chart: {
                plotBackgroundColor: null,
                plotBorderWidth: null,
                plotShadow: false,
                type: 'pie',
                height: height
            },
            title: {
                text: title
            },
            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            plotOptions: {
                pie: {
                    allowPointSelect: allowSelectionEvent,
                    cursor: cursor,
                    dataLabels: {
                        enabled: showLabels,
                        format: '<b>{point.name}</b>: {point.percentage:.1f}%',
                        style: {
                            color: (Highcharts.theme && Highcharts.theme.contrastTextColor) || 'black'
                        }
                    },
                    showInLegend: showLegend,
                    point: {
                        events: {
                            click: function () {
                                if (allowSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    }
                },
                series: {
                    animation: {
                        duration: 400
                    }
                }
            },
            legend: {
                maxHeight: 70
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            series: series
        });
    }

    _columnChart({
        title,
        chartElementId,
        categories,
        series,
        inverted,
        xAxisCaption,
        yAxisCaption,
        showLabels,
        showLegend,
        pointFormat,
        height,
        dataType,
        allowPointSelectionEvent,
        allowIntervalSelectionEvent,
        allowChartTypeChange
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)
        this._adjustExporting(chartElementId, exporting)

        var cursor = '';
        if (allowPointSelectionEvent || allowIntervalSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        function selectXAxisPointsByDrag(event) {
            if (event.xAxis) {
                var xMin = event.xAxis[0].min;
                var xMax = event.xAxis[0].max;

                $('#' + chartElementId).trigger("intervalSelected", {xMin: xMin, xMax: xMax});
            }
        }

        var selectHandler = (allowIntervalSelectionEvent) ? selectXAxisPointsByDrag : null

        var chartType = 'column'
        if (inverted)
            chartType = 'bar'
        $('#' + chartElementId).highcharts({
            chart: {
                type: chartType,
                height: height,
                events: {
                    selection: selectHandler
                },
                zoomType: 'x'
            },
            title: {
                text: title
            },
            xAxis: {
//                type: 'category',
                type: dataType,
                title: {
                    text: xAxisCaption
                },
                categories: categories,
                crosshair: true
            },
            yAxis: {
                title: {
                    text: yAxisCaption
                }
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '100px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            plotOptions: {
                series: {
                    borderWidth: 0,
                    pointPadding: 0.07,
                    groupPadding: 0.05,
                    dataLabels: {
                        enabled: showLabels,
                        formatter: function () {
                            var value = this.point.y
                            return (value === parseInt(value, 10)) ? value : Highcharts.numberFormat(value, 1)
                        }
                    },
                    allowPointSelect: allowPointSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },

            series: series
        })
    }

    _lineChart({
        title,
        chartElementId,
        categories,
        series,
        xAxisCaption,
        yAxisCaption,
        showLegend,
        enableDataLabels,
        pointFormat,
        height,
        xDataType,
        yDataType,
        allowPointSelectionEvent,
        allowIntervalSelectionEvent,
        allowChartTypeChange,
        xMin,
        xMax,
        yMin,
        yMax
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)
        this._adjustExporting(chartElementId, exporting)

        var cursor = '';
        if (allowPointSelectionEvent || allowIntervalSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        function selectXAxisPointsByDrag(event) {
            if (event.xAxis) {
                var xMin = event.xAxis[0].min;
                var xMax = event.xAxis[0].max;

                $('#' + chartElementId).trigger("intervalSelected", {xMin: xMin, xMax: xMax});
            }
        }

        var selectHandler = (allowIntervalSelectionEvent) ? selectXAxisPointsByDrag : null
        var zoomType = (allowIntervalSelectionEvent) ? 'x' : null

        $('#' + chartElementId).highcharts({
            chart: {
                height: height,
                events: {
                    selection: selectHandler
                },
                zoomType: zoomType
            },
            title: {
                text: title
            },
            xAxis: {
                type: xDataType,
                title: {
                    text: xAxisCaption
                },
                min: xMin,
                max: xMax,
                categories: categories
            },
            yAxis: {
                type: yDataType,
                title: {
                    text: yAxisCaption
                },
                min: yMin,
                max: yMax
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '80px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            plotOptions: {
                series: {
                    marker: {
                        enabled: enableDataLabels,
                        radius: 4,
                        states: {
                            hover: {
                                enabled: true
                            }
                        }
                    },
                    turboThreshold: 5000,
                    allowPointSelect: allowPointSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },

            series: series
        })
    }

    _polarChart({
        title,
        chartElementId,
        categories,
        series,
        showLegend,
        pointFormat,
        height,
        dataType,
        allowSelectionEvent,
        allowChartTypeChange
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)
        this._adjustExporting(chartElementId, exporting)

        var cursor = '';
        if (allowSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        $('#' + chartElementId).highcharts({
            chart: {
                polar: true,
                height: height
            },

            title: {
                text: title
            },
            pane: {
                center: ['50%', '52%'],
                size: '90%',
                startAngle: 0,
                endAngle: 360
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '80px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            xAxis: {
                type: dataType,
                categories: categories,
                tickmarkPlacement: 'on',
                lineWidth: 0
            },

            yAxis: {
                gridLineInterpolation: 'polygon',
                labels: {
                    enabled: true
                }
            },
            plotOptions: {
                series: {
                    dataLabels: {
                        enabled: false
                    },
                    allowPointSelect: allowSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },
            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            exporting: exporting,
            credits: {
                enabled: false
            },
            series: series
        });
    }

    _scatterChart({
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        series,
        showLegend,
        pointFormat,
        height,
        xDataType,
        yDataType,
        zoomIfDragged,
        allowSelectionEvent
    }) {
        var pointFormatter = null
        if (!pointFormat) {
            pointFormatter = this._defaultScatterPointFormatter(xDataType, yDataType)
        } else if (typeof pointFormat === "function") {
            pointFormatter = pointFormat
            pointFormat = null
        }

        const that = this

        /**
         * Custom selection handler that selects points and cancels the default zoom behaviour
         */
        function selectPointsByDrag(e) {

            // Select points
            Highcharts.each(this.series, function (series) {
                Highcharts.each(series.points, function (point) {
                    if (point.x >= e.xAxis[0].min && point.x <= e.xAxis[0].max &&
                        point.y >= e.yAxis[0].min && point.y <= e.yAxis[0].max) {
                        point.select(true, true);
                    }
                });
            });

            that._toast(this, '<b>' + this.getSelectedPoints().length + ' points selected.</b>' +
                '<br>Click on empty space to deselect.');

            if (allowSelectionEvent) triggerSelectionEvent(this.series, e)

            return false; // Don't zoom
        }

        function zoomByDrag(e) {
            if (e.xAxis && e.yAxis) {
                // count the selected points
                var count = 0

                Highcharts.each(this.series, function (series) {
                    Highcharts.each(series.points, function (point) {
                        if (point.x >= e.xAxis[0].min && point.x <= e.xAxis[0].max &&
                            point.y >= e.yAxis[0].min && point.y <= e.yAxis[0].max) {
                            count++;
                        }
                    });
                });

//                toast(this, '<b>' + count + ' points selected.</b>');

                if (allowSelectionEvent) triggerSelectionEvent(this.series, e)
            }

            return true;
        }

        function triggerSelectionEvent(series, event) {
            var cornerPoints = that._findCornerPoints(series, event)
            if (cornerPoints)
                $('#' + chartElementId).trigger("areaSelected", cornerPoints);
        }

        /**
         * On click, unselect all points
         */
        function unselectByClick() {
            var points = this.getSelectedPoints();
            if (points.length > 0) {
                Highcharts.each(points, function (point) {
                    point.select(false);
                });
            }
        }

        var exporting = {};
        this._adjustExporting(chartElementId, exporting)

        $('#' + chartElementId).highcharts({
            chart: {
                type: 'scatter',
                zoomType: 'xy',
                events: {
                    selection: (zoomIfDragged) ? zoomByDrag : selectPointsByDrag,
                    click: unselectByClick
                },
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                title: {
                    enabled: true,
                    text: xAxisCaption
                },
                type: xDataType,
                startOnTick: true,
                endOnTick: true,
                showLastLabel: true
            },
            yAxis: {
                title: {
                    text: yAxisCaption
                },
                type: yDataType
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '100px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            exporting: exporting,
            credits: {
                enabled: false
            },
            plotOptions: {
                scatter: {
                    marker: {
                        radius: 4,
                        states: {
                            hover: {
                                enabled: true,
                                lineColor: 'rgb(100,100,100)'
                            }
                        }
                    },
                    states: {
                        hover: {
                            marker: {
                                enabled: false
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    },
                    turboThreshold: 5000
                }
            },
            tooltip: {
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            series: series
        });
    }

    _defaultScatterPointFormatter(xDataType, yDataType) {
        Highcharts.setOptions({global: {useUTC: false}});

        var formatter = function () {
            var xPoint = (xDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.x) : this.point.x
            var yPoint = (yDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.y) : this.point.y

            return '<b>' + this.series.name + '</b><br>' + xPoint + ", " + yPoint
        }
        return formatter;
    }

    _heatmapChart({
        title,
        chartElementId,
        xCategories,
        yCategories,
        xAxisCaption,
        yAxisCaption,
        data,
        min,
        max,
        twoColors,
        height
    }) {
        var colors = (twoColors) ?
            [
                [0, Highcharts.getOptions().colors[5]],
                [0.5, '#FFFFFF'],
                [1, Highcharts.getOptions().colors[0]]
            ] : [
                [0, '#FFFFFF'],
                [1, Highcharts.getOptions().colors[0]]
            ]

        var exporting = {};
        this._adjustExporting(chartElementId, exporting)

        $('#' + chartElementId).highcharts({
            chart: {
                type: 'heatmap',
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                categories: xCategories,
                title: {
                    enabled: (xAxisCaption != null),
                    text: xAxisCaption
                },
                labels: {
                    formatter: function () {
                        return shorten(this.value, 10);
                    }
                }
            },
            yAxis: {
                categories: yCategories,
                title: {
                    enabled: (yAxisCaption != null),
                    text: yAxisCaption
                },
                labels: {
                    formatter: function () {
                        return shorten(this.value, 10);
                    }
                }
            },
            exporting: exporting,
            credits: {
                enabled: false
            },
            colorAxis: {
                stops: colors,
                min: min,
                max: max
//                    minColor: '#FFFFFF',
//                    maxColor: Highcharts.getOptions().colors[0]
            },
            legend: {
                align: 'right',
                layout: 'vertical',
                margin: 0,
                verticalAlign: 'top',
                y: 25,
                symbolHeight: 280
            },
            plotOptions: {
                series: {
                    boostThreshold: 100
                }
            },
            boost: {
                useGPUTranslations: true
            },
            tooltip: {
                formatter: function () {
                    var value = (this.point.value != null) ? Highcharts.numberFormat(this.point.value, 3, '.') : "Undefined"
                    return '<b>' + this.series.xAxis.categories[this.point.x] + '</b><br><b>' +
                        this.series.yAxis.categories[this.point.y] + '</b><br>' + value;
                },
                valueDecimals: 2
            },
            series: [{
                name: title,
                borderWidth: 1,
                data: data,
                dataLabels: {
                    enabled: false,
                    color: '#000000'
                }
            }]
        });
    }

    _boxPlot({
        title,
        chartElementId,
        categories,
        xAxisCaption,
        yAxisCaption,
        data,
        min,
        max,
        pointFormat,
        height,
        dataType
    }) {
        var exporting = {};
        this._adjustExporting(chartElementId, exporting)

        $('#' + chartElementId).highcharts({
            chart: {
                type: 'boxplot',
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                categories: categories,
                title: {
                    text: xAxisCaption
                }
            },
            yAxis: {
                type: dataType,
                title: {
                    text: yAxisCaption
                },
                min: min,
                max: max
            },
            legend: {
                enabled: false
            },
            exporting: exporting,
            credits: {
                enabled: false
            },
            plotOptions: {
                boxplot: {
                    fillColor: '#eeeeee',
                    lineWidth: 2,
                    medianWidth: 3,
                    animation: {
                        duration: 400
                    }
                }
            },
            series: [{
                name: title,
                data: data,
                tooltip: {
                    headerFormat: '<b>{point.series.name}</b><br/>',
                    pointFormat: pointFormat
                }
            }]
        });
    }

    _chartTypeMenu(chartElementId, chartTypes) {
        Highcharts.setOptions({
            lang: {
                chartTypeButtonTitle: "Chart Type"
            }
        });

        return {
            buttons: {
                chartTypeButton: {
                    symbol: 'circle',
                    x: '0%',
                    _titleKey: "chartTypeButtonTitle",
                    menuItems:
                        chartTypes.map(function (name) {
                            return {
                                text: name,
                                onclick: function () {
                                    $('#' + chartElementId).trigger("chartTypeChanged", name);
                                }
                            }
                        })
                }
            }
        };
    }

    /**
     * Display a temporary label on the chart
     */
    _toast(chart, text) {
        if (chart.toast)
            chart.toast = chart.toast.destroy();

        chart.toast = chart.renderer.label(text, 100, 120)
            .attr({
                fill: Highcharts.getOptions().colors[0],
                padding: 10,
                r: 5,
                zIndex: 8
            })
            .css({
                color: '#FFFFFF'
            })
            .add();

        setTimeout(function () {
            if (chart.toast)
                chart.toast.fadeOut();
        }, 2000);

        setTimeout(function () {
            if (chart.toast) {
                chart.toast = chart.toast.destroy();
            }
        }, 2500);
    }

    _findCornerPoints(series, event) {
        var xMin, xMax, yMin, yMax;
        Highcharts.each(series, function (series) {
            Highcharts.each(series.points, function (point) {
                if (point.x >= event.xAxis[0].min && point.x <= event.xAxis[0].max &&
                    point.y >= event.yAxis[0].min && point.y <= event.yAxis[0].max) {

                    if (!xMin || point.x < xMin)
                        xMin = point.x

                    if (!xMax || point.x > xMax)
                        xMax = point.x

                    if (!yMin || point.y < yMin)
                        yMin = point.y

                    if (!yMax || point.y > yMax)
                        yMax = point.y
                }
            });
        });
        return (xMin) ? {xMin: xMin, xMax: xMax, yMin: yMin, yMax: yMax} : null
    }

    _adjustExporting(chartElementId, exporting) {
        const width = $('#' + chartElementId).width()
        exporting.sourceWidth = width
        exporting.scale = 2
    }

    refresh() {
        console.log("Highcharts refresh called")
        Highcharts.charts.forEach(function (chart) {
            if (chart) chart.reflow();
        });
    }

    export(charts, format, filename) {
        var options = {
            type: format,
            filename: filename
        }

        options = Highcharts.merge(Highcharts.getOptions().exporting, options);

        function exportFun(chartId, svgCallback) {
            const chart = $("#" + chartId).highcharts()
            if (chart) {
                chart.getSVGForLocalExport(options, {}, function () {
                    console.log("Failed to get SVG");
                    svgCallback(null);
                }, function (svg) {
                    svgCallback(svg);
                });
            } else {
                svgCallback(null)
            }
        }

        // Get SVGs asynchronously and then download the resulting SVG
        this._combineSVGs(charts, exportFun, function (svg) {
            Highcharts.downloadSVGLocal(
                svg, options,
                    // (options.filename || 'chart')  + '.' + (imageType === 'image/svg+xml' ? 'svg' : imageType.split('/')[1]),
                    // imageType,
                    // options.scale || 2,
                function () {
                    console.log("Failed to export on client side");
                }
            );
        });
    }

    ////////////////
    // Formatters //
    ////////////////

    _numericalPointFormat(isDate, isDouble, that, xFloatingPoints, yFloatingPoints) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatNumericalValue(isDate, isDouble, that, xFloatingPoints) +
            this._getPointFormatY(that, yFloatingPoints)
    }

    _numericalCountPointFormat(isDate, isDouble, totalCounts, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatNumericalValue(isDate, isDouble, that, 2) +
            this._getPointFormatY(that) +
            this._getPointFormatPercent(that, totalCounts);
    }

    _categoricalCountPointFormat(totalCounts, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatCategoricalValue(that) +
            this._getPointFormatY(that) +
            this._getPointFormatPercent(that, totalCounts)
    }

    _numericalPercentPointFormat(isDate, isDouble, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatNumericalValue(isDate, isDouble, that, 2) +
            this._getPointFormatPercent2(that);
    }

    _categoricalPercentPointFormat(that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatCategoricalValue(that) +
            this._getPointFormatPercent2(that)
    }

    _getPointFormatHeader(that) {
        var seriesCount = that.series.chart.series.length
        return (seriesCount > 1) ? '<span style="font-size:11px">' + that.series.name + '</span><br>' : ''
    }

    _getPointFormatPercent(that, totalCounts) {
        return ' (<b>' + Highcharts.numberFormat(100 * that.y / totalCounts[that.series.index], 1) + '%</b>)'
    }

    _getPointFormatPercent2(that) {
        return ': <b>' + Highcharts.numberFormat(that.y, 1) + '%</b>'
    }

    _getPointFormatY(that, yFloatingPoints) {
        var yValue = (yFloatingPoints) ? Highcharts.numberFormat(that.y, yFloatingPoints) : that.y
        return ': <b>' + yValue + '</b>'
    }

    _getPointFormatNumericalValue(isDate, isDouble, that, xFloatingPoints) {
        var colorStartPart = '<span style="color:' + that.point.color + '">'

        Highcharts.setOptions({global: {useUTC: false}});

        var valuePart =
            (isDate) ?
                Highcharts.dateFormat('%Y-%m-%d', that.point.x)
                :
                (isDouble) ?
                    ((xFloatingPoints) ? Highcharts.numberFormat(that.point.x, xFloatingPoints) : that.point.x)
                    :
                    that.point.x;

        return colorStartPart + valuePart + '</span>'
    }

    _getPointFormatCategoricalValue(that) {
        return '<span style="color:' + that.point.color + '">' + that.point.name + '</span>'
    }
}