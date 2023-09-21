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

package com.oppo.cloud.flink.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Data
public class FlinkDiagnosisConfig {
    /**
     * prometheus host
     */
    @Value("${diagnosis.flinkPrometheusHost}")
    private String flinkPrometheusHost = "";
    /**
     * prometheus token
     */
    @Value("${diagnosis.flinkPrometheusToken}")
    private String flinkPrometheusToken = "";
    /**
     * prometheus database
     */
    @Value("${diagnosis.flinkPrometheusDatabase}")
    private String flinkPrometheusDatabase = "";
}
