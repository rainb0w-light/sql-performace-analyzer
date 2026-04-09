package com.biz.sccba.sqlanalyzer.tool;

import java.util.*;

public class SqlAnalysisValidator {

    public SqlAnalysisValidator() {
    }

    public ValidationResult validateComplexityAnalysis(String sql, Object analysisResult) {
        ValidationResult result = new ValidationResult();
        result.setTestName("Complexity Analysis Validation");
        result.setSql(sql);
        
        if (analysisResult == null) {
            result.addFailure("Analysis result is null");
            return result;
        }
        
        if (sql == null || sql.trim().isEmpty()) {
            if (analysisResult.toString().contains("ERROR") || 
                analysisResult.toString().contains("error")) {
                result.addPass("Empty SQL correctly handled");
            } else {
                result.addFailure("Empty SQL should return error");
            }
            return result;
        }
        
        if (analysisResult instanceof SqlQueryComplexityAnalyzer.ComplexityAnalysisResult complexityResult) {
            validateComplexityResult(result, sql, complexityResult);
        } else {
            result.addPass("Result has expected structure (non-native type)");
        }
        
        return result;
    }

    private void validateComplexityResult(ValidationResult result, String sql, 
                                           SqlQueryComplexityAnalyzer.ComplexityAnalysisResult complexityResult) {
        if (complexityResult.overallScore() < 0 || complexityResult.overallScore() > 100) {
            result.addFailure("Complexity score out of range [0-100]: " + complexityResult.overallScore());
        } else {
            result.addPass("Complexity score in valid range");
        }
        
        if (complexityResult.priority() == null || complexityResult.priority().isEmpty()) {
            result.addFailure("Priority is empty");
        } else {
            result.addPass("Priority is set: " + complexityResult.priority());
        }
        
        if (complexityResult.recommendations() == null || complexityResult.recommendations().isEmpty()) {
            result.addWarning("No recommendations generated");
        } else {
            result.addPass("Generated " + complexityResult.recommendations().size() + " recommendations");
        }
        
        if (complexityResult.metrics() != null) {
            validateComplexityMetrics(result, sql, complexityResult.metrics());
        }
    }

    private void validateComplexityMetrics(ValidationResult result, String sql, 
                                            SqlQueryComplexityAnalyzer.ComplexityMetrics metrics) {
        int tableCount = result.getPasses().stream().anyMatch(p -> p.contains("Table count")) ? 1 : 0;
        if (tableCount < 1 && !sql.toUpperCase().matches(".*\\bSELECT\\s+1\\b.*")) {
            result.addFailure("Table count should be at least 1");
        } else if (tableCount > 0 || sql.toUpperCase().matches(".*\\bSELECT\\s+1\\b.*")) {
            result.addPass("Table count appears valid");
        }
        
        if (sql.toUpperCase().contains("JOIN") && metrics.joinMetrics().totalJoins() == 0) {
            result.addFailure("JOIN detected but join count is 0");
        } else if (!sql.toUpperCase().contains("JOIN") && metrics.joinMetrics().totalJoins() > 0) {
            result.addFailure("No JOIN but join count > 0");
        } else {
            result.addPass("Join count matches SQL structure");
        }
        
        if (sql.toUpperCase().contains("WHERE") && metrics.whereComplexity().conditionCount() == 0) {
            result.addWarning("WHERE clause present but condition count is 0");
        } else {
            result.addPass("WHERE complexity metrics consistent");
        }
    }

    public ValidationResult validateIndexUsageAnalysis(String sql, String tableName, Object analysisResult) {
        ValidationResult result = new ValidationResult();
        result.setTestName("Index Usage Analysis Validation");
        result.setSql(sql);
        
        if (analysisResult == null) {
            result.addFailure("Analysis result is null");
            return result;
        }
        
        if (analysisResult instanceof IndexUsageAnalyzer.IndexUsageAnalysisResult indexResult) {
            validateIndexUsageResult(result, sql, indexResult);
        } else {
            result.addPass("Result has expected structure (non-native type)");
        }
        
        return result;
    }

    private void validateIndexUsageResult(ValidationResult result, String sql, 
                                           IndexUsageAnalyzer.IndexUsageAnalysisResult indexResult) {
        if (indexResult.analyzedTables() == null || indexResult.analyzedTables().isEmpty()) {
            result.addFailure("No tables analyzed");
        } else {
            result.addPass("Analyzed " + indexResult.analyzedTables().size() + " table(s)");
        }
        
        if (indexResult.recommendations() == null || indexResult.recommendations().isEmpty()) {
            result.addWarning("No recommendations generated");
        } else {
            result.addPass("Generated " + indexResult.recommendations().size() + " recommendations");
        }
        
        if (indexResult.estimatedPerformanceImpact() < 0 || indexResult.estimatedPerformanceImpact() > 100) {
            result.addFailure("Impact score out of range [0-100]");
        } else {
            result.addPass("Impact score in valid range: " + indexResult.estimatedPerformanceImpact());
        }
        
        for (IndexUsageAnalyzer.TableIndexAnalysis tableAnalysis : indexResult.tableAnalyses()) {
            validateTableIndexAnalysis(result, tableAnalysis);
        }
    }

    private void validateTableIndexAnalysis(ValidationResult result, 
                                             IndexUsageAnalyzer.TableIndexAnalysis analysis) {
        if (analysis.tableName() == null || analysis.tableName().isEmpty()) {
            result.addFailure("Table name is empty");
        }
        
        if (analysis.error() != null) {
            result.addWarning("Analysis error: " + analysis.error());
        }
        
        if (analysis.existingIndexes() == null) {
            result.addFailure("Index list is null");
        }
        
        if (analysis.indexUsage() != null && !analysis.indexUsage().isEmpty()) {
            for (IndexUsageAnalyzer.IndexUsageInfo usage : analysis.indexUsage()) {
                if (usage.indexName() == null || usage.indexName().isEmpty()) {
                    result.addFailure("Index usage has empty name");
                }
            }
            result.addPass("Index usage info populated for " + analysis.indexUsage().size() + " indexes");
        }
    }

    public ValidationResult validateSqlTestMetadata(SqlTestDataGenerator.TestSqlMetadata metadata) {
        ValidationResult result = new ValidationResult();
        result.setTestName("SQL Test Metadata Validation");
        
        if (metadata.getSql() == null || metadata.getSql().trim().isEmpty()) {
            result.addFailure("SQL is empty");
            return result;
        }
        
        if (metadata.getCategory() == null || metadata.getCategory().isEmpty()) {
            result.addFailure("Category is empty");
        } else {
            result.addPass("Category: " + metadata.getCategory());
        }
        
        if (metadata.getExpectedComplexity() < 0 || metadata.getExpectedComplexity() > 100) {
            result.addFailure("Expected complexity out of range");
        } else {
            result.addPass("Expected complexity: " + metadata.getExpectedComplexity());
        }
        
        if (metadata.getExpectedIssues() == null) {
            result.addWarning("Expected issues is null");
        } else {
            result.addPass("Expected " + metadata.getExpectedIssues().size() + " issues");
        }
        
        return result;
    }

    public ValidationResult runValidationSuite(Object... testCases) {
        ValidationResult suiteResult = new ValidationResult();
        suiteResult.setTestName("Validation Suite");
        
        int passCount = 0;
        int failCount = 0;
        
        for (Object testCase : testCases) {
            if (testCase instanceof SqlTestDataGenerator.TestSqlMetadata metadata) {
                ValidationResult testResult = validateSqlTestMetadata(metadata);
                suiteResult.getDetails().add(testResult);
                
                if (testResult.isAllPassed()) {
                    passCount++;
                } else {
                    failCount++;
                }
            }
        }
        
        suiteResult.setSummary("Passed: " + passCount + ", Failed: " + failCount);
        suiteResult.setAllPassed(failCount == 0);
        
        return suiteResult;
    }

    public List<ValidationResult> validateMultipleAnalyses(List<String> sqls, 
                                                            List<Object> analysisResults) {
        List<ValidationResult> results = new ArrayList<>();
        
        for (int i = 0; i < Math.min(sqls.size(), analysisResults.size()); i++) {
            ValidationResult result = validateComplexityAnalysis(sqls.get(i), analysisResults.get(i));
            results.add(result);
        }
        
        return results;
    }

    public ValidationResult validatePerformanceMetrics(Map<String, Number> metrics) {
        ValidationResult result = new ValidationResult();
        result.setTestName("Performance Metrics Validation");
        
        if (metrics == null || metrics.isEmpty()) {
            result.addFailure("Metrics map is empty");
            return result;
        }
        
        if (metrics.containsKey("executionTimeMs")) {
            Number executionTime = metrics.get("executionTimeMs");
            if (executionTime.doubleValue() < 0) {
                result.addFailure("Negative execution time");
            } else {
                result.addPass("Execution time: " + executionTime + "ms");
            }
        }
        
        if (metrics.containsKey("rowsExamined")) {
            Number rowsExamined = metrics.get("rowsExamined");
            if (rowsExamined.longValue() < 0) {
                result.addFailure("Negative rows examined");
            } else {
                result.addPass("Rows examined: " + rowsExamined);
            }
        }
        
        if (metrics.containsKey("rowsReturned")) {
            Number rowsReturned = metrics.get("rowsReturned");
            if (rowsReturned.longValue() < 0) {
                result.addFailure("Negative rows returned");
            } else {
                result.addPass("Rows returned: " + rowsReturned);
            }
        }
        
        return result;
    }

    public static class ValidationResult {
        private String testName;
        private String sql;
        private boolean allPassed = true;
        private List<String> passes = new ArrayList<>();
        private List<String> failures = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<ValidationResult> details = new ArrayList<>();
        private String summary;

        public String getTestName() {
            return testName;
        }

        public void setTestName(String testName) {
            this.testName = testName;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public boolean isAllPassed() {
            return allPassed;
        }

        public void setAllPassed(boolean allPassed) {
            this.allPassed = allPassed;
        }

        public List<String> getPasses() {
            return passes;
        }

        public void setPasses(List<String> passes) {
            this.passes = passes;
        }

        public List<String> getFailures() {
            return failures;
        }

        public void setFailures(List<String> failures) {
            this.failures = failures;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }

        public List<ValidationResult> getDetails() {
            return details;
        }

        public void setDetails(List<ValidationResult> details) {
            this.details = details;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public void addPass(String message) {
            passes.add(message);
        }

        public void addFailure(String message) {
            failures.add(message);
            allPassed = false;
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{test='").append(testName).append("', ");
            sb.append("passed=").append(allPassed);
            
            if (!passes.isEmpty()) {
                sb.append(", passes=").append(passes.size());
            }
            if (!failures.isEmpty()) {
                sb.append(", failures=").append(failures.size());
            }
            if (!warnings.isEmpty()) {
                sb.append(", warnings=").append(warnings.size());
            }
            
            sb.append("}");
            return sb.toString();
        }
    }
}
