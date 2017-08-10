/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.metadata.formatting;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.metastore.api.BinaryColumnStatsData;
import org.apache.hadoop.hive.metastore.api.BooleanColumnStatsData;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsData;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsObj;
import org.apache.hadoop.hive.metastore.api.DateColumnStatsData;
import org.apache.hadoop.hive.metastore.api.Decimal;
import org.apache.hadoop.hive.metastore.api.DecimalColumnStatsData;
import org.apache.hadoop.hive.metastore.api.DoubleColumnStatsData;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Index;
import org.apache.hadoop.hive.metastore.api.LongColumnStatsData;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.StringColumnStatsData;
import org.apache.hadoop.hive.ql.index.HiveIndex;
import org.apache.hadoop.hive.ql.index.HiveIndex.IndexType;
import org.apache.hadoop.hive.ql.metadata.ForeignKeyInfo;
import org.apache.hadoop.hive.ql.metadata.Partition;
import org.apache.hadoop.hive.ql.metadata.PrimaryKeyInfo;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.metadata.ForeignKeyInfo.ForeignKeyCol;
import org.apache.hadoop.hive.ql.plan.DescTableDesc;
import org.apache.hadoop.hive.ql.plan.PlanUtils;
import org.apache.hadoop.hive.ql.plan.ShowIndexesDesc;
import org.apache.hadoop.hive.serde2.io.DateWritable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * This class provides methods to format table and index information.
 *
 */
public final class MetaDataFormatUtils {

  public static final String FIELD_DELIM = "\t";
  public static final String LINE_DELIM = "\n";

  static final int DEFAULT_STRINGBUILDER_SIZE = 2048;
  private static final int ALIGNMENT = 20;

  private MetaDataFormatUtils() {
  }

  private static void formatColumnsHeader(StringBuilder columnInformation,
      List<ColumnStatisticsObj> colStats) {
    columnInformation.append("# "); // Easy for shell scripts to ignore
    formatOutput(getColumnsHeader(colStats), columnInformation);
    columnInformation.append(LINE_DELIM);
  }

  /**
   * Write formatted information about the given columns to a string
   * @param cols - list of columns
   * @param printHeader - if header should be included
   * @param isOutputPadded - make it more human readable by setting indentation
   *        with spaces. Turned off for use by HiveServer2
   * @param colStats
   * @return string with formatted column information
   */
  public static String getAllColumnsInformation(List<FieldSchema> cols,
      boolean printHeader, boolean isOutputPadded, List<ColumnStatisticsObj> colStats) {
    StringBuilder columnInformation = new StringBuilder(DEFAULT_STRINGBUILDER_SIZE);
    if(printHeader){
      formatColumnsHeader(columnInformation, colStats);
    }

    formatAllFields(columnInformation, cols, isOutputPadded, colStats);
    return columnInformation.toString();
  }

  /**
   * Write formatted information about the given columns, including partition
   * columns to a string
   * @param cols - list of columns
   * @param partCols - list of partition columns
   * @param printHeader - if header should be included
   * @param isOutputPadded - make it more human readable by setting indentation
   *        with spaces. Turned off for use by HiveServer2
   * @param showParColsSep - show partition column separator
   * @return string with formatted column information
   */
  public static String getAllColumnsInformation(List<FieldSchema> cols,
      List<FieldSchema> partCols, boolean printHeader, boolean isOutputPadded, boolean showPartColsSep) {
    StringBuilder columnInformation = new StringBuilder(DEFAULT_STRINGBUILDER_SIZE);
    if(printHeader){
      formatColumnsHeader(columnInformation, null);
    }
    formatAllFields(columnInformation, cols, isOutputPadded, null);

    if ((partCols != null) && !partCols.isEmpty() && showPartColsSep) {
      columnInformation.append(LINE_DELIM).append("# Partition Information")
      .append(LINE_DELIM);
      formatColumnsHeader(columnInformation, null);
      formatAllFields(columnInformation, partCols, isOutputPadded, null);
    }

    return columnInformation.toString();
  }

  /**
   * Write formatted column information into given StringBuilder
   * @param tableInfo - StringBuilder to append column information into
   * @param cols - list of columns
   * @param isOutputPadded - make it more human readable by setting indentation
   *        with spaces. Turned off for use by HiveServer2
   * @param colStats
   */
  private static void formatAllFields(StringBuilder tableInfo,
      List<FieldSchema> cols, boolean isOutputPadded, List<ColumnStatisticsObj> colStats) {
    for (FieldSchema col : cols) {
      if(isOutputPadded) {
        formatWithIndentation(col.getName(), col.getType(), getComment(col), tableInfo, colStats);
      }
      else {
        formatWithoutIndentation(col.getName(), col.getType(), col.getComment(), tableInfo, colStats);
      }
    }
  }

  private static String convertToString(Decimal val) {
    if (val == null) {
      return "";
    }

    HiveDecimal result = HiveDecimal.create(new BigInteger(val.getUnscaled()), val.getScale());
    if (result != null) {
      return result.toString();
    } else {
      return "";
    }
  }

  private static String convertToString(org.apache.hadoop.hive.metastore.api.Date val) {
    if (val == null) {
      return "";
    }

    DateWritable writableValue = new DateWritable((int) val.getDaysSinceEpoch());
    return writableValue.toString();
  }

  private static String convertToString(byte[] buf) {
    if (buf == null || buf.length == 0) {
      return "";
    }
    byte[] sub = new byte[2];
    sub[0] = (byte) buf[0];
    sub[1] = (byte) buf[1];
    return new String(sub);
  }

  private static ColumnStatisticsObj getColumnStatisticsObject(String colName,
      String colType, List<ColumnStatisticsObj> colStats) {
    if (colStats != null && !colStats.isEmpty()) {
      for (ColumnStatisticsObj cso : colStats) {
        if (cso.getColName().equalsIgnoreCase(colName)
            && cso.getColType().equalsIgnoreCase(colType)) {
          return cso;
        }
      }
    }
    return null;
  }

  private static void formatWithoutIndentation(String name, String type, String comment,
      StringBuilder colBuffer, List<ColumnStatisticsObj> colStats) {
    colBuffer.append(name);
    colBuffer.append(FIELD_DELIM);
    colBuffer.append(type);
    colBuffer.append(FIELD_DELIM);
    if (colStats != null) {
      ColumnStatisticsObj cso = getColumnStatisticsObject(name, type, colStats);
      if (cso != null) {
        ColumnStatisticsData csd = cso.getStatsData();
        if (csd.isSetBinaryStats()) {
          BinaryColumnStatsData bcsd = csd.getBinaryStats();
          appendColumnStatsNoFormatting(colBuffer, "", "", bcsd.getNumNulls(), "",
              bcsd.getAvgColLen(), bcsd.getMaxColLen(), "", "");
        } else if (csd.isSetStringStats()) {
          StringColumnStatsData scsd = csd.getStringStats();
          appendColumnStatsNoFormatting(colBuffer, "", "", scsd.getNumNulls(), scsd.getNumDVs(),
              scsd.getAvgColLen(), scsd.getMaxColLen(), "", "");
        } else if (csd.isSetBooleanStats()) {
          BooleanColumnStatsData bcsd = csd.getBooleanStats();
          appendColumnStatsNoFormatting(colBuffer, "", "", bcsd.getNumNulls(), "", "", "",
              bcsd.getNumTrues(), bcsd.getNumFalses());
        } else if (csd.isSetDecimalStats()) {
          DecimalColumnStatsData dcsd = csd.getDecimalStats();
          appendColumnStatsNoFormatting(colBuffer, convertToString(dcsd.getLowValue()),
              convertToString(dcsd.getHighValue()), dcsd.getNumNulls(), dcsd.getNumDVs(),
              "", "", "", "");
        } else if (csd.isSetDoubleStats()) {
          DoubleColumnStatsData dcsd = csd.getDoubleStats();
          appendColumnStatsNoFormatting(colBuffer, dcsd.getLowValue(), dcsd.getHighValue(),
              dcsd.getNumNulls(), dcsd.getNumDVs(), "", "", "", "");
        } else if (csd.isSetLongStats()) {
          LongColumnStatsData lcsd = csd.getLongStats();
          appendColumnStatsNoFormatting(colBuffer, lcsd.getLowValue(), lcsd.getHighValue(),
              lcsd.getNumNulls(), lcsd.getNumDVs(), "", "", "", "");
        } else if (csd.isSetDateStats()) {
          DateColumnStatsData dcsd = csd.getDateStats();
          appendColumnStatsNoFormatting(colBuffer,
              convertToString(dcsd.getLowValue()),
              convertToString(dcsd.getHighValue()),
              dcsd.getNumNulls(), dcsd.getNumDVs(), "", "", "", "");
        }
      } else {
        appendColumnStatsNoFormatting(colBuffer, "", "", "", "", "", "", "", "");
      }
    }
    colBuffer.append(comment == null ? "" : comment);
    colBuffer.append(LINE_DELIM);
  }

  public static String getAllColumnsInformation(Index index) {
    StringBuilder indexInfo = new StringBuilder(DEFAULT_STRINGBUILDER_SIZE);

    List<String> indexColumns = new ArrayList<String>();

    indexColumns.add(index.getIndexName());
    indexColumns.add(index.getOrigTableName());

    // index key names
    List<FieldSchema> indexKeys = index.getSd().getCols();
    StringBuilder keyString = new StringBuilder();
    boolean first = true;
    for (FieldSchema key : indexKeys)
    {
      if (!first)
      {
        keyString.append(", ");
      }
      keyString.append(key.getName());
      first = false;
    }

    indexColumns.add(keyString.toString());

    indexColumns.add(index.getIndexTableName());

    // index type
    String indexHandlerClass = index.getIndexHandlerClass();
    IndexType indexType = HiveIndex.getIndexTypeByClassName(indexHandlerClass);
    indexColumns.add(indexType.getName());

    indexColumns.add(index.getParameters().get("comment"));

    formatOutput(indexColumns.toArray(new String[0]), indexInfo);

    return indexInfo.toString();
  }

  public static String getConstraintsInformation(PrimaryKeyInfo pkInfo, ForeignKeyInfo fkInfo) {
    StringBuilder constraintsInfo = new StringBuilder(DEFAULT_STRINGBUILDER_SIZE);

    constraintsInfo.append(LINE_DELIM).append("# Constraints").append(LINE_DELIM);
    if (pkInfo != null && !pkInfo.getColNames().isEmpty()) {
      constraintsInfo.append(LINE_DELIM).append("# Primary Key").append(LINE_DELIM);
      getPrimaryKeyInformation(constraintsInfo, pkInfo);
    }
    if (fkInfo != null && !fkInfo.getForeignKeys().isEmpty()) {
      constraintsInfo.append(LINE_DELIM).append("# Foreign Keys").append(LINE_DELIM);
      getForeignKeysInformation(constraintsInfo, fkInfo);
    }
    return constraintsInfo.toString();
  }

  private static void  getPrimaryKeyInformation(StringBuilder constraintsInfo,
    PrimaryKeyInfo pkInfo) {
    formatOutput("Table:", pkInfo.getDatabaseName()+"."+pkInfo.getTableName(), constraintsInfo);
    formatOutput("Constraint Name:", pkInfo.getConstraintName(), constraintsInfo);
    Map<Integer, String> colNames = pkInfo.getColNames();
    final String columnNames = "Column Names:";
    constraintsInfo.append(String.format("%-" + ALIGNMENT + "s", columnNames)).append(FIELD_DELIM);
    if (colNames != null && colNames.size() > 0) {
      formatOutput(colNames.values().toArray(new String[colNames.size()]), constraintsInfo);
    }
  }

  private static void getForeignKeyColInformation(StringBuilder constraintsInfo,
    ForeignKeyCol fkCol) {
      String[] fkcFields = new String[3];
      fkcFields[0] = "Parent Column Name:" + fkCol.parentDatabaseName +
          "."+ fkCol.parentTableName + "." + fkCol.parentColName;
      fkcFields[1] = "Column Name:" + fkCol.childColName;
      fkcFields[2] = "Key Sequence:" + fkCol.position;
      formatOutput(fkcFields, constraintsInfo);
  }

  private static void getForeignKeyRelInformation(
    StringBuilder constraintsInfo,
    String constraintName,
    List<ForeignKeyCol> fkRel) {
    formatOutput("Constraint Name:", constraintName, constraintsInfo);
    if (fkRel != null && fkRel.size() > 0) {
      for (ForeignKeyCol fkc : fkRel) {
        getForeignKeyColInformation(constraintsInfo, fkc);
      }
    }
    constraintsInfo.append(LINE_DELIM);
  }

  private static void  getForeignKeysInformation(StringBuilder constraintsInfo,
    ForeignKeyInfo fkInfo) {
    formatOutput("Table:",
                 fkInfo.getChildDatabaseName()+"."+fkInfo.getChildTableName(),
                 constraintsInfo);
    Map<String, List<ForeignKeyCol>> foreignKeys = fkInfo.getForeignKeys();
    if (foreignKeys != null && foreignKeys.size() > 0) {
      for (Map.Entry<String, List<ForeignKeyCol>> me : foreignKeys.entrySet()) {
        getForeignKeyRelInformation(constraintsInfo, me.getKey(), me.getValue());
      }
    }
  }

  public static String getPartitionInformation(Partition part) {
    StringBuilder tableInfo = new StringBuilder(DEFAULT_STRINGBUILDER_SIZE);

    // Table Metadata
    tableInfo.append(LINE_DELIM).append("# Detailed Partition Information").append(LINE_DELIM);
    getPartitionMetaDataInformation(tableInfo, part);

    // Storage information.
    if (part.getTable().getTableType() != TableType.VIRTUAL_VIEW) {
      tableInfo.append(LINE_DELIM).append("# Storage Information").append(LINE_DELIM);
      getStorageDescriptorInfo(tableInfo, part.getTPartition().getSd());
    }

    return tableInfo.toString();
  }

  public static String getTableInformation(Table table) {
    StringBuilder tableInfo = new StringBuilder(DEFAULT_STRINGBUILDER_SIZE);

    // Table Metadata
    tableInfo.append(LINE_DELIM).append("# Detailed Table Information").append(LINE_DELIM);
    getTableMetaDataInformation(tableInfo, table);

    // Storage information.
    tableInfo.append(LINE_DELIM).append("# Storage Information").append(LINE_DELIM);
    getStorageDescriptorInfo(tableInfo, table.getTTable().getSd());

    if (table.isView()) {
      tableInfo.append(LINE_DELIM).append("# View Information").append(LINE_DELIM);
      getViewInfo(tableInfo, table);
    }

    return tableInfo.toString();
  }

  private static void getViewInfo(StringBuilder tableInfo, Table tbl) {
    formatOutput("View Original Text:", tbl.getViewOriginalText(), tableInfo);
    formatOutput("View Expanded Text:", tbl.getViewExpandedText(), tableInfo);
  }

  private static void getStorageDescriptorInfo(StringBuilder tableInfo,
      StorageDescriptor storageDesc) {

    formatOutput("SerDe Library:", storageDesc.getSerdeInfo().getSerializationLib(), tableInfo);
    formatOutput("InputFormat:", storageDesc.getInputFormat(), tableInfo);
    formatOutput("OutputFormat:", storageDesc.getOutputFormat(), tableInfo);
    formatOutput("Compressed:", storageDesc.isCompressed() ? "Yes" : "No", tableInfo);
    formatOutput("Num Buckets:", String.valueOf(storageDesc.getNumBuckets()), tableInfo);
    formatOutput("Bucket Columns:", storageDesc.getBucketCols().toString(), tableInfo);
    formatOutput("Sort Columns:", storageDesc.getSortCols().toString(), tableInfo);
    if (storageDesc.isStoredAsSubDirectories()) {// optional parameter
      formatOutput("Stored As SubDirectories:", "Yes", tableInfo);
    }

    if (null != storageDesc.getSkewedInfo()) {
      List<String> skewedColNames = storageDesc.getSkewedInfo().getSkewedColNames();
      if ((skewedColNames != null) && (skewedColNames.size() > 0)) {
        formatOutput("Skewed Columns:", skewedColNames.toString(), tableInfo);
      }

      List<List<String>> skewedColValues = storageDesc.getSkewedInfo().getSkewedColValues();
      if ((skewedColValues != null) && (skewedColValues.size() > 0)) {
        formatOutput("Skewed Values:", skewedColValues.toString(), tableInfo);
      }

      Map<List<String>, String> skewedColMap = storageDesc.getSkewedInfo()
          .getSkewedColValueLocationMaps();
      if ((skewedColMap!=null) && (skewedColMap.size() > 0)) {
        formatOutput("Skewed Value to Path:", skewedColMap.toString(),
            tableInfo);
        Map<List<String>, String> truncatedSkewedColMap = new HashMap<List<String>, String>();
        // walk through existing map to truncate path so that test won't mask it
        // then we can verify location is right
        Set<Entry<List<String>, String>> entries = skewedColMap.entrySet();
        for (Entry<List<String>, String> entry : entries) {
          truncatedSkewedColMap.put(entry.getKey(),
              PlanUtils.removePrefixFromWarehouseConfig(entry.getValue()));
        }
        formatOutput("Skewed Value to Truncated Path:",
            truncatedSkewedColMap.toString(), tableInfo);
      }
    }

    if (storageDesc.getSerdeInfo().getParametersSize() > 0) {
      tableInfo.append("Storage Desc Params:").append(LINE_DELIM);
      displayAllParameters(storageDesc.getSerdeInfo().getParameters(), tableInfo);
    }
  }

  private static void getTableMetaDataInformation(StringBuilder tableInfo, Table  tbl) {
    formatOutput("Database:", tbl.getDbName(), tableInfo);
    formatOutput("Owner:", tbl.getOwner(), tableInfo);
    formatOutput("CreateTime:", formatDate(tbl.getTTable().getCreateTime()), tableInfo);
    formatOutput("LastAccessTime:", formatDate(tbl.getTTable().getLastAccessTime()), tableInfo);
    formatOutput("Retention:", Integer.toString(tbl.getRetention()), tableInfo);
    if (!tbl.isView()) {
      formatOutput("Location:", tbl.getDataLocation().toString(), tableInfo);
    }
    formatOutput("Table Type:", tbl.getTableType().name(), tableInfo);

    if (tbl.getParameters().size() > 0) {
      tableInfo.append("Table Parameters:").append(LINE_DELIM);
      displayAllParameters(tbl.getParameters(), tableInfo);
    }
  }

  private static void getPartitionMetaDataInformation(StringBuilder tableInfo, Partition part) {
    formatOutput("Partition Value:", part.getValues().toString(), tableInfo);
    formatOutput("Database:", part.getTPartition().getDbName(), tableInfo);
    formatOutput("Table:", part.getTable().getTableName(), tableInfo);
    formatOutput("CreateTime:", formatDate(part.getTPartition().getCreateTime()), tableInfo);
    formatOutput("LastAccessTime:", formatDate(part.getTPartition().getLastAccessTime()),
        tableInfo);
    formatOutput("Location:", part.getLocation(), tableInfo);

    if (part.getTPartition().getParameters().size() > 0) {
      tableInfo.append("Partition Parameters:").append(LINE_DELIM);
      displayAllParameters(part.getTPartition().getParameters(), tableInfo);
    }
  }

  private static void displayAllParameters(Map<String, String> params, StringBuilder tableInfo) {
    List<String> keys = new ArrayList<String>(params.keySet());
    Collections.sort(keys);
    for (String key : keys) {
      tableInfo.append(FIELD_DELIM); // Ensures all params are indented.
      formatOutput(key, StringEscapeUtils.escapeJava(params.get(key)), tableInfo);
    }
  }

  static String getComment(FieldSchema col) {
    return col.getComment() != null ? col.getComment() : "";
  }

  private static String formatDate(long timeInSeconds) {
    if (timeInSeconds != 0) {
      Date date = new Date(timeInSeconds * 1000);
      return date.toString();
    }
    return "UNKNOWN";
  }

  private static void formatOutput(String[] fields, StringBuilder tableInfo) {
    for (String field : fields) {
      if (field == null) {
        tableInfo.append(FIELD_DELIM);
        continue;
      }
      tableInfo.append(String.format("%-" + ALIGNMENT + "s", field)).append(FIELD_DELIM);
    }
    tableInfo.append(LINE_DELIM);
  }

  private static void formatOutput(String name, String value,
      StringBuilder tableInfo) {
    tableInfo.append(String.format("%-" + ALIGNMENT + "s", name)).append(FIELD_DELIM);
    tableInfo.append(String.format("%-" + ALIGNMENT + "s", value)).append(LINE_DELIM);
  }

  /**
   * Prints the name value pair
   * It the output is padded then unescape the value, so it could be printed in multiple lines.
   * In this case it assumes the pair is already indented with a field delimiter
   * @param name The field name to print
   * @param value The value t print
   * @param tableInfo The target builder
   * @param isOutputPadded Should the value printed as a padded string?
   */
  protected static void formatOutput(String name, String value, StringBuilder tableInfo,
      boolean isOutputPadded) {
    String unescapedValue =
        (isOutputPadded && value != null) ? value.replaceAll("\\\\n|\\\\r|\\\\r\\\\n","\n"):value;
    formatOutput(name, unescapedValue, tableInfo);
  }

  private static void formatWithIndentation(String colName, String colType, String colComment,
      StringBuilder tableInfo, List<ColumnStatisticsObj> colStats) {
    tableInfo.append(String.format("%-" + ALIGNMENT + "s", colName)).append(FIELD_DELIM);
    tableInfo.append(String.format("%-" + ALIGNMENT + "s", colType)).append(FIELD_DELIM);

    if (colStats != null) {
      ColumnStatisticsObj cso = getColumnStatisticsObject(colName, colType, colStats);
      if (cso != null) {
        ColumnStatisticsData csd = cso.getStatsData();
        if (csd.isSetBinaryStats()) {
          BinaryColumnStatsData bcsd = csd.getBinaryStats();
          appendColumnStats(tableInfo, "", "", bcsd.getNumNulls(), "", "", bcsd.getAvgColLen(),
              bcsd.getMaxColLen(), "", "");
        } else if (csd.isSetStringStats()) {
          StringColumnStatsData scsd = csd.getStringStats();
          appendColumnStats(tableInfo, "", "", scsd.getNumNulls(), scsd.getNumDVs(),
              convertToString(scsd.getBitVectors()), scsd.getAvgColLen(),
              scsd.getMaxColLen(), "", "");
        } else if (csd.isSetBooleanStats()) {
          BooleanColumnStatsData bcsd = csd.getBooleanStats();
          appendColumnStats(tableInfo, "", "", bcsd.getNumNulls(), "", "", "", "",
              bcsd.getNumTrues(), bcsd.getNumFalses());
        } else if (csd.isSetDecimalStats()) {
          DecimalColumnStatsData dcsd = csd.getDecimalStats();
          appendColumnStats(tableInfo, convertToString(dcsd.getLowValue()),
              convertToString(dcsd.getHighValue()), dcsd.getNumNulls(), dcsd.getNumDVs(),
              convertToString(dcsd.getBitVectors()),
              "", "", "", "");
        } else if (csd.isSetDoubleStats()) {
          DoubleColumnStatsData dcsd = csd.getDoubleStats();
          appendColumnStats(tableInfo, dcsd.getLowValue(), dcsd.getHighValue(), dcsd.getNumNulls(),
              dcsd.getNumDVs(), convertToString(dcsd.getBitVectors()),
              "", "", "", "");
        } else if (csd.isSetLongStats()) {
          LongColumnStatsData lcsd = csd.getLongStats();
          appendColumnStats(tableInfo, lcsd.getLowValue(), lcsd.getHighValue(), lcsd.getNumNulls(),
              lcsd.getNumDVs(), convertToString(lcsd.getBitVectors()),
              "", "", "", "");
        } else if (csd.isSetDateStats()) {
          DateColumnStatsData dcsd = csd.getDateStats();
          appendColumnStats(tableInfo,
              convertToString(dcsd.getLowValue()),
              convertToString(dcsd.getHighValue()),
              dcsd.getNumNulls(), dcsd.getNumDVs(),
              convertToString(dcsd.getBitVectors()),
              "", "", "", "");
        }
      } else {
        appendColumnStats(tableInfo, "", "", "", "", "", "", "", "", "");
      }
    }

    // comment indent processing for multi-line comments
    // comments should be indented the same amount on each line
    // if the first line comment starts indented by k,
    // the following line comments should also be indented by k
    String[] commentSegments = colComment.split("\n|\r|\r\n");
    tableInfo.append(String.format("%-" + ALIGNMENT + "s", commentSegments[0])).append(LINE_DELIM);
    int colNameLength = ALIGNMENT > colName.length() ? ALIGNMENT : colName.length();
    int colTypeLength = ALIGNMENT > colType.length() ? ALIGNMENT : colType.length();
    for (int i = 1; i < commentSegments.length; i++) {
      tableInfo.append(String.format("%" + colNameLength + "s" + FIELD_DELIM + "%"
          + colTypeLength + "s" + FIELD_DELIM + "%s", "", "", commentSegments[i])).append(LINE_DELIM);
    }
  }

  private static void appendColumnStats(StringBuilder sb, Object min, Object max, Object numNulls,
      Object ndv, Object bitVector, Object avgColLen, Object maxColLen, Object numTrues, Object numFalses) {
    sb.append(String.format("%-" + ALIGNMENT + "s", min)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", max)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", numNulls)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", ndv)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", avgColLen)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", maxColLen)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", numTrues)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", numFalses)).append(FIELD_DELIM);
    sb.append(String.format("%-" + ALIGNMENT + "s", bitVector)).append(FIELD_DELIM);
  }

  private static void appendColumnStatsNoFormatting(StringBuilder sb, Object min,
      Object max, Object numNulls, Object ndv, Object avgColLen, Object maxColLen,
      Object numTrues, Object numFalses) {
    sb.append(min).append(FIELD_DELIM);
    sb.append(max).append(FIELD_DELIM);
    sb.append(numNulls).append(FIELD_DELIM);
    sb.append(ndv).append(FIELD_DELIM);
    sb.append(avgColLen).append(FIELD_DELIM);
    sb.append(maxColLen).append(FIELD_DELIM);
    sb.append(numTrues).append(FIELD_DELIM);
    sb.append(numFalses).append(FIELD_DELIM);
  }

  public static String[] getColumnsHeader(List<ColumnStatisticsObj> colStats) {
    boolean showColStats = false;
    if (colStats != null) {
      showColStats = true;
    }
    return DescTableDesc.getSchema(showColStats).split("#")[0].split(",");
  }

  public static String getIndexColumnsHeader() {
    StringBuilder indexCols = new StringBuilder(DEFAULT_STRINGBUILDER_SIZE);
    formatOutput(ShowIndexesDesc.getSchema().split("#")[0].split(","), indexCols);
    return indexCols.toString();
  }
  public static MetaDataFormatter getFormatter(HiveConf conf) {
    if ("json".equals(conf.get(HiveConf.ConfVars.HIVE_DDL_OUTPUT_FORMAT.varname, "text"))) {
      return new JsonMetaDataFormatter();
    } else {
      return new TextMetaDataFormatter(conf.getIntVar(HiveConf.ConfVars.CLIPRETTYOUTPUTNUMCOLS), conf.getBoolVar(ConfVars.HIVE_DISPLAY_PARTITION_COLUMNS_SEPARATELY));
    }
  }
}
