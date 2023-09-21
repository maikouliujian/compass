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

package com.oppo.cloud.portal.service.diagnose.resource;

import com.alibaba.fastjson2.JSONObject;
import com.oppo.cloud.common.constant.AppCategoryEnum;
import com.oppo.cloud.common.domain.eventlog.CpuWasteAbnormal;
import com.oppo.cloud.common.domain.eventlog.DetectorResult;
import com.oppo.cloud.common.domain.eventlog.config.DetectorConfig;
import com.oppo.cloud.common.util.ui.UIUtil;
import com.oppo.cloud.portal.domain.diagnose.Chart;
import com.oppo.cloud.portal.domain.diagnose.IsAbnormal;
import com.oppo.cloud.portal.domain.diagnose.resources.CpuWasteInfo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * cpu资源浪费
 */
@Order(1)
@Service
public class CpuWasteService extends ResourceBaseService<CpuWasteInfo> {

    @Override
    public String getCategory() {
        return AppCategoryEnum.CPU_WASTE.getCategory();
    }

    @Override
    public String getType() {
        return "cpuChart";
    }

    @Override
    public CpuWasteInfo generateData(DetectorResult detectorResult, DetectorConfig config,
                                     String applicationId) throws Exception {
        CpuWasteAbnormal cpuWasteAbnormal =
                ((JSONObject) detectorResult.getData()).toJavaObject(CpuWasteAbnormal.class);
        CpuWasteInfo cpuWasteInfo = new CpuWasteInfo();
        cpuWasteInfo.buildInfo(cpuWasteAbnormal, config.getCpuWasteConfig());
        if (cpuWasteInfo.getDriverWastePercent() > cpuWasteInfo.getDriverThreshold()
                || cpuWasteInfo.getExecutorWastePercent() > cpuWasteInfo.getExecutorThreshold()) {
            cpuWasteInfo.setAbnormal(true);
        }
        addColor(cpuWasteInfo);
        return cpuWasteInfo;
    }


    @Override
    public String generateConclusionDesc(IsAbnormal data) {
        return String.format(
                "计算规则：<br/>&nbsp;  app消耗的计算资源 = 最大并发vCore * app运行时间<br/> &nbsp;  job消耗的计算资源 = 最大并发vCore * job运行时间 <br/> &nbsp;  task消耗的计算资源 = task计算时间累计"
                        +
                        "<br/> &nbsp;  driver资源浪费 = app消耗的计算资源 - job消耗的计算资源 <br/> &nbsp;  executor资源浪费 = job消耗的计算资源 - task消耗的计算资源"
                        +
                        "<br/>&nbsp;  当executor资源浪费占比超过阈值%s或者driver资源浪费占比超过阈值%s,即判断为发生CPU资源浪费",
                data.getVars().get("executorThreshold"), data.getVars().get("driverThreshold"));
    }

    @Override
    public String generateItemDesc() {
        return "CPU浪费分析";
    }

    public void addColor(CpuWasteInfo cpuWasteInfo) {
        cpuWasteInfo.getDataCategory().put("waste", new Chart.ChartInfo("浪费", UIUtil.ABNORMAL_COLOR));
        cpuWasteInfo.getDataCategory().put("efficient", new Chart.ChartInfo("有效", UIUtil.NORMAL_COLOR));
    }

}
