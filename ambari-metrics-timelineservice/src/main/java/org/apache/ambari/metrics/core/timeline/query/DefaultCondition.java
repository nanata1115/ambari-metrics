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
package org.apache.ambari.metrics.core.timeline.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.metrics2.sink.timeline.Precision;
import org.apache.ambari.metrics.core.timeline.PhoenixHBaseAccessor;

public class DefaultCondition implements Condition {
  List<String> metricNames;
  List<String> hostnames;
  String appId;
  String instanceId;
  Long startTime;
  Long endTime;
  Precision precision;
  Integer limit;
  boolean grouped;
  boolean noLimit = false;
  Integer fetchSize;
  String statement;
  Set<String> orderByColumns = new LinkedHashSet<String>();
  boolean metricNamesNotCondition = false;
  boolean hostNamesNotCondition = false;
  boolean uuidNotCondition = false;
  List<byte[]> uuids = new ArrayList<>();

  private static final Logger LOG = LoggerFactory.getLogger(DefaultCondition.class);

  public DefaultCondition(List<String> metricNames, List<String> hostnames, String appId,
                          String instanceId, Long startTime, Long endTime, Precision precision,
                          Integer limit, boolean grouped) {
    this.metricNames = metricNames;
    this.hostnames = hostnames;
    this.appId = appId;
    this.instanceId = instanceId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.precision = precision;
    this.limit = limit;
    this.grouped = grouped;
  }

  public DefaultCondition(List<byte[]> uuids, List<String> metricNames, List<String> hostnames, String appId,
                          String instanceId, Long startTime, Long endTime, Precision precision,
                          Integer limit, boolean grouped) {
    this.uuids = uuids;
    this.metricNames = metricNames;
    this.hostnames = hostnames;
    this.appId = appId;
    this.instanceId = instanceId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.precision = precision;
    this.limit = limit;
    this.grouped = grouped;
  }

  @Override
  public String getStatement() {
    return statement;
  }

  @Override
  public void setStatement(String statement) {
    this.statement = statement;
  }

  @Override
  public List<String> getMetricNames() {
    return metricNames == null || metricNames.isEmpty() ? null : metricNames;
  }

  @Override
  public StringBuilder getConditionClause() {
    StringBuilder sb = new StringBuilder();
    boolean appendConjunction = appendUuidClause(sb);
    appendConjunction = append(sb, appendConjunction, getStartTime(), " SERVER_TIME >= ?");
    append(sb, appendConjunction, getEndTime(), " SERVER_TIME < ?");

    return sb;
  }

  protected static boolean append(StringBuilder sb,
                                  boolean appendConjunction,
                                  Object value, String str) {
    if (value != null) {
      if (appendConjunction) {
        sb.append(" AND");
      }

      sb.append(str);
      appendConjunction = true;
    }
    return appendConjunction;
  }

  @Override
  public List<String> getHostnames() {
    return hostnames;
  }

  @Override
  public Precision getPrecision() {
    return precision;
  }

  @Override
  public void setPrecision(Precision precision) {
    this.precision = precision;
  }

  @Override
  public String getAppId() {
    if (appId != null && !appId.isEmpty()) {
      if (!(appId.equals("HOST") || appId.equals("FLUME_HANDLER"))) {
        return appId.toLowerCase();
      } else {
        return appId;
      }
    }
    return null;
  }

  @Override
  public String getInstanceId() {
    return instanceId == null || instanceId.isEmpty() ? null : instanceId;
  }

  /**
   * Convert to millis.
   */
  @Override
  public Long getStartTime() {
    if (startTime == null) {
      return null;
    } else if (startTime < 9999999999l) {
      return startTime * 1000;
    } else {
      return startTime;
    }
  }

  @Override
  public Long getEndTime() {
    if (endTime == null) {
      return null;
    }
    if (endTime < 9999999999l) {
      return endTime * 1000;
    } else {
      return endTime;
    }
  }

  @Override
  public void setNoLimit() {
    this.noLimit = true;
  }

  @Override
  public boolean doUpdate() {
    return false;
  }

  @Override
  public Integer getLimit() {
    if (noLimit) {
      return null;
    }
    return limit == null ? PhoenixHBaseAccessor.RESULTSET_LIMIT : limit;
  }

  @Override
  public boolean isGrouped() {
    return grouped;
  }

  @Override
  public boolean isPointInTime() {
    return getStartTime() == null && getEndTime() == null;
  }

  @Override
  public boolean isEmpty() {
    return (metricNames == null || metricNames.isEmpty())
      && (hostnames == null || hostnames.isEmpty())
      && (appId == null || appId.isEmpty())
      && (instanceId == null || instanceId.isEmpty())
      && startTime == null
      && endTime == null;
  }

  @Override
  public Integer getFetchSize() {
    return fetchSize;
  }

  @Override
  public void setFetchSize(Integer fetchSize) {
    this.fetchSize = fetchSize;
  }

  @Override
  public void addOrderByColumn(String column) {
    orderByColumns.add(column);
  }

  @Override
  public String getOrderByClause(boolean asc) {
    String orderByStr = " ORDER BY ";
    if (!orderByColumns.isEmpty()) {
      StringBuilder sb = new StringBuilder(orderByStr);
      for (String orderByColumn : orderByColumns) {
        if (sb.length() != orderByStr.length()) {
          sb.append(", ");
        }
        sb.append(orderByColumn);
        if (!asc) {
          sb.append(" DESC");
        }
      }
      sb.append(" ");
      return sb.toString();
    }
    return null;
  }

  protected boolean appendUuidClause(StringBuilder sb) {
    boolean appendConjunction = false;

    if (CollectionUtils.isNotEmpty(uuids)) {
      // Put a '(' first
      sb.append("(");

      //IN clause
      // UUID (NOT) IN (?,?,?,?)
      if (CollectionUtils.isNotEmpty(uuids)) {
        sb.append("UUID");
        if (metricNamesNotCondition) {
          sb.append(" NOT");
        }
        sb.append(" IN (");
        //Append ?,?,?,?
        for (int i = 0; i < uuids.size(); i++) {
          sb.append("?");
          if (i < uuids.size() - 1) {
            sb.append(", ");
          }
        }
        sb.append(")");
      }
      appendConjunction = true;
      sb.append(")");
    }

    return appendConjunction;
  }

  @Override
  public String toString() {
    return "Condition{" +
      "uuids=" + uuids +
      ", appId='" + appId + '\'' +
      ", instanceId='" + instanceId + '\'' +
      ", startTime=" + startTime +
      ", endTime=" + endTime +
      ", limit=" + limit +
      ", grouped=" + grouped +
      ", orderBy=" + orderByColumns +
      ", noLimit=" + noLimit +
      '}';
  }

  protected static boolean metricNamesHaveWildcard(List<String> metricNames) {
    for (String name : metricNames) {
      if (name.contains("%")) {
        return true;
      }
    }
    return false;
  }

  protected static boolean hostNamesHaveWildcard(List<String> hostnames) {
    if (hostnames == null)
      return false;
    for (String name : hostnames) {
      if (name.contains("%")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void setMetricNamesNotCondition(boolean metricNamesNotCondition) {
    this.metricNamesNotCondition = metricNamesNotCondition;
  }

  @Override
  public void setHostnamesNotCondition(boolean hostNamesNotCondition) {
    this.hostNamesNotCondition = hostNamesNotCondition;
  }

  @Override
  public void setUuidNotCondition(boolean uuidNotCondition) {
    this.uuidNotCondition = uuidNotCondition;
  }

  @Override
  public List<byte[]> getUuids() {
    return uuids;
  }

  @Override
  public List<String> getTransientMetricNames() {
    return Collections.EMPTY_LIST;
  }
}
