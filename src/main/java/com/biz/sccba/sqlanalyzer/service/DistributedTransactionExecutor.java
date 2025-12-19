package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.DistributedTransactionTestConfig;
import com.biz.sccba.sqlanalyzer.model.SqlExecutionStep;
import com.biz.sccba.sqlanalyzer.model.TestExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;

/**
 * 分布式事务执行器
 * 使用JDBC原生方式控制事务，通过SQL语句（BEGIN/COMMIT/ROLLBACK）管理事务
 * 配置按thread分组，每个thread有独立的step列表
 * 所有thread完成当前step后，才能进入下一个step（使用CountDownLatch同步）
 */
@Service
public class DistributedTransactionExecutor {

    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    /**
     * 执行测试场景
     * 每个thread在自己的线程中按顺序执行steps
     * 所有thread完成当前step后，才能进入下一个step
     *
     * @param config 测试配置
     */
    public List<TestExecutionResult> execute(DistributedTransactionTestConfig config) {
        DataSource dataSource = dataSourceManagerService.getDataSource("");
        DistributedTransactionTestConfig.Scenario scenario = config.getScenario();
        Map<String, DistributedTransactionTestConfig.ThreadConfig> threads = scenario.getThreads();

        if (threads == null || threads.isEmpty()) {
            throw new IllegalArgumentException("配置无效: threads不能为空");
        }

        // 验证所有thread的step数量是否相同
        int expectedStepCount = -1;
        for (Map.Entry<String, DistributedTransactionTestConfig.ThreadConfig> entry : threads.entrySet()) {
            List<SqlExecutionStep> steps = entry.getValue().getSteps();
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("配置无效: thread[" + entry.getKey() + "].steps不能为空");
            }
            if (expectedStepCount == -1) {
                expectedStepCount = steps.size();
            } else if (steps.size() != expectedStepCount) {
                throw new IllegalArgumentException("配置无效: 所有thread的step数量必须相同，thread[" + 
                    entry.getKey() + "]的step数量为" + steps.size() + "，期望为" + expectedStepCount);
            }
        }

        // 为每个线程创建独立的连接
        Map<String, Connection> connections = new ConcurrentHashMap<>();
        Map<String, Integer> isolationLevels = new HashMap<>();

        // 初始化连接和隔离级别
        try {
            for (String threadId : threads.keySet()) {
                Connection conn = dataSource.getConnection();
                conn.setAutoCommit(false); // 关闭自动提交，使用手动事务控制
                
                // 设置默认隔离级别
                String defaultIsolation = scenario.getDefaultIsolationLevel();
                if (defaultIsolation != null) {
                    int isolation = parseIsolationLevel(defaultIsolation);
                    conn.setTransactionIsolation(isolation);
                    isolationLevels.put(threadId, isolation);
                } else {
                    isolationLevels.put(threadId, Connection.TRANSACTION_READ_COMMITTED);
                }
                
                connections.put(threadId, conn);
            }

            // 创建线程池
            ExecutorService executorService = Executors.newFixedThreadPool(threads.size());
            List<Future<List<TestExecutionResult>>> futures = new ArrayList<>();

            // 记录测试开始时间
            long testStartTime = System.nanoTime();

            // 为每个step创建同步点（CountDownLatch）
            // stepSyncLatches[i] 用于同步第i个step：所有thread完成step i后，才能进入step i+1
            List<CountDownLatch> stepSyncLatches = new ArrayList<>();
            for (int i = 0; i < expectedStepCount; i++) {
                stepSyncLatches.add(new CountDownLatch(threads.size()));
            }

            // 为每个thread提交执行任务
            for (Map.Entry<String, DistributedTransactionTestConfig.ThreadConfig> entry : threads.entrySet()) {
                String threadId = entry.getKey();
                List<SqlExecutionStep> threadSteps = entry.getValue().getSteps();
                Connection conn = connections.get(threadId);

                Future<List<TestExecutionResult>> future = executorService.submit(() -> {
                    List<TestExecutionResult> threadResults = new ArrayList<>();
                    
                    try {
                        // 按顺序执行该thread的所有steps
                        for (int stepIndex = 0; stepIndex < threadSteps.size(); stepIndex++) {
                            SqlExecutionStep step = threadSteps.get(stepIndex);
                            
                            // 如果不是第一个step，等待所有thread完成上一个step
                            if (stepIndex > 0) {
                                CountDownLatch previousStepLatch = stepSyncLatches.get(stepIndex - 1);
                                try {
                                    if (!previousStepLatch.await(60, TimeUnit.SECONDS)) {
                                        throw new InterruptedException("等待其他thread完成step[" + (stepIndex - 1) + "]超时");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    // 创建错误结果
                                    TestExecutionResult errorResult = createErrorResult(
                                        step, threadId, "InterruptedException", 
                                        "等待其他thread完成step[" + (stepIndex - 1) + "]被中断: " + e.getMessage(), 
                                        testStartTime);
                                    threadResults.add(errorResult);
                                    // 即使出错也要释放当前step的latch，避免其他thread死锁
                                    stepSyncLatches.get(stepIndex).countDown();
                                    return threadResults;
                                }
                            }

                            // 如果步骤指定了隔离级别，在BEGIN时设置
                            List<String> sqlList = step.getSqlList();
                            if (step.getIsolationLevel() != null && !sqlList.isEmpty()) {
                                String firstSql = sqlList.get(0).trim();
                                if (firstSql.equalsIgnoreCase("BEGIN")) {
                                    try {
                                        int isolation = parseIsolationLevel(step.getIsolationLevel());
                                        conn.setTransactionIsolation(isolation);
                                        isolationLevels.put(threadId, isolation);
                                    } catch (SQLException e) {
                                        // 记录错误但继续执行
                                        System.err.println("设置隔离级别失败: " + e.getMessage());
                                    }
                                }
                            }

                            // 执行SQL（支持多个SQL）
                            TestExecutionResult result = executeSql(conn, step, threadId, testStartTime);
                            threadResults.add(result);

                            // 通知当前step已完成（释放latch）
                            stepSyncLatches.get(stepIndex).countDown();
                        }
                    } catch (Exception e) {
                        System.err.println("Thread[" + threadId + "]执行异常: " + e.getMessage());
                        e.printStackTrace();
                    }

                    return threadResults;
                });

                futures.add(future);
            }

            // 收集所有结果
            List<TestExecutionResult> allResults = new ArrayList<>();
            for (Future<List<TestExecutionResult>> future : futures) {
                try {
                    List<TestExecutionResult> threadResults = future.get(120, TimeUnit.SECONDS); // 超时120秒
                    allResults.addAll(threadResults);
                } catch (Exception e) {
                    System.err.println("收集结果异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            executorService.shutdown();
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 关闭所有连接
            for (Connection conn : connections.values()) {
                try {
                    if (!conn.isClosed()) {
                        conn.rollback(); // 确保回滚未提交的事务
                        conn.close();
                    }
                } catch (SQLException e) {
                    System.err.println("关闭连接失败: " + e.getMessage());
                }
            }

            // 按thread和step顺序排序结果
            // 先按threadId排序，再按step索引排序
            Map<String, Integer> threadOrder = new HashMap<>();
            int threadIndex = 0;
            for (String threadId : threads.keySet()) {
                threadOrder.put(threadId, threadIndex++);
            }

            // 为每个result计算排序键：threadIndex * 10000 + stepIndex
            Map<String, Integer> stepIndexMap = new HashMap<>();
            for (Map.Entry<String, DistributedTransactionTestConfig.ThreadConfig> entry : threads.entrySet()) {
                List<SqlExecutionStep> steps = entry.getValue().getSteps();
                for (int i = 0; i < steps.size(); i++) {
                    String stepId = steps.get(i).getId();
                    if (stepId != null) {
                        stepIndexMap.put(entry.getKey() + ":" + stepId, i);
                    }
                }
            }

            final Map<String, Integer> finalThreadOrder = threadOrder;
            final Map<String, Integer> finalStepIndexMap = stepIndexMap;
            allResults.sort((r1, r2) -> {
                int threadCompare = Integer.compare(
                    finalThreadOrder.getOrDefault(r1.getThreadId(), Integer.MAX_VALUE),
                    finalThreadOrder.getOrDefault(r2.getThreadId(), Integer.MAX_VALUE)
                );
                if (threadCompare != 0) {
                    return threadCompare;
                }
                int step1Index = finalStepIndexMap.getOrDefault(r1.getThreadId() + ":" + r1.getStepId(), Integer.MAX_VALUE);
                int step2Index = finalStepIndexMap.getOrDefault(r2.getThreadId() + ":" + r2.getStepId(), Integer.MAX_VALUE);
                return Integer.compare(step1Index, step2Index);
            });

            return allResults;

        } catch (SQLException e) {
            throw new RuntimeException("初始化连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建错误结果
     */
    private TestExecutionResult createErrorResult(SqlExecutionStep step, String threadId, 
                                                   String exceptionType, String message, long testStartTime) {
        TestExecutionResult result = new TestExecutionResult();
        result.setStepId(step.getId());
        result.setThreadId(threadId);
        List<String> sqlList = step.getSqlList();
        if (!sqlList.isEmpty()) {
            result.setSql(sqlList.get(0)); // 向后兼容
        }
        result.setStartTimeNanos(System.nanoTime() - testStartTime);
        result.setEndTimeNanos(System.nanoTime() - testStartTime);
        result.setStatus(TestExecutionResult.ExecutionStatus.FAILED);
        TestExecutionResult.ExceptionInfo exceptionInfo = new TestExecutionResult.ExceptionInfo();
        exceptionInfo.setExceptionType(exceptionType);
        exceptionInfo.setMessage(message);
        result.setException(exceptionInfo);
        return result;
    }

    /**
     * 执行SQL语句（支持多个SQL）
     */
    private TestExecutionResult executeSql(Connection conn, SqlExecutionStep step, String threadId, long testStartTime) {
        TestExecutionResult result = new TestExecutionResult();
        result.setStepId(step.getId());
        result.setThreadId(threadId);
        
        List<String> sqlList = step.getSqlList();
        if (sqlList.isEmpty()) {
            result.setStatus(TestExecutionResult.ExecutionStatus.FAILED);
            TestExecutionResult.ExceptionInfo exceptionInfo = new TestExecutionResult.ExceptionInfo();
            exceptionInfo.setExceptionType("IllegalArgumentException");
            exceptionInfo.setMessage("步骤中没有SQL语句");
            result.setException(exceptionInfo);
            return result;
        }

        // 向后兼容：设置第一个SQL
        result.setSql(sqlList.get(0));

        long stepStartTime = System.nanoTime();
        result.setStartTimeNanos(stepStartTime - testStartTime);

        List<TestExecutionResult.SqlExecutionDetail> sqlDetails = new ArrayList<>();
        boolean allSuccess = true;
        SQLException firstException = null;

        // 执行所有SQL语句
        for (String sql : sqlList) {
            TestExecutionResult.SqlExecutionDetail detail = new TestExecutionResult.SqlExecutionDetail();
            detail.setSql(sql);
            
            long sqlStartTime = System.nanoTime();
            detail.setStartTimeNanos(sqlStartTime - testStartTime);

            try (Statement stmt = conn.createStatement()) {
                // 执行SQL
                String trimmedSql = sql.trim();
                boolean hasResultSet = stmt.execute(trimmedSql);

                long sqlEndTime = System.nanoTime();
                detail.setEndTimeNanos(sqlEndTime - testStartTime);
                detail.setDurationMillis((sqlEndTime - sqlStartTime) / 1_000_000);
                detail.setStatus(TestExecutionResult.ExecutionStatus.SUCCESS);

                // 如果是查询语句，可以获取结果集（可选）
                if (hasResultSet) {
                    // 这里可以选择是否处理结果集
                    // 对于测试场景，通常不需要处理结果
                }

            } catch (SQLException e) {
                allSuccess = false;
                if (firstException == null) {
                    firstException = e;
                }

                long sqlEndTime = System.nanoTime();
                detail.setEndTimeNanos(sqlEndTime - testStartTime);
                detail.setDurationMillis((sqlEndTime - sqlStartTime) / 1_000_000);
                detail.setStatus(TestExecutionResult.ExecutionStatus.FAILED);

                TestExecutionResult.ExceptionInfo exceptionInfo = new TestExecutionResult.ExceptionInfo();
                exceptionInfo.setExceptionType(e.getClass().getName());
                exceptionInfo.setMessage(e.getMessage());
                exceptionInfo.setSqlErrorCode(e.getErrorCode());
                exceptionInfo.setSqlState(e.getSQLState());
                
                // 记录堆栈跟踪（可选，用于详细调试）
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                exceptionInfo.setStackTrace(sw.toString());

                detail.setException(exceptionInfo);
            }

            sqlDetails.add(detail);
        }

        long stepEndTime = System.nanoTime();
        result.setEndTimeNanos(stepEndTime - testStartTime);
        result.setDurationMillis((stepEndTime - stepStartTime) / 1_000_000);
        result.setSqlDetails(sqlDetails);

        if (allSuccess) {
            result.setStatus(TestExecutionResult.ExecutionStatus.SUCCESS);
        } else {
            result.setStatus(TestExecutionResult.ExecutionStatus.FAILED);
            if (firstException != null) {
                TestExecutionResult.ExceptionInfo exceptionInfo = new TestExecutionResult.ExceptionInfo();
                exceptionInfo.setExceptionType(firstException.getClass().getName());
                exceptionInfo.setMessage("执行多个SQL时发生错误: " + firstException.getMessage());
                exceptionInfo.setSqlErrorCode(firstException.getErrorCode());
                exceptionInfo.setSqlState(firstException.getSQLState());
                result.setException(exceptionInfo);
            }
        }

        return result;
    }

    /**
     * 解析事务隔离级别字符串为JDBC常量
     */
    private int parseIsolationLevel(String isolationLevel) {
        if (isolationLevel == null) {
            return Connection.TRANSACTION_READ_COMMITTED;
        }

        switch (isolationLevel.toUpperCase()) {
            case "READ_UNCOMMITTED":
                return Connection.TRANSACTION_READ_UNCOMMITTED;
            case "READ_COMMITTED":
                return Connection.TRANSACTION_READ_COMMITTED;
            case "REPEATABLE_READ":
                return Connection.TRANSACTION_REPEATABLE_READ;
            case "SERIALIZABLE":
                return Connection.TRANSACTION_SERIALIZABLE;
            default:
                return Connection.TRANSACTION_READ_COMMITTED;
        }
    }
}
