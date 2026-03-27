/**
 * Ada Widget & Chart Type Definitions
 *
 * TypeScript interfaces describing the JSON structures of all widgets
 * produced by the Ada server and consumed by the widget engines
 * (Plotly, Highcharts, ECharts, ApexCharts).
 *
 * Source Scala classes:
 *   - Widgets:        ada-web/.../models/Widget.scala
 *   - Widget Specs:   ada-server/.../models/WidgetSpec.scala
 *   - Display Opts:   ada-server/.../models/WidgetSpec.scala
 *   - Serialization:  ada-web/.../models/WidgetWrites.scala
 *   - Stats/Quartiles: core/.../calc/impl/
 */

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------

/** org.edena.core.field.FieldTypeId */
export type FieldTypeId =
  | "Null"
  | "Boolean"
  | "Double"
  | "Integer"
  | "Enum"
  | "String"
  | "Date"
  | "Json";

/** org.edena.ada.server.models.ChartType */
export type ChartType = "Pie" | "Column" | "Bar" | "Line" | "Spline" | "Polar";

/** org.edena.ada.server.models.AggType */
export type AggType = "Mean" | "Max" | "Min" | "Variance";

/** org.edena.ada.server.models.CorrelationType */
export type CorrelationType = "Pearson" | "Matthews";

// ---------------------------------------------------------------------------
// Display Options
// ---------------------------------------------------------------------------

/** org.edena.ada.server.models.DisplayOptions (sealed trait) */
export interface DisplayOptions {
  gridWidth?: number | null;    // Bootstrap col-md-N
  gridOffset?: number | null;
  height?: number | null;       // pixels
  isTextualForm: boolean;       // true = render as table, false = render as chart
  title?: string | null;        // optional title override
}

/** org.edena.ada.server.models.BasicDisplayOptions */
export interface BasicDisplayOptions extends DisplayOptions {
  concreteClass: "org.edena.ada.server.models.BasicDisplayOptions";
}

/** org.edena.ada.server.models.MultiChartDisplayOptions */
export interface MultiChartDisplayOptions extends DisplayOptions {
  concreteClass: "org.edena.ada.server.models.MultiChartDisplayOptions";
  chartType?: ChartType | null;
}

// ---------------------------------------------------------------------------
// Shared Data Types
// ---------------------------------------------------------------------------

/** org.edena.ada.web.models.Count[T] — serialized as { value, count, key? } */
export interface Count<T> {
  value: T;
  count: number;
  key?: string | null;
}

/**
 * org.edena.core.calc.impl.Quartiles[T]
 * Used in BoxWidget for box-and-whisker plots.
 */
export interface Quartiles<T> {
  lowerWhisker: T;
  lowerQuantile: T;
  median: T;
  upperQuantile: T;
  upperWhisker: T;
}

/** org.edena.core.calc.impl.BasicStatsResult */
export interface BasicStatsResult {
  min: number;
  max: number;
  mean: number;
  sum: number;
  sqSum: number;
  variance: number;
  standardDeviation: number;
  sampleVariance: number;
  sampleStandardDeviation: number;
  definedCount: number;
  undefinedCount: number;
}

/**
 * org.edena.core.calc.impl.IndependenceTestResult (sealed trait)
 * Subtypes are discriminated by `concreteClass`.
 */
export type IndependenceTestResult = ChiSquareResult | OneWayAnovaResult;

/** org.edena.core.calc.impl.ChiSquareResult */
export interface ChiSquareResult {
  concreteClass: "org.edena.core.calc.impl.ChiSquareResult";
  pValue: number;
  statistics: number;
  degreeOfFreedom: number;
}

/** org.edena.core.calc.impl.OneWayAnovaResult */
export interface OneWayAnovaResult {
  concreteClass: "org.edena.core.calc.impl.OneWayAnovaResult";
  pValue: number;
  FValue: number;
  dfbg: number; // degree of freedom between groups
  dfwg: number; // degree of freedom within groups
}

// ---------------------------------------------------------------------------
// Base Widget
// ---------------------------------------------------------------------------

/** BSON ObjectID as serialized by ReactiveMongo (e.g. { "$oid": "507f1f77bcf86cd799439011" }) */
export interface BSONObjectID {
  $oid: string;
}

/**
 * Every widget JSON object includes these fields.
 * `concreteClass` is the Scala fully-qualified class name used as a discriminator.
 * `_id` is a BSON ObjectID object (added by WidgetWrites).
 *
 * Usage in JS: widget._id.$oid to get the ID string.
 */
export interface WidgetBase {
  _id: BSONObjectID;
  title: string;
  concreteClass: string;
  displayOptions: BasicDisplayOptions | MultiChartDisplayOptions;
}

// ---------------------------------------------------------------------------
// Widget Types
// ---------------------------------------------------------------------------

/**
 * CategoricalCountWidget
 * Distribution of categorical (string) values.
 * Supports: Pie, Column, Bar charts or table view.
 *
 * `data` is an array of series, each series is a tuple [seriesName, counts[]]
 * serialized as a JSON array: ["seriesName", [{value, count, key?}, ...]]
 */
export interface CategoricalCountWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.CategoricalCountWidget";
  fieldName: string;
  fieldLabel: string;
  showLabels: boolean;
  showLegend: boolean;
  useRelativeValues: boolean;
  isCumulative: boolean;
  data: [string, Count<string>[]][];
  displayOptions: MultiChartDisplayOptions;
}

/**
 * NumericalCountWidget
 * Histogram / distribution of numerical values.
 * Supports: Column, Bar, Line charts or table view.
 *
 * `data` series tuples: ["seriesName", [{value, count, key?}, ...]]
 * `value` type depends on `fieldType` (number for Double/Integer, string for Date, etc.)
 */
export interface NumericalCountWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.NumericalCountWidget";
  fieldName: string;
  fieldLabel: string;
  useRelativeValues: boolean;
  isCumulative: boolean;
  fieldType: FieldTypeId;
  data: [string, Count<any>[]][];
  displayOptions: MultiChartDisplayOptions;
}

/**
 * CategoricalCheckboxCountWidget
 * Checkbox-style categorical display (checked/unchecked counts).
 *
 * `data` is an array of tuples: [isChecked, {value, count, key?}]
 */
export interface CategoricalCheckboxCountWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.CategoricalCheckboxCountWidget";
  fieldName: string;
  data: [boolean, Count<string>][];
  displayOptions: BasicDisplayOptions;
}

/**
 * ScatterWidget
 * 2D scatter plot with optional series grouping.
 *
 * `data` series tuples: ["seriesName", [[x, y], [x, y], ...]]
 * Point values depend on `xFieldType` and `yFieldType`.
 */
export interface ScatterWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.ScatterWidget";
  xFieldName: string;
  yFieldName: string;
  xAxisCaption: string;
  yAxisCaption: string;
  xFieldType: FieldTypeId;
  yFieldType: FieldTypeId;
  data: [string, [any, any][]][];
  displayOptions: BasicDisplayOptions;
}

/**
 * ValueScatterWidget
 * 3D scatter (bubble chart): x, y position + value (bubble size/color).
 *
 * `data` is a flat array of triples: [[x, y, value], ...]
 * (NOT grouped by series, unlike ScatterWidget)
 */
export interface ValueScatterWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.ValueScatterWidget";
  xFieldName: string;
  yFieldName: string;
  valueFieldName: string;
  xAxisCaption: string;
  yAxisCaption: string;
  xFieldType: FieldTypeId;
  yFieldType: FieldTypeId;
  valueFieldType: FieldTypeId;
  data: [any, any, any][];
  displayOptions: BasicDisplayOptions;
}

/**
 * LineWidget
 * Line / time-series chart with optional min/max bounds.
 *
 * `data` series tuples: ["seriesName", [[x, y], [x, y], ...]]
 */
export interface LineWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.LineWidget";
  xFieldName: string;
  xAxisCaption: string;
  yAxisCaption: string;
  xFieldType: FieldTypeId;
  yFieldType: FieldTypeId;
  data: [string, [any, any][]][];
  xMin?: any | null;
  xMax?: any | null;
  yMin?: any | null;
  yMax?: any | null;
  displayOptions: BasicDisplayOptions;
}

/**
 * BoxWidget
 * Box-and-whisker plot.
 *
 * `data` series tuples: ["groupName", {lowerWhisker, lowerQuantile, median, upperQuantile, upperWhisker}]
 */
export interface BoxWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.BoxWidget";
  xAxisCaption?: string | null;
  yAxisCaption: string;
  fieldType: FieldTypeId;
  data: [string, Quartiles<any>][];
  min?: any | null;
  max?: any | null;
  displayOptions: BasicDisplayOptions;
}

/**
 * HeatmapWidget
 * 2D heatmap / correlation matrix.
 *
 * `data` is a 2D matrix: data[row][col] = value or null.
 */
export interface HeatmapWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.HeatmapWidget";
  xCategories: string[];
  yCategories: string[];
  xAxisCaption?: string | null;
  yAxisCaption?: string | null;
  xFieldName?: string | null;
  yFieldName?: string | null;
  xFieldType?: FieldTypeId | null;
  yFieldType?: FieldTypeId | null;
  data: (number | null)[][];
  min?: number | null;
  max?: number | null;
  twoColors: boolean;
  displayOptions: BasicDisplayOptions;
}

/**
 * BasicStatsWidget
 * Summary statistics table (min, max, mean, std dev, etc.).
 * Typically rendered as a table (isTextualForm often true).
 */
export interface BasicStatsWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.BasicStatsWidget";
  fieldLabel: string;
  data: BasicStatsResult;
  displayOptions: BasicDisplayOptions;
}

/**
 * IndependenceTestWidget
 * Statistical independence test results (Chi-Square or One-Way ANOVA).
 *
 * `data` is an array of tuples: ["fieldName", testResult]
 */
export interface IndependenceTestWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.IndependenceTestWidget";
  data: [string, IndependenceTestResult][];
  displayOptions: BasicDisplayOptions;
}

/**
 * HtmlWidget
 * Custom HTML content rendered directly in the widget area.
 */
export interface HtmlWidget extends WidgetBase {
  concreteClass: "org.edena.ada.web.models.HtmlWidget";
  content: string;
  displayOptions: BasicDisplayOptions;
}

// ---------------------------------------------------------------------------
// Union type of all widgets
// ---------------------------------------------------------------------------

export type Widget =
  | CategoricalCountWidget
  | NumericalCountWidget
  | CategoricalCheckboxCountWidget
  | ScatterWidget
  | ValueScatterWidget
  | LineWidget
  | BoxWidget
  | HeatmapWidget
  | BasicStatsWidget
  | IndependenceTestWidget
  | HtmlWidget;

// ---------------------------------------------------------------------------
// Widget Spec Types (server-side configuration that produces widgets)
// ---------------------------------------------------------------------------

/** Base fields for all widget specs */
export interface WidgetSpecBase {
  concreteClass: string;
  fieldNames: string[];
  subFilterId?: string | null; // BSONObjectID reference
  displayOptions: BasicDisplayOptions | MultiChartDisplayOptions;
}

/** Distribution chart spec (categorical or numerical) */
export interface DistributionWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.DistributionWidgetSpec";
  fieldName: string;
  groupFieldName?: string | null;
  relativeValues: boolean;
  numericBinCount?: number | null;
  useDateMonthBins: boolean;
  displayOptions: MultiChartDisplayOptions;
}

/** Cumulative count chart spec */
export interface CumulativeCountWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.CumulativeCountWidgetSpec";
  fieldName: string;
  groupFieldName?: string | null;
  relativeValues: boolean;
  numericBinCount?: number | null;
  useDateMonthBins: boolean;
  displayOptions: MultiChartDisplayOptions;
}

/** Categorical checkbox spec */
export interface CategoricalCheckboxWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.CategoricalCheckboxWidgetSpec";
  fieldName: string;
  displayOptions: BasicDisplayOptions;
}

/** Box plot spec */
export interface BoxWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.BoxWidgetSpec";
  fieldName: string;
  groupFieldName?: string | null;
  displayOptions: BasicDisplayOptions;
}

/** Scatter plot spec */
export interface ScatterWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.ScatterWidgetSpec";
  xFieldName: string;
  yFieldName: string;
  groupFieldName?: string | null;
  displayOptions: BasicDisplayOptions;
}

/** Value (bubble) scatter spec */
export interface ValueScatterWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.ValueScatterWidgetSpec";
  xFieldName: string;
  yFieldName: string;
  valueFieldName: string;
  displayOptions: BasicDisplayOptions;
}

/** Heatmap with aggregation spec */
export interface HeatmapAggWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.HeatmapAggWidgetSpec";
  xFieldName: string;
  yFieldName: string;
  valueFieldName: string;
  xBinCount: number;
  yBinCount: number;
  aggType: AggType;
  displayOptions: BasicDisplayOptions;
}

/** Grid distribution count spec */
export interface GridDistributionCountWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.GridDistributionCountWidgetSpec";
  xFieldName: string;
  yFieldName: string;
  xBinCount: number;
  yBinCount: number;
  displayOptions: BasicDisplayOptions;
}

/** Correlation matrix spec (Pearson or Matthews) */
export interface CorrelationWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.CorrelationWidgetSpec";
  fieldNames: string[];
  correlationType: CorrelationType;
  displayOptions: BasicDisplayOptions;
}

/** Independence test spec */
export interface IndependenceTestWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.IndependenceTestWidgetSpec";
  fieldName: string;
  inputFieldNames: string[];
  topCount?: number | null;
  keepUndefined: boolean;
  displayOptions: BasicDisplayOptions;
}

/** Basic statistics spec */
export interface BasicStatsWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.BasicStatsWidgetSpec";
  fieldName: string;
  displayOptions: BasicDisplayOptions;
}

/** Line chart spec */
export interface XLineWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.XLineWidgetSpec";
  xFieldName: string;
  yFieldNames: string[];
  groupFieldName?: string | null;
  displayOptions: BasicDisplayOptions;
}

/** Custom HTML widget spec */
export interface CustomHtmlWidgetSpec extends WidgetSpecBase {
  concreteClass: "org.edena.ada.server.models.CustomHtmlWidgetSpec";
  content: string;
  displayOptions: BasicDisplayOptions;
}

/** Union of all widget specs */
export type WidgetSpec =
  | DistributionWidgetSpec
  | CumulativeCountWidgetSpec
  | CategoricalCheckboxWidgetSpec
  | BoxWidgetSpec
  | ScatterWidgetSpec
  | ValueScatterWidgetSpec
  | HeatmapAggWidgetSpec
  | GridDistributionCountWidgetSpec
  | CorrelationWidgetSpec
  | IndependenceTestWidgetSpec
  | BasicStatsWidgetSpec
  | XLineWidgetSpec
  | CustomHtmlWidgetSpec;
