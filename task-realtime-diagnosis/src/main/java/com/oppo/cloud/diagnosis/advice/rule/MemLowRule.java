package com.oppo.cloud.diagnosis.advice.rule;


import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.oppo.cloud.common.domain.flink.enums.EDiagnosisReportYAxisType;
import com.oppo.cloud.common.domain.flink.report.DiagnosisRuleLine;
import com.oppo.cloud.common.domain.flink.report.DiagnosisRuleLineChart;
import com.oppo.cloud.common.domain.flink.report.DiagnosisRuleReport;
import com.oppo.cloud.diagnosis.advice.BaseRule;
import com.oppo.cloud.diagnosis.advice.turning.TurningAdvice;
import com.oppo.cloud.diagnosis.advice.turning.TurningManager;
import com.oppo.cloud.common.domain.flink.enums.EDiagnosisRule;
import com.oppo.cloud.common.domain.flink.enums.EDiagnosisTurningStatus;
import com.oppo.cloud.diagnosis.domain.diagnosis.DiagnosisContext;
import com.oppo.cloud.diagnosis.domain.diagnosis.RcJobDiagnosis;
import com.oppo.cloud.diagnosis.domain.diagnosis.RcJobDiagnosisAdvice;
import com.oppo.cloud.common.domain.flink.metric.MetricResult;
import com.oppo.cloud.diagnosis.util.DoctorUtil;
import com.oppo.cloud.diagnosis.util.MonitorMetricUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.oppo.cloud.diagnosis.constant.MonitorMetricConstant.TM_HEAP_MEM_USAGE_RATE;

/**
 * 缩减内存规则
 */
@Component
@Slf4j
public class MemLowRule extends BaseRule {
    @Autowired
    DoctorUtil doctorUtil;
    @Autowired
    TurningManager turningManager;
    @Autowired
    MonitorMetricUtil monitorMetricUtil;

    @Override
    public RcJobDiagnosisAdvice advice(DiagnosisContext context) {
        Double memLowThreshold = doctorUtil.getMemLowThreshold(context);
        RcJobDiagnosis rcJobDiagnosis = context.getRcJobDiagnosis();
        RcJobDiagnosisAdvice.RcJobDiagnosisAdviceBuilder builder = getBuilder(context);
        builder.adviceType(EDiagnosisRule.TmMemoryLow);
        // 尝试降低tm内存,判断最大的堆内存利用率小于阈值
        List<MetricResult.DataResult> memUsageList = context.getMetrics().get(TM_HEAP_MEM_USAGE_RATE);
        if (memUsageList != null && memUsageList.size() > 0) {
            Double maxMemUsage = memUsageList.stream()
                    .map(monitorMetricUtil::getAvg).max(Double::compareTo)
                    .orElse(Double.MAX_VALUE);
            if (maxMemUsage < memLowThreshold) {
                TurningAdvice turning = turningManager.turningMemDown(context);
                if (turning != null && turning.getStatus().equals(EDiagnosisTurningStatus.HAS_ADVICE)) {
                    RcJobDiagnosisAdvice build = builder.hasAdvice(true)
                            .diagnosisParallel(turning.getParallel())
                            .diagnosisTmMem(turning.getTmMem())
                            .diagnosisTmSlotNum(turning.getTmSlotNum())
                            .diagnosisTmCore(turning.getVcore())
                            .diagnosisTmNum(turning.getTmNum())
                            .adviceDescription(String.format("tm最大堆内存利用率低(%.2f%%);",
                                    maxMemUsage * 100)
                            ).build();
                    convertAdviceToRcJobDiagnosis(build,context);
                    String resourceChange = buildResourceChange(context);
                    String conclusion = String.format("作业内存利用率%.2f%%,小于阈值%.2f%%;%s",
                            maxMemUsage * 100, memLowThreshold * 100,resourceChange);
                    DiagnosisRuleReport diagnosisRuleReport = new DiagnosisRuleReport();
                    diagnosisRuleReport.setTitle("内存利用率高分析");
                    diagnosisRuleReport.setConclusion(conclusion);
                    DiagnosisRuleLineChart diagnosisRuleLineChart = new DiagnosisRuleLineChart();
                    diagnosisRuleLineChart.setTitle("作业内存使用率");
                    diagnosisRuleLineChart.setYAxisUnit("%");
                    diagnosisRuleLineChart.setYAxisValueType(EDiagnosisReportYAxisType.Percent.name());
                    diagnosisRuleLineChart.setYAxisMax(1d);
                    diagnosisRuleLineChart.setYAxisMin(0d);
                    DiagnosisRuleLine line = new DiagnosisRuleLine();
                    line.setLabel("作业内存使用率");
                    line.setData(memUsageList);
                    diagnosisRuleLineChart.setLine(line);
                    Map<String,Double> constLine = new HashMap<>();
                    constLine.put("阈值",memLowThreshold);
                    constLine.put("内存利用率",maxMemUsage);
                    diagnosisRuleLineChart.setConstLines(constLine);
                    diagnosisRuleReport.setIDiagnosisRuleCharts(Lists.newArrayList(diagnosisRuleLineChart));
                    build.setDiagnosisRuleReport(diagnosisRuleReport);
                    return build;
                } else {
                    String desc = String.format("tm最大堆内存利用率低(%.2f%%)", maxMemUsage * 100);
                    if (turning != null) {
                        desc = desc + "," + turning.getDescription();
                    }
                    return builder
                            .hasAdvice(false)
                            .adviceDescription(desc)
                            .build();
                }
            } else {
                log.debug(String.format("%s最大的tm堆内存利用率%.2f", context.getRcJobDiagnosis().getJobName(), maxMemUsage));
            }
        }

        return builder
                .adviceDescription("没有建议")
                .build();
    }

}
