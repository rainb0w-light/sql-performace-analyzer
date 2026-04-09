package com.biz.sccba.sqlanalyzer.memory;

import com.biz.sccba.sqlanalyzer.model.agent.BusinessSemantics;
import com.biz.sccba.sqlanalyzer.model.agent.BusinessSemantics.BusinessDomain;
import com.biz.sccba.sqlanalyzer.model.agent.BusinessSemantics.UpdateSource;
import com.biz.sccba.sqlanalyzer.model.agent.FieldSemantics;
import com.biz.sccba.sqlanalyzer.model.agent.ValueConstraint;
import com.biz.sccba.sqlanalyzer.model.agent.BusinessRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 业务语义记忆服务单元测试
 */
class BusinessSemanticsMemoryServiceTest {

    private BusinessSemanticsMemoryService service;

    @BeforeEach
    void setUp() {
        service = new BusinessSemanticsMemoryService();
    }

    @Test
    @DisplayName("测试存储和获取业务语义")
    void testStoreAndGetSemantics() {
        // 创建测试数据
        BusinessSemantics semantics = createTestSemantics();

        // 存储
        service.storeSemantics("customer", semantics);

        // 获取
        BusinessSemantics retrieved = service.getSemantics("customer");

        // 验证
        assertNotNull(retrieved);
        assertEquals("customer", retrieved.getTableName());
        assertEquals(BusinessDomain.CUSTOMER, retrieved.getBusinessDomain());
        assertEquals("客户信息表", retrieved.getDescription());
    }

    @Test
    @DisplayName("测试大小写不敏感")
    void testCaseInsensitive() {
        BusinessSemantics semantics = createTestSemantics();
        service.storeSemantics("CUSTOMER", semantics);

        // 验证不同大小写都能获取
        assertNotNull(service.getSemantics("customer"));
        assertNotNull(service.getSemantics("Customer"));
        assertNotNull(service.getSemantics("CUSTOMER"));
    }

    @Test
    @DisplayName("测试检查是否存在")
    void testHasSemantics() {
        assertFalse(service.hasSemantics("customer"));

        service.storeSemantics("customer", createTestSemantics());

        assertTrue(service.hasSemantics("customer"));
    }

    @Test
    @DisplayName("测试删除业务语义")
    void testRemoveSemantics() {
        service.storeSemantics("customer", createTestSemantics());
        assertTrue(service.hasSemantics("customer"));

        service.removeSemantics("customer");

        assertFalse(service.hasSemantics("customer"));
        assertNull(service.getSemantics("customer"));
    }

    @Test
    @DisplayName("测试获取所有表名")
    void testGetAllTableNames() {
        assertEquals(0, service.getAllTableNames().size());

        service.storeSemantics("customer", createTestSemantics());
        service.storeSemantics("account", createTestSemantics());
        service.storeSemantics("transaction", createTestSemantics());

        List<String> tableNames = service.getAllTableNames();
        assertEquals(3, tableNames.size());
        assertTrue(tableNames.contains("customer"));
        assertTrue(tableNames.contains("account"));
        assertTrue(tableNames.contains("transaction"));
    }

    @Test
    @DisplayName("测试获取所有业务语义")
    void testGetAllSemantics() {
        Map<String, BusinessSemantics> allSemantics = service.getAllSemantics();
        assertTrue(allSemantics.isEmpty());

        service.storeSemantics("customer", createTestSemantics());
        service.storeSemantics("account", createTestSemantics());

        allSemantics = service.getAllSemantics();
        assertEquals(2, allSemantics.size());
        assertTrue(allSemantics.containsKey("customer"));
        assertTrue(allSemantics.containsKey("account"));
    }

    @Test
    @DisplayName("测试清空所有业务语义")
    void testClearAll() {
        service.storeSemantics("customer", createTestSemantics());
        service.storeSemantics("account", createTestSemantics());

        service.clearAll();

        assertTrue(service.getAllTableNames().isEmpty());
        assertTrue(service.getAllSemantics().isEmpty());
    }

    @Test
    @DisplayName("测试批量存储")
    void testStoreAll() {
        Map<String, BusinessSemantics> semanticsMap = Map.of(
            "customer", createTestSemantics(),
            "account", createTestSemantics(),
            "transaction", createTestSemantics()
        );

        service.storeAll(semanticsMap);

        assertEquals(3, service.getAllTableNames().size());
        assertTrue(service.hasSemantics("customer"));
        assertTrue(service.hasSemantics("account"));
        assertTrue(service.hasSemantics("transaction"));
    }

    @Test
    @DisplayName("测试导出为 JSON")
    void testExportToJson() {
        service.storeSemantics("customer", createTestSemantics());

        String json = service.exportToJson();

        assertNotNull(json);
        assertTrue(json.contains("customer"));
        assertTrue(json.contains("客户信息表"));
    }

    @Test
    @DisplayName("测试从 JSON 导入")
    void testImportFromJson() {
        // 先存储数据
        service.storeSemantics("customer", createTestSemantics());

        // 导出
        String json = service.exportToJson();

        // 清空
        service.clearAll();
        assertTrue(service.getAllTableNames().isEmpty());

        // 导入
        service.importFromJson(json);

        // 验证
        assertEquals(1, service.getAllTableNames().size());
        assertTrue(service.hasSemantics("customer"));
    }

    @Test
    @DisplayName("测试获取不存在的表返回 null")
    void testGetNonExistentTable() {
        BusinessSemantics result = service.getSemantics("non_existent_table");
        assertNull(result);
    }

    @Test
    @DisplayName("测试更新时间自动设置")
    void testUpdateTimeAutoSet() {
        BusinessSemantics semantics = createTestSemantics();
        assertNull(semantics.getLastUpdatedAt());

        service.storeSemantics("customer", semantics);

        BusinessSemantics retrieved = service.getSemantics("customer");
        assertNotNull(retrieved.getLastUpdatedAt());
    }

    @Test
    @DisplayName("测试更新来源默认值")
    void testUpdateSourceDefault() {
        BusinessSemantics semantics = createTestSemantics();
        assertNull(semantics.getUpdatedBy());

        service.storeSemantics("customer", semantics);

        BusinessSemantics retrieved = service.getSemantics("customer");
        assertNotNull(retrieved.getUpdatedBy());
        assertEquals(UpdateSource.MANUAL, retrieved.getUpdatedBy());
    }

    // ========== 辅助方法 ==========

    private BusinessSemantics createTestSemantics() {
        BusinessSemantics semantics = new BusinessSemantics();
        semantics.setTableName("customer");
        semantics.setBusinessDomain(BusinessDomain.CUSTOMER);
        semantics.setDescription("客户信息表");
        semantics.setSensitiveFields(List.of("id_no", "mobile"));
        semantics.setConstraints(Map.of("unique_cust_no", "客户号全局唯一"));

        // 添加字段语义
        FieldSemantics nameField = new FieldSemantics();
        nameField.setFieldName("name");
        nameField.setBusinessMeaning("客户姓名");
        semantics.addFieldSemantics("name", nameField);

        return semantics;
    }
}
