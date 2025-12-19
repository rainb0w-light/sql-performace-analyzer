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

    @PostConstruct
    public void initDefaultTemplates() {
        if (repository.count() == 0) {
            logger.info("初始化默认 Prompt 模板...");
            
            // 初始化 MySQL 模板
            createDefaultTemplate(TYPE_MYSQL, "MySQL性能分析专家", getDefaultMysqlTemplate());
            
            // 初始化 GoldenDB 模板
            createDefaultTemplate(TYPE_GOLDENDB, "GoldenDB性能分析专家", getDefaultGoldenDbTemplate());
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
}

