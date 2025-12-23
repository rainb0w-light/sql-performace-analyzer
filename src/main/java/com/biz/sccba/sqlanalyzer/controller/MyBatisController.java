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
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}

