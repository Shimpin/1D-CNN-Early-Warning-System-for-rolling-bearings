package org.sxk.dcnn.earlywarning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class DynamicTaskService {

    private TaskScheduler taskScheduler;
    @Autowired
    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    // 使用 ConcurrentHashMap 存储多个任务，Key 为任务唯一标识，Value 为任务句柄
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutureMap = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(DynamicTaskService.class);


    /**
     * 启动或更新一个定时任务
     * @param taskKey 任务唯一标识 (例如: "order_clean_job")
     * @param cronExpression Cron 表达式
     * @param runnable 具体的业务逻辑
     */
    public void startTask(String taskKey, String cronExpression, Runnable runnable) {
        // 1. 如果任务已存在，先停止旧任务（实现热更新）
        stopTask(taskKey);

        try {
            // 2. 包装任务，增加异常捕获，防止单个任务异常影响调度器或其他任务
            Runnable safeRunnable = () -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                log.error("任务 [" + taskKey + "] 执行异常: " + e.getMessage(),e);
                }
            };

            // 3. 注册新任务
            ScheduledFuture<?> future = taskScheduler.schedule(safeRunnable, new CronTrigger(cronExpression));

            // 4. 存入Map
            if (future != null) {
                scheduledFutureMap.put(taskKey, future);
            }
            log.info("任务 [{}] 已启动，Cron: {}", taskKey, cronExpression);
        } catch (Exception e) {
            log.error("任务 [{}] 启动失败: {}", taskKey, e.getMessage(), e);
        }
    }

    /**
     * 停止指定任务
     */
    public void stopTask(String taskKey) {
        ScheduledFuture<?> future = scheduledFutureMap.get(taskKey);
        if (future != null) {
            // cancel(true) 表示尝试中断正在执行的任务
            future.cancel(true);
            scheduledFutureMap.remove(taskKey);
            log.info("任务 [{}] 已停止", taskKey);
        } else {
            log.error("任务 [{}] 不存在或未运行",taskKey);
        }
    }

    /**
     * 检查任务是否正在运行
     */
    public boolean isTaskRunning(String taskKey) {
        ScheduledFuture<?> future = scheduledFutureMap.get(taskKey);
        return future != null && !future.isCancelled() && !future.isDone();
    }

    /**
     * (可选) 停止所有任务，通常在应用关闭时使用
     */
    public void stopAllTasks() {
        scheduledFutureMap.forEach((key, future) ->
            future.cancel(true)
        );
        log.info("所有任务已全部停止");
        scheduledFutureMap.clear();
    }
}
