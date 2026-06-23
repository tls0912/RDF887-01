package com.czkuo.rdf88701.config.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerInspector implements ApplicationRunner {

    private final ApplicationContext ctx;

    @Override
    public void run(ApplicationArguments args) {
        // 1) 列出 TaskScheduler beans（確認你用的是自訂 pool）
        Map<String, TaskScheduler> schedulers = ctx.getBeansOfType(TaskScheduler.class);
        if (schedulers.isEmpty()) {
            log.warn("[SCHED] 沒有任何 TaskScheduler bean（可能正在使用 Spring 預設單執行緒排程器）");
        }
        schedulers.forEach((name, ts) -> {
            log.info("[SCHED] TaskScheduler bean: {} -> {}", name, ts.getClass().getName());
            if (ts instanceof ThreadPoolTaskScheduler tpts) {
                var ex = tpts.getScheduledExecutor();
                if (ex instanceof ScheduledThreadPoolExecutor stpe) {
                    log.info("[SCHED]  └─ poolSize={} queueSize={} removeOnCancel={}",
                            stpe.getCorePoolSize(), stpe.getQueue().size(), stpe.getRemoveOnCancelPolicy());
                }
            }
        });

        // 2) 列出所有已註冊的 @Scheduled 任務
        //    透過 ScheduledTaskHolder（包含 ScheduledAnnotationBeanPostProcessor）
        Collection<ScheduledTaskHolder> holders = ctx.getBeansOfType(ScheduledTaskHolder.class).values();
        if (holders.isEmpty()) {
            log.warn("[SCHED] 找不到 ScheduledTaskHolder（沒有任何 @Scheduled？或版本不支援）");
            return;
        }

        int idx = 0;
        for (ScheduledTaskHolder holder : holders) {
            Set<ScheduledTask> tasks = holder.getScheduledTasks();
            for (ScheduledTask st : tasks) {
                String kind = "unknown";
                Runnable r = null;

                if (st.getTask() instanceof CronTask ct) {
                    kind = "cron(" + ct.getExpression() + ")";
                    r = ct.getRunnable();
                } else if (st.getTask() instanceof FixedRateTask fr) {
                    kind = "fixedRate(" + fr.getInterval() + "ms, initialDelay=" + fr.getInitialDelay() + "ms)";
                    r = fr.getRunnable();
                } else if (st.getTask() instanceof FixedDelayTask fd) {
                    kind = "fixedDelay(" + fd.getInterval() + "ms, initialDelay=" + fd.getInitialDelay() + "ms)";
                    r = fd.getRunnable();
                } else if (st.getTask() instanceof TriggerTask tt) {
                    kind = "trigger(" + tt.getTrigger() + ")";
                    r = tt.getRunnable();
                }

                String owner = describeRunnable(r);
                log.info("[SCHED] #{} {} -> {}", ++idx, kind, owner);
            }
        }

        if (idx == 0) {
            log.warn("[SCHED] 沒有列出任何排程任務（可能尚未掃描到 @Scheduled，或全在條件排除）");
        }
    }

    /** 嘗試把 Runnable 解析成 類別#方法，失敗就印 toString() 當備援。 */
    private String describeRunnable(Runnable r) {
        if (r == null) return "n/a";
        try {
            if (r instanceof ScheduledMethodRunnable smr) {
                Object target = getTargetObject(smr);
                Method method = getMethodObject(smr);
                String cls = (target != null) ? target.getClass().getName() : "unknown";
                String mth = (method != null) ? method.getName() : "unknown";
                return cls + "#" + mth;
            }
        } catch (Throwable ignore) {
            // fall through
        }
        return r.toString();
    }

    // 兼容不同 Spring 版本：ScheduledMethodRunnable 可能是 getTargetObject()/getTarget()
    private Object getTargetObject(ScheduledMethodRunnable smr) {
        try { return ScheduledMethodRunnable.class.getMethod("getTargetObject").invoke(smr); }
        catch (Exception ignored) { /* try legacy */ }
        try { return ScheduledMethodRunnable.class.getMethod("getTarget").invoke(smr); }
        catch (Exception ignored) { return null; }
    }

    // 兼容不同 Spring 版本：method accessor 名稱一致，但保個 try/catch
    private Method getMethodObject(ScheduledMethodRunnable smr) {
        try { return (Method) ScheduledMethodRunnable.class.getMethod("getMethod").invoke(smr); }
        catch (Exception ignored) { return null; }
    }
}
