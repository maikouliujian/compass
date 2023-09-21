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

package com.oppo.cloud.meta.scheduler;

import com.oppo.cloud.common.service.RedisService;
import com.oppo.cloud.meta.service.ITaskSyncerMetaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;

/**
 * SPARK任务app列表数据同步
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "scheduler.sparkMeta", name = "enable", havingValue = "true")
public class SparkMetaScheduler {


    /**
     * synchronize spark app redis distributed lock
     */
    private static final String LOCK_KEY = "compass:metadata:spark";

    @Resource
    private RedisService redisService;

    @Resource
    private RedisScript<Object> releaseLockScript;

    @Resource(name = "SparkMetaServiceImpl")
    private ITaskSyncerMetaService spark;

    @Scheduled(cron = "${scheduler.sparkMeta.cron}")
    private void run() {
        try {
            syncer();
        } catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    private void syncer() throws Exception {
        String lockValue = UUID.randomUUID().toString();
        // Only one instance of the application can run the synchronization process at the same time.
        //todo 用redis实现分布式锁，确保同一时间只有一个task在调用
        Boolean acquire = redisService.acquireLock(LOCK_KEY, lockValue, 5L);
        if (!acquire) {
            log.info("can not get the lock: {}", LOCK_KEY);
            return;
        }
        try {
            log.info("lockKey: {}, lockValue: {}", LOCK_KEY, lockValue);
            spark.syncer();
        } catch (Exception e) {
            log.error("Exception: ", e);
        } finally {
            Object result = redisService.executeScript(releaseLockScript, Collections.singletonList(LOCK_KEY), lockValue);
            log.info("release {}, result: {}", LOCK_KEY, result);
        }
    }

}
