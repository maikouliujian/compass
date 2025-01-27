/*
 * Copyright 2023 OPPO.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oppo.cloud.flink.advice.rule;

import com.google.common.collect.Lists;
import com.oppo.cloud.common.domain.flink.enums.DiagnosisReportYAxisType;
import com.oppo.cloud.common.domain.flink.metric.MetricResult;
import com.oppo.cloud.common.domain.flink.report.DiagnosisRuleLine;
import com.oppo.cloud.common.domain.flink.report.DiagnosisRuleLineChart;
import com.oppo.cloud.common.domain.flink.report.DiagnosisRuleReport;
import com.oppo.cloud.flink.advice.BaseRule;
import com.oppo.cloud.flink.constant.DiagnosisParamsConstants;
import com.oppo.cloud.common.domain.flink.enums.FlinkRule;
import com.oppo.cloud.flink.domain.diagnosis.DiagnosisContext;
import com.oppo.cloud.flink.domain.diagnosis.RcJobDiagnosisAdvice;
import com.oppo.cloud.flink.util.MonitorMetricUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.oppo.cloud.flink.constant.MonitorMetricConstant.SLOW_VERTICES;


/**
 * 慢算子监测规则
 */
@Component
@Slf4j
public class SlowVerticesRule extends BaseRule {
    @Autowired
    private MonitorMetricUtil monitorMetricUtil;

    @Data
    @AllArgsConstructor
    public static class SlowVerticesRuleData extends RcJobDiagnosisAdvice {
        private List<SlowVerticesRecord> slowVertices;

        public SlowVerticesRuleData() {
            this.slowVertices = new ArrayList<>();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlowVerticesRecord {
        private String jobName;
        private String taskName;
        private String taskId;
        private String tmId;
        private String subtaskIndex;
    }

    @Autowired
    DiagnosisParamsConstants cons;

    @Override
    public RcJobDiagnosisAdvice advice(DiagnosisContext context) {
        SlowVerticesRuleData data = new SlowVerticesRuleData();
        buildAdvice(context, data);
        data.setAdviceType(FlinkRule.SlowVerticesRule);
        data.setHasAdvice(false);
        List<MetricResult.DataResult> dataResults = context.getMetrics().get(SLOW_VERTICES);
        if (dataResults == null) {
            data.setAdviceDescription("dataResults为空");
            return data;
        }
        double poolUsageDiffThreshold = cons.slowVerticesInoutDiffHighThreshold;
        long poolUsageDiffHighThresholdSeconds = cons.slowVerticesInoutDiffHighDuration;
        dataResults.forEach(dr -> {
            try {
                int step = monitorMetricUtil.getStep(context.getStart(), context.getEnd());
                Supplier<Stream<Double>> stream = monitorMetricUtil.getValueStream(dr);
                long highCount = stream.get().filter(poolUsageDiff -> {
                    if (poolUsageDiff == null) {
                        return false;
                    }
                    if (poolUsageDiff > poolUsageDiffThreshold) {
                        return true;
                    }
                    return false;
                }).count();
                long highDuration = highCount * step;
                if (highDuration > poolUsageDiffHighThresholdSeconds) {
                    SlowVerticesRecord slowVerticesRecord = new SlowVerticesRecord();
                    slowVerticesRecord.setJobName(context.getRcJobDiagnosis().getJobName());
                    slowVerticesRecord.setTmId((String) dr.getMetric().get("tm_id"));
                    if (dr.getMetric().get("task_id") != null) {
                        slowVerticesRecord.setTaskId((String) dr.getMetric().get("task_id"));
                    }
                    if (dr.getMetric().get("task_name") != null) {
                        slowVerticesRecord.setTaskName((String) dr.getMetric().get("task_name"));
                    }
                    if (dr.getMetric().get("subtask_index") != null) {
                        slowVerticesRecord.setSubtaskIndex((String) dr.getMetric().get("subtask_index"));
                    }
                    if (data.getSlowVertices().size() == 0) {
                        data.getSlowVertices().add(slowVerticesRecord);
                    }
                }
            } catch (Throwable e) {
                log.error("计算慢算子报错,请打开stdout查看异常堆栈:" + e.getMessage(), e);
            }
        });
        Set<String> tasks = new HashSet<>();
        data.getSlowVertices().stream().forEach(x -> {
            tasks.add(x.getTaskName());
        });
        String distinctTaskName = tasks.toString();
        if (data.getSlowVertices().size() != 0) {
            data.setHasAdvice(true);
            StringBuilder sb = new StringBuilder();
            sb.append("存在慢算子");
            if (distinctTaskName != null && !distinctTaskName.equals("")) {
                sb.append(":").append(distinctTaskName);
            }
            data.setAdviceDescription(sb.toString());
            data.setSlowTasks(distinctTaskName);
            String conclusion = String.format("存在慢算子,%s 算子的in/out pool使用率差值大于阈值%.2f%% 超过%d秒,建议优化代码",
                    distinctTaskName, poolUsageDiffThreshold, poolUsageDiffHighThresholdSeconds);
            DiagnosisRuleReport diagnosisRuleReport = new DiagnosisRuleReport();
            diagnosisRuleReport.setTitle("慢算子分析");
            diagnosisRuleReport.setConclusion(conclusion);
            DiagnosisRuleLineChart diagnosisRuleLineChart = new DiagnosisRuleLineChart();
            diagnosisRuleLineChart.setTitle("慢算子");
            Map<String, Double> constLineMap = new HashMap<>();
            constLineMap.put("阈值", poolUsageDiffThreshold);
            diagnosisRuleLineChart.setConstLines(constLineMap);
            DiagnosisRuleLine line = new DiagnosisRuleLine();
            line.setLabel("算子in/out pool使用率差值");
            line.setData(dataResults);
            diagnosisRuleLineChart.setLine(line);
            diagnosisRuleLineChart.setYAxisUnit("%");
            diagnosisRuleLineChart.setYAxisValueType(DiagnosisReportYAxisType.Percent.name());
            Map<String, Double> constLine = new HashMap<>();
            diagnosisRuleLineChart.setConstLines(constLine);
            diagnosisRuleReport.setIDiagnosisRuleCharts(Lists.newArrayList(diagnosisRuleLineChart));
            data.setDiagnosisRuleReport(diagnosisRuleReport);
        } else {
            data.setAdviceDescription("无建议");
        }
        return data;
    }
}
