package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.PromptTemplateDefinition;
import com.biz.sccba.sqlanalyzer.repository.PromptTemplateRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PromptTemplateManagerService {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateManagerService.class);

    @Autowired
    private PromptTemplateRepository repository;

    public static final String TYPE_MYSQL = "MYSQL";
    public static final String TYPE_GOLDENDB = "GOLDENDB";
    public static final String TYPE_TABLE_QUERY_ANALYSIS = "TABLE_QUERY_ANALYSIS";
    public static final String TYPE_SQL_AGENT_DISTRIBUTION = "SQL_AGENT_DISTRIBUTION";
    public static final String TYPE_SQL_AGENT_PLAN_EVALUATION = "SQL_AGENT_PLAN_EVALUATION";
    public static final String TYPE_SQL_RISK_ASSESSMENT = "SQL_RISK_ASSESSMENT";
    public static final String TYPE_SQL_RISK_COMPARISON = "SQL_RISK_COMPARISON";
    public static final String TYPE_SQL_RISK_REFINEMENT = "SQL_RISK_REFINEMENT";
    public static final String TYPE_SQL_PARAMETER_FILLING = "SQL_PARAMETER_FILLING";

    @PostConstruct
    public void initDefaultTemplates() {
        logger.info("检查并初始化默认 Prompt 模板...");
        
        // 检查并初始化 MySQL 模板
        if (repository.findByTemplateType(TYPE_MYSQL).isEmpty()) {
            createDefaultTemplate(TYPE_MYSQL, "MySQL性能分析专家", getDefaultMysqlTemplate());
        }
        
        // 检查并初始化 GoldenDB 模板
        if (repository.findByTemplateType(TYPE_GOLDENDB).isEmpty()) {
            createDefaultTemplate(TYPE_GOLDENDB, "GoldenDB性能分析专家", getDefaultGoldenDbTemplate());
        }
        
        // 检查并初始化表查询分析模板
        if (repository.findByTemplateType(TYPE_TABLE_QUERY_ANALYSIS).isEmpty()) {
            createDefaultTemplate(TYPE_TABLE_QUERY_ANALYSIS, "表查询综合分析专家", getDefaultTableQueryAnalysisTemplate());
        }

        // 检查并初始化 SQL Agent 分布分析模板
        if (repository.findByTemplateType(TYPE_SQL_AGENT_DISTRIBUTION).isEmpty()) {
            createDefaultTemplate(TYPE_SQL_AGENT_DISTRIBUTION, "SQL Agent 数据分布分析专家", getDefaultSqlAgentDistributionTemplate());
        }

        // 检查并初始化 SQL Agent 执行计划评估模板
        if (repository.findByTemplateType(TYPE_SQL_AGENT_PLAN_EVALUATION).isEmpty()) {
            createDefaultTemplate(TYPE_SQL_AGENT_PLAN_EVALUATION, "SQL Agent 执行计划评估专家", getDefaultSqlAgentPlanEvaluationTemplate());
        }

        // 检查并初始化 SQL 风险评估模板
        if (repository.findByTemplateType(TYPE_SQL_RISK_ASSESSMENT).isEmpty()) {
            createDefaultTemplate(TYPE_SQL_RISK_ASSESSMENT, "SQL 风险评估 DBA 专家", getDefaultSqlRiskAssessmentTemplate());
        }

        // 检查并初始化 SQL 风险对比模板
        if (repository.findByTemplateType(TYPE_SQL_RISK_COMPARISON).isEmpty()) {
            createDefaultTemplate(TYPE_SQL_RISK_COMPARISON, "SQL 风险对比分析专家", getDefaultSqlRiskComparisonTemplate());
        }

        // 检查并初始化 SQL 风险修正模板
        if (repository.findByTemplateType(TYPE_SQL_RISK_REFINEMENT).isEmpty()) {
            createDefaultTemplate(TYPE_SQL_RISK_REFINEMENT, "SQL 风险修正专家", getDefaultSqlRiskRefinementTemplate());
        }

        // 检查并初始化 SQL 参数填充模板
        if (repository.findByTemplateType(TYPE_SQL_PARAMETER_FILLING).isEmpty()) {
            createDefaultTemplate(TYPE_SQL_PARAMETER_FILLING, "SQL 参数填充专家", getDefaultSqlParameterFillingTemplate());
        }
    }

    private void createDefaultTemplate(String type, String name, String content) {
        PromptTemplateDefinition def = new PromptTemplateDefinition();
        def.setTemplateType(type);
        def.setTemplateName(name);
        def.setTemplateContent(content);
        def.setDescription("系统默认初始化模板");
        repository.save(def);
    }

    public String getTemplateContent(String type) {
        return repository.findByTemplateType(type)
                .map(PromptTemplateDefinition::getTemplateContent)
                .orElseThrow(() -> new RuntimeException("找不到类型为 " + type + " 的 Prompt 模板"));
    }

    public List<PromptTemplateDefinition> getAllTemplates() {
        return repository.findAll();
    }
    
    public PromptTemplateDefinition getTemplateDefinition(String type) {
        return repository.findByTemplateType(type)
                .orElseThrow(() -> new RuntimeException("找不到类型为 " + type + " 的 Prompt 模板"));
    }

    @Transactional
    public PromptTemplateDefinition updateTemplate(String type, String content) {
        PromptTemplateDefinition def = repository.findByTemplateType(type)
                .orElseThrow(() -> new RuntimeException("找不到类型为 " + type + " 的 Prompt 模板"));
        def.setTemplateContent(content);
        return repository.save(def);
    }

    @Transactional
    public PromptTemplateDefinition createTemplate(String type, String name, String content, String description) {
        // 检查模板类型是否已存在
        if (repository.findByTemplateType(type).isPresent()) {
            throw new RuntimeException("模板类型 " + type + " 已存在");
        }
        
        PromptTemplateDefinition def = new PromptTemplateDefinition();
        def.setTemplateType(type);
        def.setTemplateName(name);
        def.setTemplateContent(content);
        def.setDescription(description != null ? description : "");
        return repository.save(def);
    }

    private String getDefaultMysqlTemplate() {
        return """
            你是一位资深的MySQL InnoDB性能优化专家，拥有10年以上的数据库调优经验。请针对以下SQL查询进行专业的性能分析，并提供详细、可执行的优化建议。
            
            ## SQL语句
            {sql}
            
            ## 执行计划
            {execution_plan}
            
            ## 表结构信息
            {table_structures}
            
            ## 分析要求
            
            请从以下专业角度深入分析SQL性能（重点关注MySQL InnoDB引擎特性）：
            
            1. **执行计划深度分析**
               
               1.1 访问类型（type列）分析：
               - 详细解读访问类型（ALL、index、range、ref、eq_ref、const、system）
               - 评估访问效率，识别性能瓶颈
               - 分析是否可以通过索引优化提升访问类型
               
               1.2 索引使用情况：
               - 分析使用的索引类型（主键索引、唯一索引、普通索引、复合索引）
               - 评估key_len值，判断索引使用长度和选择性
               - 检查是否存在索引未充分利用的情况
               - 分析ref列，评估连接条件的匹配效率
               
               1.3 扫描效率分析：
               - 分析rows列（预估扫描行数）与实际数据量的关系
               - 结合filtered列评估过滤效率（filtered < 100%表示需要额外过滤）
               - 计算实际需要处理的行数（rows × filtered%）
               - 识别是否存在大量无效扫描
               
               1.4 连接算法分析：
               - 识别使用的连接算法（Nested Loop Join、Block Nested Loop、Batched Key Access）
               - 评估连接顺序是否最优
               - 分析是否存在笛卡尔积风险
               
               1.5 Extra列关键信息：
               - Using index：是否使用了覆盖索引
               - Using where：WHERE条件过滤情况
               - Using filesort：文件排序问题
               - Using temporary：临时表使用情况
               - Using join buffer：连接缓冲区使用
               - Impossible WHERE：不可能的WHERE条件
            
            2. **InnoDB引擎特性分析**
               
               2.1 索引结构分析：
               - 评估聚簇索引（主键索引）的使用情况
               - 分析二级索引的选择和设计合理性
               - 评估覆盖索引（Covering Index）的可能性
               - 检查是否存在回表查询（需要访问主键索引）
               
               2.2 InnoDB优化器特性：
               - 索引下推（ICP - Index Condition Pushdown）是否生效
               - 多范围读取（MRR - Multi-Range Read）优化评估
               - 批量键访问（BKA - Batched Key Access）使用情况
               
               2.3 锁机制影响：
               - 评估行锁竞争风险
               - 分析间隙锁（Gap Lock）的影响范围
               - 评估死锁可能性
               - 分析事务隔离级别对性能的影响（MVCC开销）
               
               2.4 存储引擎特性：
               - 评估缓冲池（Buffer Pool）命中率
               - 分析页分裂和页合并的影响
               - 评估自适应哈希索引（AHI）的适用性
            
            3. **性能问题识别**
               
               3.1 扫描问题：
               - 识别全表扫描（type=ALL）及其原因
               - 识别全索引扫描（type=index）及其优化空间
               - 评估扫描行数是否合理
               
               3.2 排序和分组问题：
               - 识别文件排序（Using filesort）及其优化方案
               - 分析临时表使用（Using temporary）的原因
               - 评估GROUP BY和ORDER BY的优化空间
               
               3.3 索引问题：
               - 识别索引未使用的情况及原因
               - 识别索引选择不当的情况
               - 识别索引冗余或缺失的情况
               
               3.4 连接问题：
               - 识别低效的连接方式
               - 识别连接顺序不当的情况
               - 识别子查询可优化为JOIN的情况
            
            4. **优化建议（必须提供具体可执行的方案）**
               
               4.1 索引优化建议：
               - 提供具体的索引创建语句（CREATE INDEX）
               - 说明索引设计理由（最左前缀原则、选择性考虑）
               - 评估索引维护成本
               - 建议删除的冗余索引
               - 复合索引字段顺序建议
               
               4.2 SQL语句重写建议：
               - 提供优化后的SQL语句（前后对比）
               - 说明重写的理由和预期效果
               - 子查询优化为JOIN的示例
               - WHERE条件优化建议
               - LIMIT优化技巧
               
               4.3 表结构优化建议：
               - 字段类型优化建议（减少存储空间）
               - 行格式选择建议（COMPACT、DYNAMIC、COMPRESSED）
               - 分区策略建议（如果适用）
               - 表压缩建议（如果适用）
               
               4.4 查询逻辑优化：
               - 避免SELECT *的建议
               - 分页查询优化（避免深度分页）
               - 批量操作优化建议
               - 查询缓存利用建议
               
               4.5 配置参数调优建议：
               - 相关InnoDB参数调优建议
               - 查询缓存配置建议
               - 连接池配置建议（如果适用）
            
            5. **性能评分与风险评估**
               
               5.1 性能评分：
               - 给出1-10分的性能评分（10分为最优）
               - 详细说明评分理由（基于执行计划、扫描行数、索引使用等）
               - 评估当前查询的瓶颈等级（严重/中等/轻微）
               
               5.2 优化预期：
               - 预估优化后的性能提升（百分比或倍数）
               - 说明优化方案的优先级（高/中/低）
               
               5.3 风险评估：
               - 索引维护成本评估（写入性能影响）
               - 优化方案的风险评估（兼容性、稳定性）
               - 实施建议（分阶段实施、回滚方案）
            
            ## 输出格式要求
            
            请以结构化的Markdown格式输出分析结果，必须包含以下部分：
            
            ### 一、执行计划分析
            - 访问类型分析
            - 索引使用详情
            - 扫描效率评估
            - 连接算法分析
            - Extra信息解读
            
            ### 二、InnoDB特性分析
            - 索引结构评估
            - 优化器特性使用情况
            - 锁机制影响分析
            - 存储引擎特性评估
            
            ### 三、性能问题总结
            - 主要性能瓶颈（按严重程度排序）
            - 问题影响评估
            - 问题根因分析
            
            ### 四、优化建议
            - 索引优化方案（附具体SQL）
            - SQL重写方案（附前后对比）
            - 表结构优化建议
            - 配置调优建议
            
            ### 五、性能评分
            - 当前性能评分：[X]/10
            - 评分理由：详细说明
            - 优化预期：预估提升效果
            - 风险评估：实施注意事项
            
            ## 注意事项
            
            1. 所有优化建议必须具体可执行，避免泛泛而谈
            2. 提供的SQL语句必须语法正确，可直接使用
            3. 考虑生产环境的稳定性和兼容性
            4. 优先考虑高收益、低风险的优化方案
            5. 对于复杂查询，提供分步骤的优化路径
            """;
    }

    private String getDefaultGoldenDbTemplate() {
        return """
            你是一位资深的GoldenDB分布式数据库性能优化专家，拥有10年以上的分布式数据库调优经验。请针对以下SQL查询进行专业的性能分析，并提供详细、可执行的优化建议。
            
            ## SQL语句
            {sql}
            
            ## 执行计划
            {execution_plan}
            
            ## 表结构信息
            {table_structures}
            
            ## 分析要求
            
            请从以下专业角度深入分析SQL性能（重点关注GoldenDB分布式数据库特性）：
            
            1. **分布式查询执行计划分析**
               
               1.1 查询路由分析：
               - 分析查询是否命中分片键（Shard Key），评估路由效率
               - 识别单分片查询 vs 跨分片查询（广播查询）
               - 评估查询下推（Push Down）到分片的可能性
               - 分析是否存在不必要的全分片扫描
               
               1.2 分片策略评估：
               - 评估分片键选择是否合理（均匀性、查询模式匹配）
               - 分析分片数量对查询性能的影响
               - 识别热点分片（Hot Shard）风险
               - 评估分片键在WHERE条件中的使用情况
               
               1.3 跨分片查询分析：
               - 识别跨分片JOIN查询及其性能影响
               - 分析跨分片聚合（GROUP BY、SUM、COUNT等）的开销
               - 评估跨分片排序（ORDER BY）的性能问题
               - 识别跨分片DISTINCT查询的优化空间
               
               1.4 执行计划类型分析：
               - 分析访问类型（ALL、index、range、ref、eq_ref、const、system）
               - 评估每个分片上的执行计划效率
               - 识别分布式执行计划中的性能瓶颈
               - 分析结果集合并（Merge）的开销
            
            2. **GoldenDB分布式特性分析**
               
               2.1 分布式事务分析：
               - 评估XA分布式事务的使用情况
               - 分析两阶段提交（2PC）的开销
               - 识别长事务对分布式锁的影响
               - 评估事务超时和死锁风险
               - 分析本地事务 vs 分布式事务的选择
               
               2.2 读写分离优化：
               - 评估读写分离路由的合理性
               - 分析读延迟（Read Lag）对一致性的影响
               - 识别可路由到从库的查询
               - 评估主从延迟对查询结果的影响
               
               2.3 分布式索引分析：
               - 评估全局索引 vs 本地索引的使用
               - 分析索引在分片上的分布情况
               - 识别跨分片索引查询的性能问题
               - 评估覆盖索引在分布式场景下的效果
               
               2.4 数据路由效率：
               - 分析路由算法的效率（Hash、Range、List等）
               - 评估路由缓存命中率
               - 识别路由计算的开销
               - 分析数据倾斜对路由的影响
            
            3. **分布式性能问题识别**
               
               3.1 跨分片查询问题：
               - 识别不必要的跨分片查询（应优化为单分片查询）
               - 识别跨分片JOIN的性能瓶颈
               - 识别跨分片聚合的性能问题
               - 识别跨分片排序的性能开销
               
               3.2 分片键相关问题：
               - 识别WHERE条件中缺少分片键导致的广播查询
               - 识别分片键选择不当导致的数据倾斜
               - 识别分片键在JOIN条件中的缺失
               - 识别分片键更新导致的数据迁移问题
               
               3.3 分布式事务问题：
               - 识别不必要的分布式事务（可优化为本地事务）
               - 识别长事务导致的锁竞争
               - 识别分布式死锁风险
               - 识别事务超时问题
               
               3.4 数据倾斜问题：
               - 识别热点分片（数据分布不均）
               - 识别查询倾斜（某些分片查询压力大）
               - 识别写入倾斜（某些分片写入压力大）
               - 识别索引倾斜问题
            
            4. **优化建议（必须提供具体可执行的方案）**
               
               4.1 分片策略优化：
               - 提供分片键选择建议（基于查询模式）
               - 建议分片数量调整方案
               - 提供数据重分布（Rebalance）建议
               - 建议分片键字段的选择和组合
               
               4.2 SQL语句重写优化：
               - 提供优化后的SQL语句（前后对比）
               - 在WHERE条件中添加分片键的建议
               - 跨分片查询改写为单分片查询的方案
               - 跨分片JOIN优化为应用层JOIN的建议
               - 子查询下推优化建议
               
               4.3 分布式查询优化：
               - 提供查询下推（Push Down）优化方案
               - 跨分片聚合优化建议（预聚合、物化视图）
               - 跨分片排序优化建议（并行排序、归并排序）
               - 分页查询优化（避免跨分片深度分页）
               
               4.4 索引优化建议：
               - 提供本地索引创建建议（CREATE INDEX）
               - 提供全局索引使用建议（如果支持）
               - 覆盖索引在分布式场景下的设计
               - 复合索引字段顺序建议（考虑分片键）
               
               4.5 事务优化建议：
               - 避免不必要的分布式事务的建议
               - 事务拆分建议（大事务拆分为小事务）
               - 读写分离路由优化建议
               - 最终一致性场景下的优化建议
               
               4.6 表结构优化建议：
               - 分片表设计建议（分片键选择）
               - 非分片表（广播表、单表）使用建议
               - 字段类型优化（减少跨分片传输）
               - 分区策略与分片策略的配合
               
               4.7 配置参数调优建议：
               - GoldenDB分布式相关参数调优
               - 连接池配置建议（考虑分片数量）
               - 超时参数配置建议
               - 路由缓存配置建议
            
            5. **性能评分与风险评估**
               
               5.1 性能评分：
               - 给出1-10分的性能评分（10分为最优）
               - 详细说明评分理由（基于执行计划、分片路由、跨分片开销等）
               - 评估当前查询的瓶颈等级（严重/中等/轻微）
               - 区分单分片和跨分片场景的评分
               
               5.2 优化预期：
               - 预估优化后的性能提升（百分比或倍数）
               - 说明优化方案的优先级（高/中/低）
               - 评估从跨分片查询优化为单分片查询的收益
               - 评估读写分离带来的性能提升
               
               5.3 风险评估：
               - 分片键变更的风险评估（数据迁移成本）
               - 分布式事务优化的风险评估（一致性影响）
               - 数据重分布的风险评估（业务影响）
               - 实施建议（分阶段实施、灰度发布、回滚方案）
               
               5.4 扩展性评估：
               - 评估查询在数据量增长下的性能表现
               - 评估分片扩展对查询性能的影响
               - 评估读写分离扩展的收益
               - 提供容量规划建议
            
            ## 输出格式要求
            
            请以结构化的Markdown格式输出分析结果，必须包含以下部分：
            
            ### 一、分布式查询执行计划分析
            - 查询路由分析（单分片/跨分片）
            - 分片策略评估
            - 跨分片查询开销分析
            - 执行计划类型和效率评估
            
            ### 二、GoldenDB分布式特性分析
            - 分布式事务使用情况
            - 读写分离路由分析
            - 分布式索引评估
            - 数据路由效率分析
            
            ### 三、分布式性能问题总结
            - 主要性能瓶颈（按严重程度排序）
            - 跨分片查询问题识别
            - 分片键相关问题
            - 数据倾斜问题
            
            ### 四、优化建议
            - 分片策略优化方案
            - SQL重写方案（附前后对比，重点关注分片键）
            - 分布式查询优化方案
            - 索引优化方案（附具体SQL）
            - 事务优化建议
            - 表结构优化建议
            - 配置调优建议
            
            ### 五、性能评分
            - 当前性能评分：[X]/10
            - 评分理由：详细说明（区分单分片和跨分片场景）
            - 优化预期：预估提升效果
            - 风险评估：实施注意事项
            - 扩展性评估：容量规划建议
            
            ## 注意事项
            
            1. 所有优化建议必须具体可执行，避免泛泛而谈
            2. 提供的SQL语句必须语法正确，可直接使用
            3. 优先考虑将跨分片查询优化为单分片查询
            4. 考虑分布式事务的一致性和性能平衡
            5. 评估分片键变更的数据迁移成本
            6. 考虑生产环境的稳定性和兼容性
            7. 优先考虑高收益、低风险的优化方案
            8. 对于复杂查询，提供分步骤的优化路径
            9. 特别关注数据倾斜和热点分片问题
            10. 考虑读写分离对一致性的影响
            """;
    }

    private String getDefaultTableQueryAnalysisTemplate() {
        return """
            你是一位资深的数据库性能优化专家，拥有10年以上的数据库调优经验。请针对以下表的所有查询进行综合性能分析，并提供详细、可执行的优化建议。
            
            ## 表名
            {table_name}
            
            ## 表结构信息
            {table_structure}
            
            ## 所有SQL查询语句
            {all_sqls}
            
            ## 所有执行计划
            {all_execution_plans}
            
            ## 分析要求
            
            请从以下专业角度深入分析该表的所有查询性能：
            
            1. **查询综合分析**
               
               1.1 查询模式分析：
               - 分析所有查询的访问模式（全表扫描、索引扫描、范围扫描等）
               - 识别高频查询和低频查询
               - 分析查询的WHERE条件模式（哪些列经常被查询）
               - 识别查询的排序和分组模式
               
               1.2 索引使用情况分析：
               - 分析哪些查询使用了索引，哪些没有
               - 评估现有索引的利用率
               - 识别索引冗余或缺失的情况
               - 分析复合索引的设计是否合理
               
               1.3 执行计划分析：
               - 分析每个查询的执行计划效率
               - 识别慢查询及其原因
               - 评估扫描行数和实际返回行数的比例
               - 识别全表扫描的查询
               
               1.4 查询性能评估：
               - 评估每个查询的性能等级（优秀/良好/需要优化/严重问题）
               - 识别性能瓶颈
               - 分析查询之间的性能差异
               
            2. **索引优化建议**
               
               2.1 缺失索引建议：
               - 基于查询模式，建议创建哪些索引
               - 提供具体的索引创建语句（CREATE INDEX）
               - 说明索引设计理由（最左前缀原则、选择性考虑）
               - 评估索引维护成本
               
               2.2 索引优化建议：
               - 建议删除的冗余索引
               - 建议合并的索引（多个单列索引合并为复合索引）
               - 复合索引字段顺序优化建议
               - 覆盖索引设计建议
               
               2.3 索引使用优化：
               - 识别可以优化索引使用的查询
               - 提供查询重写建议以更好地利用索引
               
            3. **SQL语句优化建议**
               
               3.1 查询重写建议：
               - 提供优化后的SQL语句（前后对比）
               - 说明重写的理由和预期效果
               - 子查询优化为JOIN的示例
               - WHERE条件优化建议
               
               3.2 查询逻辑优化：
               - 避免SELECT *的建议
               - 分页查询优化（避免深度分页）
               - 批量操作优化建议
               - 查询合并建议（多个查询合并为一个）
               
               3.3 查询模式优化：
               - 识别可以优化的查询模式
               - 提供查询缓存利用建议
               - 提供查询结果预计算建议
               
            4. **表结构优化建议**
               
               4.1 字段类型优化：
               - 字段类型优化建议（减少存储空间）
               - 字段长度优化建议
               
               4.2 表设计优化：
               - 行格式选择建议（COMPACT、DYNAMIC、COMPRESSED）
               - 分区策略建议（如果适用）
               - 表压缩建议（如果适用）
               
            5. **综合优化方案**
               
               5.1 优化优先级：
               - 按影响范围和收益排序优化建议
               - 识别高优先级优化项（影响多个查询的优化）
               - 识别低风险高收益的优化项
               
               5.2 优化实施建议：
               - 提供分阶段实施建议
               - 评估优化方案的兼容性风险
               - 提供回滚方案
               
               5.3 性能提升预期：
               - 预估每个优化方案的性能提升
               - 评估整体性能提升效果
               
            ## 输出格式要求
            
            请以结构化的Markdown格式输出分析结果，必须包含以下部分：
            
            ### 一、查询综合分析
            - 查询模式分析
            - 索引使用情况分析
            - 执行计划分析
            - 查询性能评估
            
            ### 二、索引优化建议
            - 缺失索引建议（附具体SQL）
            - 索引优化建议（删除冗余、合并索引等）
            - 索引使用优化建议
            
            ### 三、SQL语句优化建议
            - 查询重写方案（附前后对比）
            - 查询逻辑优化建议
            - 查询模式优化建议
            
            ### 四、表结构优化建议
            - 字段类型优化建议
            - 表设计优化建议
            
            ### 五、综合优化方案
            - 优化优先级排序
            - 优化实施建议
            - 性能提升预期
            
            ## 注意事项
            
            1. 所有优化建议必须具体可执行，避免泛泛而谈
            2. 提供的SQL语句必须语法正确，可直接使用
            3. 优先考虑影响多个查询的优化方案（如创建复合索引）
            4. 考虑生产环境的稳定性和兼容性
            5. 优先考虑高收益、低风险的优化方案
            6. 对于复杂优化，提供分步骤的优化路径
            7. 分析时要综合考虑所有查询，避免优化一个查询而影响其他查询
            """;
    }

    private String getDefaultSqlAgentDistributionTemplate() {
        return """
            你是一位资深的MySQL数据库专家。请根据以下提供的表统计信息和直方图数据，解读数据分布，并分析特定SQL在当前数据分布下的执行效率，特别是范围查询。
            
            ## SQL语句（带参数占位符）
            {sql}
            
            ## 表统计信息
            {table_statistics}
            
            ## 任务要求：
            1. **解读数据分布**：根据直方图（Histogram）和统计信息，详细描述相关列的数据分布特征（如：数据倾斜、值的范围、高频值等）。
            2. **分析预测效率**：分析SQL中WHERE条件涉及的列。在当前数据分布下，如果该SQL执行，其效率如何？特别是如果是范围查询（RANGE），预估其扫描行数和索引效率。
            3. **识别瓶颈**：是否可能出现全表扫描？数据分布是否会导致现有的索引失效？
            
            ## 输出格式：
            请直接给出分析内容，使用Markdown格式。
            """;
    }

    private String getDefaultSqlAgentPlanEvaluationTemplate() {
        return """
            你是一位MySQL性能调优专家。请根据以下实例化的SQL及其对应的EXPLAIN执行计划，判定执行效率并评估添加索引的可行性。
            
            ## 实例化SQL
            {instantiated_sql}
            
            ## 执行计划 (EXPLAIN)
            {execution_plan}
            
            ## 任务要求：
            1. **判定执行计划效率**：分析访问类型（type）、扫描行数（rows）、使用的索引（key）等关键指标。
            2. **评估性能瓶颈**：是否使用了文件排序（filesort）、临时表（temporary）？是否为全表扫描？
            3. **索引可行性建议**：
               - 如果当前没有使用索引或索引效率低下，请明确建议应该在哪些列上添加索引。
               - 考虑复合索引的最佳顺序。
               - 分析添加索引对写入性能的潜在影响。
            
            ## 输出格式：
            请提供专业的分析报告，使用Markdown格式。包含：[核心瓶颈]、[执行效率判定]、[索引优化建议]。
            """;
    }

    private String getDefaultSqlRiskAssessmentTemplate() {
        return """
            你是一位拥有20年经验的资深MySQL DBA专家。请根据以下SQL语句、表结构信息（包括索引）和列的直方图统计数据，预测该SQL的执行风险和性能表现。
            
            ## SQL语句
            {sql}
            
            ## 表结构和索引信息
            {table_structure}
            
            ## 列直方图统计数据
            {histogram_data}
            
            ## 任务要求：
            
            请基于表结构（包括现有索引）、直方图数据分析并预测以下内容：
            
            1. **风险等级评估**：
               - **重点关注表中已有的索引**，判断查询是否能有效利用这些索引
               - 综合考虑数据分布、预期扫描行数、索引使用情况，评估风险等级
               - 风险等级必须为以下之一：LOW（低风险）、MEDIUM（中等风险）、HIGH（高风险）、CRITICAL（严重风险）
               - 扫描行数 < 100 且使用索引 → LOW
               - 扫描行数 100-1000 且使用索引 → MEDIUM
               - 扫描行数 > 1000 或未使用索引 → HIGH
               - 全表扫描且数据量大 → CRITICAL
            
            2. **性能指标预测**：
               - **estimatedRowsExamined**: 预估需要扫描的行数（基于表结构、索引信息、直方图分布和WHERE条件）
               - **expectedIndexUsage**: 预期是否会使用索引（true/false，**请仔细检查表中已有的索引**）
               - **expectedIndexName**: 预期使用的索引名称（**必须从表结构中的实际索引中选择，如果没有合适的索引则为null**）
               - **expectedAccessType**: 预期的访问类型（ALL, index, range, ref, eq_ref, const，**基于索引情况判断**）
               - **estimatedQueryCost**: 预估的查询成本（1-10000）
            
            3. **测试参数建议**：
               - 基于直方图数据，建议具体的测试参数值
               - 参数应该覆盖典型场景（如：最小值、最大值、中位数、高频值）
               - 格式：{"param_name": "value"} 或 {"column_name": "value"}
            
            4. **预测理由**：
               - **详细说明为什么选择或不选择某个索引**
               - 基于表结构、现有索引和直方图的数据分布特征进行分析
               - 说明WHERE条件对索引选择和数据分布的影响
               - 如果有索引但预测不会使用，必须说明原因
            
            5. **优化建议**：
               - 提供至少3条具体的优化建议
               - 每条建议应该可执行且有针对性
               - **如果现有索引不合适，建议创建新的索引或修改现有索引**
            
            ## 输出格式（必须严格遵守JSON格式）：
            
            请以JSON格式输出，必须包含以下字段：
            
            JSON结构示例：
            - riskLevel: 字符串，值为 LOW, MEDIUM, HIGH, CRITICAL 之一
            - estimatedRowsExamined: 数字，预估扫描行数
            - expectedIndexUsage: 布尔值，true 或 false
            - expectedIndexName: 字符串，索引名称或 null
            - expectedAccessType: 字符串，ALL, index, range, ref, eq_ref, const 之一
            - estimatedQueryCost: 数字，查询成本
            - suggestedParameters: 对象，键值对表示参数名和建议值
            - reasoning: 字符串，详细的预测理由
            - recommendations: 数组，包含优化建议字符串
            
            **重要提示**：
            - 必须返回有效的JSON格式，不要包含其他文本
            - 所有字段都必须填写，不能省略
            - riskLevel 必须是四个值之一：LOW, MEDIUM, HIGH, CRITICAL
            - expectedAccessType 必须是标准的MySQL访问类型
            """;
    }

    private String getDefaultSqlRiskComparisonTemplate() {
        return """
            你是一位资深的MySQL DBA专家。请对比LLM预测的SQL执行情况与实际EXPLAIN结果，判断是否需要修正预测。
            
            ## 预测结果
            {prediction}
            
            ## 实际EXPLAIN结果
            {actual_explain}
            
            ## 任务要求：
            
            请对比以下关键指标：
            
            1. **扫描行数对比**：
               - 预测的 estimatedRowsExamined vs 实际的 rows_examined_per_scan
               - 如果偏差超过50%或偏差绝对值超过1000行，视为需要修正
            
            2. **索引使用对比**：
               - 预测的 expectedIndexUsage vs 实际是否使用索引（key字段）
               - 如果预测使用索引但实际未使用，或相反，视为需要修正
            
            3. **访问类型对比**：
               - 预测的 expectedAccessType vs 实际的 access_type
               - 如果预测为高效访问（ref, eq_ref, const）但实际为低效访问（ALL, index），视为需要修正
            
            4. **风险等级评估**：
               - 如果实际扫描行数显示为全表扫描（type=ALL）但预测为低风险，视为需要修正
               - 如果实际使用了索引但预测为高风险，可能需要修正
            
            ## 输出格式（必须严格遵守JSON格式）：
            
            请以JSON格式输出判断结果：
            
            JSON结构说明：
            - needsRefinement: 布尔值，true 表示需要修正，false 表示不需要
            - deviationSeverity: 字符串，NONE, MINOR, MODERATE, SEVERE 之一
            - deviations: 数组，每个元素包含 metric（指标名称）, predicted（预测值）, actual（实际值）, severity（严重程度）
            - reason: 字符串，是否需要修正的理由
            
            **重要提示**：
            - 必须返回有效的JSON格式
            - needsRefinement 为 true 表示需要修正，false 表示不需要
            - deviationSeverity 必须是：NONE, MINOR, MODERATE, SEVERE 之一
            """;
    }

    private String getDefaultSqlRiskRefinementTemplate() {
        return """
            你是一位资深的MySQL DBA专家。基于原始预测和实际EXPLAIN结果的差异，请修正SQL风险评估。
            
            ## 原始预测
            {original_prediction}
            
            ## 实际EXPLAIN结果
            {actual_explain}
            
            ## 偏差说明
            {deviation_details}
            
            ## 列直方图统计数据（参考）
            {histogram_data}
            
            ## 任务要求：
            
            请基于实际执行计划修正预测，重点关注：
            
            1. **风险等级修正**：
               - 根据实际的访问类型和扫描行数重新评估风险等级
               - 必须使用：LOW, MEDIUM, HIGH, CRITICAL 之一
            
            2. **性能指标修正**：
               - 使用实际EXPLAIN的数据更新预测值
               - estimatedRowsExamined 应接近实际的 rows_examined_per_scan
               - expectedIndexUsage 应匹配实际是否使用索引
               - expectedAccessType 应匹配实际的 access_type
            
            3. **原因分析**：
               - 解释为什么原始预测与实际结果有偏差
               - 分析直方图数据与实际执行计划的关系
               - 说明修正的依据
            
            4. **优化建议更新**：
               - 基于实际执行计划提供更准确的优化建议
               - 建议应该针对实际发现的问题
            
            ## 输出格式（必须严格遵守JSON格式）：
            
            JSON结构说明：
            - riskLevel: 字符串，LOW, MEDIUM, HIGH, CRITICAL 之一
            - estimatedRowsExamined: 数字
            - expectedIndexUsage: 布尔值
            - expectedIndexName: 字符串或null
            - expectedAccessType: 字符串
            - estimatedQueryCost: 数字
            - suggestedParameters: 对象，参数键值对
            - reasoning: 字符串，修正后的理由，说明为什么与原始预测不同
            - recommendations: 数组，基于实际执行计划的优化建议
            
            **重要提示**：
            - 必须返回有效的JSON格式
            - 所有字段格式与原始预测相同
            - reasoning 字段应该解释修正的原因
            - 优先使用实际EXPLAIN的数据而不是预测数据
            """;
    }

    private String getDefaultSqlParameterFillingTemplate() {
        return """
            你是一位资深的 SQL 测试专家和数据库性能分析师。请根据以下 SQL 模板、表结构信息（包括索引）和列的直方图数据，生成多个测试场景的 SQL 语句。
            
            ## SQL 模板
            {sql}
            
            ## 表结构和索引信息
            {table_structure}
            
            ## 列直方图数据
            {histogram_data}
            
            ## 任务要求
            
            请分析 SQL 模板和直方图数据，生成 3-5 个不同测试场景的 SQL 语句：
            
            ### 1. 识别 SQL 占位符
            - 识别 SQL 中的占位符：? （问号）、:paramName （冒号参数）、#{paramName} （MyBatis 参数）
            - 根据 WHERE 条件和直方图数据，推断每个占位符对应的列名
            - 如果无法确定对应关系，按照出现顺序匹配
            
            ### 2. 场景设计原则
            
            基于直方图数据的分布特征，为以下场景生成合适的参数值：
            
            **场景 1: 最小值场景（边界测试）**
            - 使用列的最小值或接近最小值
            - 目的：测试下边界的执行计划
            - 适用于测试范围查询的起始边界
            
            **场景 2: 最大值场景（边界测试）**
            - 使用列的最大值或接近最大值
            - 目的：测试上边界的执行计划
            - 适用于测试范围查询的结束边界
            
            **场景 3: 典型值场景（常规测试）**
            - 使用中位数或高频值（基于直方图的桶分布）
            - 目的：测试最常见数据的执行计划
            - 适用于评估日常查询性能
            
            **场景 4: 稀疏值场景（特殊情况）**（可选）
            - 使用稀疏分布区域的值
            - 目的：测试索引选择性高的情况
            - 适用于评估选择性查询
            
            **场景 5: 边界值场景（索引失效测试）**（可选）
            - 使用可能触发全表扫描的参数值
            - 例如：范围过大、选择性低的值
            - 目的：识别潜在的性能风险
            
            ### 3. 参数选择指导
            
            - **数值类型**：直接使用直方图中的 minValue、maxValue、中位数或采样值
            - **字符串类型**：使用采样值中的实际字符串
            - **日期时间**：根据 minValue 和 maxValue 推算合理的日期
            - **多个参数**：确保参数之间的逻辑关系合理（如 start_date < end_date）
            
            ### 4. SQL 生成规则
            
            - 将占位符替换为实际的参数值
            - 数值类型：直接替换（如 age > 25）
            - 字符串类型：加单引号（如 name = 'Alice'）
            - NULL 值：使用 NULL 关键字
            - 确保生成的 SQL 语法正确，可以直接执行
            
            ## 输出格式（必须严格遵守 JSON 格式）
            
            请以 JSON 格式输出，JSON 结构说明：
            - originalSql: 原始 SQL 模板字符串
            - scenarios: 场景数组，每个场景包含：
              - scenarioName: 场景名称（如"最小值场景"）
              - filledSql: 填充好的完整 SQL 语句
              - parameters: 参数对象，键为参数名，值为参数值
              - description: 场景描述，说明为什么选择这些参数
            - reasoning: 整体推理过程，解释参数选择的依据
            
            示例说明：
            对于 SQL "SELECT * FROM users WHERE age > ? AND city = ?"
            可以生成三个场景：
            1. 最小值场景：age=18, city='Beijing'
            2. 最大值场景：age=65, city='Shanghai'  
            3. 典型值场景：age=35, city='Guangzhou'
            
            ## 重要提示
            
            1. **必须返回有效的 JSON 格式**，不要包含其他文本
            2. **scenarios 数组必须包含 3-5 个场景**
            3. **每个场景的 filledSql 必须是完整的、可执行的 SQL 语句**
            4. **parameters 对象的 key 应该是列名或参数名**
            5. **如果 SQL 没有占位符（已经是完整 SQL），也要生成场景，通过修改 WHERE 条件值来创建不同场景**
            6. **确保参数值的类型正确**：数字用数字，字符串用字符串，不要全部用字符串
            7. **reasoning 字段要解释参数选择的依据和测试目的**
            
            ## 特殊情况处理
            
            - 如果没有直方图数据：使用合理的默认值，并在 reasoning 中说明
            - 如果 SQL 没有占位符：基于 WHERE 条件创建不同的测试场景
            - 如果直方图数据不完整：使用已有的信息推断合理的参数值
            - 如果无法生成多个场景：至少生成一个典型场景
            """;
    }
}

