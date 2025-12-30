package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.MapperParameter;
import com.biz.sccba.sqlanalyzer.model.ParseResult;
import com.biz.sccba.sqlanalyzer.service.MyBatisConfigurationParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis相关API控制器
 */
@RestController
@RequestMapping("/api/mybatis")
public class MyBatisController {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisController.class);

    @Autowired
    private MyBatisConfigurationParserService myBatisConfigurationParserService;

    /**
     * 上传并解析MyBatis Mapper XML文件
     * POST /api/mybatis/upload
     * 
     * 请求参数：
     * - xmlContent: XML内容（必需）
     * - mapperNamespace: Mapper命名空间（必需）
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMapperXml(@RequestBody Map<String, Object> request) {
        try {
            String xmlContent = (String) request.get("xmlContent");
            String mapperNamespace = (String) request.get("mapperNamespace");

            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("xmlContent不能为空"));
            }

            if (mapperNamespace == null || mapperNamespace.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("mapperNamespace不能为空"));
            }

            logger.info("收到MyBatis Mapper XML上传请求: namespace={}", mapperNamespace);

            // 使用MyBatis内置解析器解析XML
            ParseResult result = myBatisConfigurationParserService.parseMapperXml(xmlContent, mapperNamespace);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mapperNamespace", result.getMapperNamespace());
            response.put("queryCount", result.getQueryCount());
            response.put("message", "成功解析 " + result.getQueryCount() + " 个SQL查询");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("解析MyBatis Mapper XML失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("解析失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有Mapper命名空间
     * GET /api/mybatis/namespaces
     */
    @GetMapping("/namespaces")
    public ResponseEntity<?> getAllNamespaces() {
        try {
            logger.info("获取所有Mapper命名空间");

            List<String> namespaces = myBatisConfigurationParserService.getAllNamespaces();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", namespaces.size());
            response.put("namespaces", namespaces);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取命名空间列表失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("获取命名空间列表失败: " + e.getMessage()));
        }
    }

    /**
     * 根据表名查询相关SQL
     * GET /api/mybatis/queries/table/{tableName}
     */
    @GetMapping("/queries/table/{tableName}")
    public ResponseEntity<?> getQueriesByTable(@PathVariable String tableName) {
        try {
            logger.info("查询表相关的SQL: tableName={}", tableName);

            var queries = myBatisConfigurationParserService.getQueriesByTable(tableName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tableName", tableName);
            response.put("count", queries.size());
            response.put("queries", queries);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查询表SQL失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据命名空间查询相关SQL
     * GET /api/mybatis/queries/namespace/{namespace}
     */
    @GetMapping("/queries/namespace/{namespace}")
    public ResponseEntity<?> getQueriesByNamespace(@PathVariable String namespace) {
        try {
            logger.info("查询命名空间相关的SQL: namespace={}", namespace);

            var queries = myBatisConfigurationParserService.getQueriesByNamespace(namespace);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("namespace", namespace);
            response.put("count", queries.size());
            response.put("queries", queries);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查询命名空间SQL失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有Mapper参数
     * GET /api/mybatis/parameters
     */
    @GetMapping("/parameters")
    public ResponseEntity<?> getAllParameters() {
        try {
            // 注意：这里需要添加一个方法来获取所有参数
            // 由于MapperParameterRepository继承自JpaRepository，可以直接使用findAll()
            List<MapperParameter> parameters = myBatisConfigurationParserService.getAllMapperParameters();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", parameters.size());
            response.put("parameters", parameters);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取Mapper参数列表失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("获取参数列表失败: " + e.getMessage()));
        }
    }

    /**
     * 根据命名空间获取参数
     * GET /api/mybatis/parameters/namespace/{namespace}
     */
    @GetMapping("/parameters/namespace/{namespace}")
    public ResponseEntity<?> getParametersByNamespace(@PathVariable String namespace) {
        try {
            logger.info("获取命名空间的参数: namespace={}", namespace);
            
            List<MapperParameter> parameters = myBatisConfigurationParserService.getParametersByNamespace(namespace);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("namespace", namespace);
            response.put("count", parameters.size());
            response.put("parameters", parameters);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取命名空间参数失败: namespace={}", namespace, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("获取参数失败: " + e.getMessage()));
        }
    }

    /**
     * 根据Mapper ID获取参数
     * GET /api/mybatis/parameters/{mapperId}
     */
    @GetMapping("/parameters/{mapperId}")
    public ResponseEntity<?> getParameter(@PathVariable String mapperId) {
        try {
            Map<String, Object> parameter = myBatisConfigurationParserService.getMapperParameter(mapperId);
            
            if (parameter == null) {
                return ResponseEntity.status(404)
                        .body(createErrorResponse("参数不存在"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mapperId", mapperId);
            response.put("parameter", parameter);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取Mapper参数失败: mapperId={}", mapperId, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("获取参数失败: " + e.getMessage()));
        }
    }

    /**
     * 保存或更新Mapper参数
     * POST /api/mybatis/parameters
     */
    @PostMapping("/parameters")
    public ResponseEntity<?> saveParameter(@RequestBody Map<String, Object> request) {
        try {
            String mapperId = (String) request.get("mapperId");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) request.get("parameters");
            
            if (mapperId == null || mapperId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("mapperId不能为空"));
            }
            
            if (parameters == null) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("parameters不能为空"));
            }
            
            logger.info("保存Mapper参数: mapperId={}", mapperId);
            
            MapperParameter saved = myBatisConfigurationParserService.saveMapperParameter(mapperId, parameters);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "参数保存成功");
            response.put("mapperId", saved.getMapperId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存Mapper参数失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("保存参数失败: " + e.getMessage()));
        }
    }

    /**
     * 删除Mapper参数
     * DELETE /api/mybatis/parameters/{mapperId}
     */
    @DeleteMapping("/parameters/{mapperId}")
    public ResponseEntity<?> deleteParameter(@PathVariable String mapperId) {
        try {
            logger.info("删除Mapper参数: mapperId={}", mapperId);
            
            myBatisConfigurationParserService.deleteMapperParameter(mapperId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "参数删除成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除Mapper参数失败: mapperId={}", mapperId, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("删除参数失败: " + e.getMessage()));
        }
    }

    /**
     * 更新SQL查询
     * PUT /api/mybatis/queries/{id}
     */
    @PutMapping("/queries/{id}")
    public ResponseEntity<?> updateQuery(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            logger.info("更新SQL查询: id={}", id);
            
            var updatedQuery = myBatisConfigurationParserService.updateQuery(id, request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "查询更新成功");
            response.put("query", updatedQuery);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("更新SQL查询失败: id={}", id, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("更新查询失败: " + e.getMessage()));
        }
    }

    /**
     * 删除SQL查询
     * DELETE /api/mybatis/queries/{id}
     */
    @DeleteMapping("/queries/{id}")
    public ResponseEntity<?> deleteQuery(@PathVariable Long id) {
        try {
            logger.info("删除SQL查询: id={}", id);
            
            myBatisConfigurationParserService.deleteQuery(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "查询删除成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除SQL查询失败: id={}", id, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("删除查询失败: " + e.getMessage()));
        }
    }

    /**
     * 批量删除SQL查询
     * DELETE /api/mybatis/queries
     */
    @DeleteMapping("/queries")
    public ResponseEntity<?> deleteQueries(@RequestBody List<Long> ids) {
        try {
            logger.info("批量删除SQL查询: ids={}", ids);
            
            myBatisConfigurationParserService.deleteQueries(ids);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "成功删除 " + ids.size() + " 个查询");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("批量删除SQL查询失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("批量删除查询失败: " + e.getMessage()));
        }
    }

    /**
     * 更新Mapper参数
     * PUT /api/mybatis/parameters/id/{id}
     */
    @PutMapping("/parameters/id/{id}")
    public ResponseEntity<?> updateParameter(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            logger.info("更新Mapper参数: id={}", id);
            
            var updatedParameter = myBatisConfigurationParserService.updateParameter(id, request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "参数更新成功");
            response.put("parameter", updatedParameter);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("更新Mapper参数失败: id={}", id, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("更新参数失败: " + e.getMessage()));
        }
    }

    /**
     * 删除Mapper参数（根据ID）
     * DELETE /api/mybatis/parameters/id/{id}
     */
    @DeleteMapping("/parameters/id/{id}")
    public ResponseEntity<?> deleteParameterById(@PathVariable Long id) {
        try {
            logger.info("删除Mapper参数: id={}", id);
            
            myBatisConfigurationParserService.deleteParameterById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "参数删除成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除Mapper参数失败: id={}", id, e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("删除参数失败: " + e.getMessage()));
        }
    }

    /**
     * 批量删除Mapper参数
     * DELETE /api/mybatis/parameters/batch
     */
    @DeleteMapping("/parameters/batch")
    public ResponseEntity<?> deleteParameters(@RequestBody List<Long> ids) {
        try {
            logger.info("批量删除Mapper参数: ids={}", ids);
            
            myBatisConfigurationParserService.deleteParameters(ids);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "成功删除 " + ids.size() + " 个参数");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("批量删除Mapper参数失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("批量删除参数失败: " + e.getMessage()));
        }
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}

