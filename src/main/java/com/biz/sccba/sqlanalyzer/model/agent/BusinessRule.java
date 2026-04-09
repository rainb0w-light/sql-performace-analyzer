package com.biz.sccba.sqlanalyzer.model.agent;

/**
 * 业务规则模型
 *
 * 定义字段或表级别的业务规则
 */
public class BusinessRule {

    /**
     * 规则类型
     */
    private RuleType ruleType;

    /**
     * 规则表达式
     * 可以是自然语言描述，也可以是简单的 DSL 表达式
     */
    private String expression;

    /**
     * 规则描述
     */
    private String description;

    // Getters and Setters
    public RuleType getRuleType() { return ruleType; }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * 规则类型枚举
     */
    public enum RuleType {
        /**
         * 依赖规则：字段 A 有值时，字段 B 必须有值
         */
        DEPENDENCY("依赖规则"),

        /**
         * 条件规则：满足某条件时，字段值必须在某范围内
         */
        CONDITION("条件规则"),

        /**
         * 格式规则：字段值必须符合特定格式
         */
        FORMAT("格式规则"),

        /**
         * 一致性规则：多字段之间的值必须一致
         */
        CONSISTENCY("一致性规则"),

        /**
         * 业务逻辑规则：特定的业务逻辑约束
         */
        BUSINESS_LOGIC("业务逻辑规则");

        private final String displayName;

        RuleType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
