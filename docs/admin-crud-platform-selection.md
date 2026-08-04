# DAO 驱动的 CRUD、REST 与管理查询平台选型

> 状态：选型讨论稿  
> 日期：2026-07-30  
> 适用范围：SQL Performance Analyzer 服务端管理能力  
> 本文只记录架构与产品选型，不代表已批准实施。

## 1. 背景与目标

项目希望形成一套类似 Django Admin 的内部管理开发能力：根据数据模型和 DAO
设计，自动生成标准 CRUD、REST 接口及查询管理页面，减少为每一种后台资源重复编写
Controller、分页查询、表格、筛选器和表单的工作。

目标能力包括：

1. 根据受控的资源定义生成标准 CRUD。
2. 同时生成稳定、分页化、可审计的 REST API 和 OpenAPI 描述。
3. 自动提供列表、筛选、排序、分页、详情、创建和编辑页面。
4. 通过少量声明配置权限、字段展示、查询条件、敏感信息和业务动作。
5. 数据模型变化后可以安全重新生成，不覆盖手写业务代码。
6. 保留现有领域边界、租户隔离、状态机和审计规则。

本文中的“DAO 驱动”不等于直接把所有 Repository 方法暴露为 HTTP。DAO 是资源行为的
执行端口，但不是完整的 UI、权限和 API 元数据来源。

## 2. 当前工程约束

当前工程已经确定以下技术和架构边界：

- Java 21、Spring Boot 3.5。
- Spring Data JDBC，不使用 JPA/Hibernate。
- Flyway 是数据库结构的唯一事实来源。
- 领域 Repository Port 与 JDBC Adapter 分离。
- 管理数据库同时支持 H2 和 PostgreSQL。
- 所有租户资源必须由服务端认证上下文派生 `clientId`。
- 报告、事件、操作日志等资源是只读或追加写，不允许通用修改。
- Knowledge Version、Run、Conflict 等资源具有状态机或显式命令语义。
- Token、凭据、敏感样本等资源必须使用专门的掩码和操作规则。

详细约束见：

- [持久层架构决策](persistence-architecture.md)
- [系统架构](architecture.md)
- [REST 契约](contracts/rest-api.md)

当前代码中同时存在：

- 面向领域的 Repository Port，例如
  `KnowledgeSourceRepository`；
- Spring Data JDBC Entity 和底层 `CrudRepository`；
- 面向业务动作的 Controller，例如知识发布、回滚、Run 取消和冲突解决；
- 手工编写的静态管理页面。

这些资源并不都适合映射成相同的 CRUD。

## 3. 需求澄清：DAO 不能单独成为生成事实源

Repository 接口可以描述“能够执行什么查询或命令”，但不能可靠推导以下信息：

- 字段是否允许展示或编辑；
- 字段使用精确、模糊、范围、枚举还是关系查询；
- 字段是否需要脱敏；
- 哪些查询必须附加租户条件；
- 哪些操作需要什么角色；
- 删除是物理删除、软删除还是完全禁止；
- 哪些更新实际是需要审计的状态转换；
- 表单字段的标签、顺序、帮助文本和控件类型；
- 关联对象的显示字段和选择范围；
- 乐观锁、幂等和审计要求。

因此，完整事实源应由四部分组成：

```text
Flyway Schema
  提供表、列、主外键、唯一约束和数据库类型
        +
Spring Data JDBC Entity / API DTO
  提供 Java 类型、校验和 JSON 结构
        +
Admin Resource Spec
  提供权限、字段展示、过滤器、敏感性和允许操作
        +
Repository / Application Service
  提供租户隔离和真实业务行为
        ↓
生成 REST + OpenAPI + 标准管理页面
```

其中，Admin Resource Spec 是需要新增的显式元数据层。它可以使用 Java 注解、独立 YAML
或类型化 Java DSL 表达，具体载体应在原型验证后决定。

## 4. 资源分类

在生成之前，应先把 DAO 或领域资源分成以下四类：

| 类型 | 典型资源 | 允许生成的能力 |
|---|---|---|
| 标准资源 | 部分数据源配置、索引和分片元数据 | 列表、详情、创建、编辑；按策略决定是否删除 |
| 查询资源 | Analysis Report、Run Event、操作日志 | 查询、详情和导出，不允许修改 |
| 状态机资源 | Knowledge Version、Run、Metadata Conflict | 查询及显式命令，例如发布、回滚、取消和解决 |
| 敏感资源 | Token、数据源凭据 | 掩码展示、签发或吊销，禁止通用编辑和明文回显 |

通用生成器必须理解资源能力，而不能假设所有表都具有完整 CRUD。

## 5. 候选方案

### 5.1 Jmix

Jmix 是现成方案中最接近 Django Admin 产品体验的候选。它提供实体列表、详情、通用
过滤器、保存查询、权限、审计和 Generic REST API。Jmix Studio 可以从实体生成列表、
详情或主从视图。

优势：

- Django Admin 式能力完整。
- Generic REST API 可以直接暴露实体 CRUD。
- 通用过滤器、列表页、权限和审计能力成熟。
- 基于 Spring Boot，Java 团队学习成本相对可控。

不适配点：

- 核心数据模型以 JPA Entity 和 JPQL 为中心。
- 需要引入 Jmix Entity 增强、DataManager 和 UI 运行时。
- 与项目已经冻结的 Spring Data JDBC 决策冲突。
- 若嵌入现有服务，框架对数据模型和应用结构的接管范围较大。

结论：

如果建立全新的独立后台应用，并允许采用 JPA，Jmix 是首选候选；不建议直接接管当前
服务的持久层。

参考：

- [Jmix Entities](https://docs.jmix.io/jmix/2.7/data-model/entities.html)
- [Jmix Generic REST API](https://docs.jmix.io/jmix/2.7/rest/index.html)
- [Jmix Generic Filter](https://docs.jmix.io/jmix/2.7/flow-ui/vc/components/genericFilter.html)
- [Jmix View Creation Wizard](https://docs.jmix.io/jmix/2.7/studio/view-wizard.html)

### 5.2 JHipster

JHipster 可以从 Entity/JDL 生成数据库模型、Spring Data JPA Repository、REST
Controller 和 React/Angular/Vue CRUD 页面。

优势：

- 前后端生成范围完整。
- REST、分页、校验、DTO 和前端页面有统一约定。
- JDL 可以作为独立的数据模型描述。

不适配点：

- SQL 项目主要围绕 JPA/Hibernate。
- 数据库变更默认围绕 Liquibase，而本项目规定 Flyway 为唯一事实源。
- 更适合从 JDL 生成完整应用，而不是增量接入既有项目。
- 重新生成实体存在覆盖本地定制代码的风险。

结论：

适合新项目脚手架，不适合作为当前 Spring Data JDBC 工程的增量管理平台。

参考：

- [JHipster Creating an Entity](https://www.jhipster.tech/creating-an-entity/)
- [JHipster JDL](https://www.jhipster.tech/jdl/intro)

### 5.3 Apache Causeway

Apache Causeway 从实体、领域服务和 Repository 动态生成 Web UI、GraphQL 或 REST
表示，产品理念与“根据领域模型自动产生后台”高度一致。

优势：

- 运行时从领域模型直接生成 UI。
- 支持 REST、权限、审计、多租户和领域动作。
- 不要求为每个资源编写 Controller 和 HTML。

不适配点：

- 应用需要围绕 Causeway 的领域元模型组织。
- UI 和 API 的运行时生成机制会接管较多应用结构。
- 对现有 Spring Data JDBC Entity、Repository Port 和 Controller 的增量复用成本较高。

结论：

适合以 Naked Objects/领域模型驱动方式创建的新应用；不作为当前服务的首选增量方案。

参考：

- [Apache Causeway](https://github.com/apache/causeway)

### 5.4 OpenXava

OpenXava 可以从 JPA Entity 自动生成完整 Web 应用。

优势：

- 自动生成列表、详情、表单和常见业务界面。
- 简单数据后台的开发速度快。

不适配点：

- 以 JPA Entity 为核心。
- 对现有持久层和应用结构侵入较大。
- 复杂状态机、租户规则和产品化 REST 仍需专项设计。

结论：

适合简单、JPA 中心的后台应用，不适合直接承载当前服务。

参考：

- [OpenXava](https://github.com/openxava/openxava)

### 5.5 Spring Data REST

Spring Data REST 可以把受支持的 Spring Data Repository 暴露为 HAL REST，提供
CRUD、关联、分页和 Repository 查询方法。

优势：

- 后端样板代码少。
- Spring 生态内集成简单。
- 支持分页、搜索资源和模型元数据。

不适配点：

- HAL Explorer 是 API 浏览器，不是 Django Admin 管理页面。
- 容易直接暴露持久化实体，API 与内部模型耦合。
- 当前官方支持列表没有列出 Spring Data JDBC。
- 行级租户、敏感字段、状态机命令和审计仍需大量定制。
- 前端需要理解 HAL/HAL-FORMS，或额外实现适配器。

结论：

不建议作为本项目的标准 API 层。

参考：

- [Spring Data REST](https://github.com/spring-projects/spring-data-rest)
- [Spring Data REST Reference](https://docs.spring.io/spring-data/rest/reference/introduction.html)

### 5.6 Directus 等数据库直连后台

Directus 可以直接从数据库生成 API、管理面板、权限和内容模型。

优势：

- 上线速度快。
- 查询、表单、权限和管理页面完整。
- 几乎不需要 Java 端生成代码。

不适配点：

- 直接访问数据库会绕过 Repository Port 和 Application Service。
- 无法自然复用现有租户派生、状态机、幂等和审计规则。
- 容易把数据库表结构变成外部 API 契约。
- H2 本地模式与生产 PostgreSQL 的一致性难以保证。

结论：

只适合隔离环境中的临时只读运维查询，不作为正式产品后台。

参考：

- [Directus](https://github.com/directus/directus)

### 5.7 React-admin + Admin Resource Spec

React-admin 是构建在 REST/GraphQL API 之上的管理前端框架，提供列表、筛选、排序、
分页、表单、关系、权限、保存查询和批量操作。它通过 Data Provider 适配后端，不会
接管服务端持久层。

优势：

- 可以保留现有 Spring Data JDBC 和 Repository Port。
- 核心采用 MIT 许可证。
- 页面能力接近 Django Admin，且允许逐页深度定制。
- 可以从 Admin Resource Spec 或 OpenAPI 生成 Resource 配置。
- 复杂业务页面可以与标准生成页面并存。
- 不强制后端使用特定 ORM、媒体类型或数据库。

代价：

- React-admin 只解决前端，需要建设一层受控的后端生成器。
- 需要制定统一分页、筛选、排序、错误和权限契约。
- 需要自行维护 Admin Resource Spec 与生成模板。

结论：

与当前工程兼容性最高，推荐作为主方案。

参考：

- [React-admin](https://github.com/marmelab/react-admin)
- [React-admin Data Providers](https://marmelab.com/react-admin/DataProviders.html)

## 6. 选型矩阵

| 方案 | Django Admin 体验 | REST 自动化 | 当前架构适配 | 重新生成安全性 | 主要结论 |
|---|---:|---:|---:|---:|---|
| Jmix | 很强 | 很强 | 低 | 中 | 允许迁移 JPA 时的重构型备选 |
| JHipster | 较强 | 很强 | 低 | 低到中 | 适合由 JDL 创建新应用 |
| Apache Causeway | 很强 | 很强 | 低 | 高 | 适合领域模型由框架接管的新应用 |
| OpenXava | 很强 | 中等 | 低 | 高 | 适合简单 JPA 后台 |
| Spring Data REST | 弱 | 很强 | 低到中 | 高 | 没有完整管理产品体验 |
| Directus | 很强 | 很强 | 很低 | 高 | 绕过领域和租户边界 |
| React-admin + Resource Spec | 强 | 需要生成层 | 很高 | 可设计为高 | 推荐 |

## 7. 推荐决策

当前工程推荐选择：

> **Admin Resource Spec + Spring MVC/OpenAPI 生成层 + React-admin**

保留：

- Spring Data JDBC；
- Flyway；
- Repository Port；
- Application Service；
- `BearerClients` 派生的租户上下文；
- H2/PostgreSQL 双数据库契约；
- 现有审计、幂等、状态机和敏感信息边界。

新增：

1. Admin Resource Spec。
2. 统一的管理查询和命令 REST 规范。
3. 生成标准 Controller、DTO 映射、OpenAPI 元数据和契约测试的工具。
4. React-admin Data Provider。
5. 根据 Resource Spec 生成的列表、详情和表单配置。
6. 手写扩展点，用于状态机命令和复杂业务视图。

如果未来决定：

- 建立独立后台服务；
- 后台允许使用 JPA；
- 可以接受新的数据访问和 UI 运行时；

则重新评估 Jmix。满足这些前提时，Jmix 是最强的现成产品候选。

## 8. Admin Resource Spec 建议能力

每个资源至少应声明：

- 资源名、显示名、复数名和菜单分组；
- ID 字段和乐观锁字段；
- 读模型、写 DTO 和服务绑定；
- `list/get/create/update/delete/export` 是否允许；
- 默认排序和最大分页大小；
- 列表字段、详情字段和编辑字段；
- 字段标签、帮助文本、顺序和控件类型；
- 字段是否可筛选、可排序和可全文搜索；
- 每个字段允许的查询操作符；
- 枚举、日期范围和关系选择器配置；
- 敏感字段掩码及是否禁止进入响应；
- 租户归属及强制的服务端租户过滤；
- 资源、操作和字段级角色；
- 自定义命令及其参数、确认文案和幂等要求；
- 审计策略；
- 删除策略；
- API 版本和兼容性策略。

Resource Spec 不应包含数据库密码、Token 或运行时凭据。

## 9. REST 生成边界

生成的 REST API 应使用统一约定：

- 列表统一返回 `items/page/size/totalElements/totalPages`。
- 过滤条件采用白名单字段和类型化操作符。
- 排序字段必须在 Resource Spec 中声明。
- `clientId` 永远不接受客户端输入，必须从认证上下文派生。
- 创建和更新使用独立 DTO，不直接接收持久化 Entity。
- 敏感字段默认不进入响应。
- 状态机动作使用显式命令端点，不伪装为通用 `PATCH`。
- 幂等命令复用项目既有 `Idempotency-Key` 规则。
- 错误继续使用 RFC 9457 Problem Details。
- 删除默认关闭；必须由 Resource Spec 显式启用。
- 所有生成端点必须进入 OpenAPI 和消费者契约测试。

不应自动暴露底层 Spring Data `CrudRepository`。

## 10. 生成代码与手写代码隔离

为保证可持续重新生成，应遵循：

- 生成文件放入明确的 generated 目录或 generated source set。
- 生成文件禁止手工修改。
- Application Service、权限判断和复杂查询保持手写。
- 页面通过注册式扩展点追加列、过滤器、命令和详情区块。
- 重新生成只覆盖 generated 目录。
- CI 校验生成结果和 Resource Spec 是否一致。
- 模板版本写入生成文件，升级模板时可以识别差异。
- 不通过字符串拼接修改已有 Java 或 TypeScript 文件。

## 11. 建议的原型验证范围

第一轮不覆盖所有 DAO，选择四种代表性资源：

| 资源 | 验证重点 |
|---|---|
| DatasourceProfile | 标准 CRUD、敏感字段、角色权限和租户隔离 |
| AnalysisReport | 只读列表、复杂筛选、分页、详情和导出 |
| KnowledgeVersion | 状态展示、发布/回滚命令、审计和幂等 |
| MetadataConflict | 查询、详情、解决命令和角色控制 |

原型验收标准：

1. 能生成列表、详情和适用的表单。
2. 分页、排序、枚举、日期范围和模糊搜索具有统一行为。
3. H2 与 PostgreSQL 的 API 行为一致。
4. 租户负向测试证明不能跨 `clientId` 访问。
5. 权限可以控制到资源、操作和敏感字段。
6. OpenAPI 可以作为稳定前后端契约。
7. 重新生成不会覆盖手写逻辑。
8. 状态机命令可以出现在标准页面中，但仍调用现有 Application Service。
9. 查询页面对大数据量使用服务端分页和筛选。
10. 生成端点具备契约测试、权限测试和审计断言。

## 12. 待讨论事项

以下问题会影响 Resource Spec 和生成器的最终形态：

1. “DAO 设计”最终以 Flyway、Spring Data JDBC Entity、领域 DTO，还是新的资源描述文件
   为主要入口。
2. 管理页面是仅内部运维使用，还是未来也面向租户管理员。
3. P1 是否允许任何物理删除，还是统一只读、软删除或显式业务命令。
4. 是否要求保存个人查询条件、列配置和导出模板。
5. 是否需要字段级权限和数据脱敏，还是先只实现资源/操作级权限。
6. React 管理前端是作为当前服务的静态资源部署，还是独立构建和部署。
7. OpenAPI 是生成输入、生成输出，还是双向校验契约。

在这些事项确认前，本文推荐的是技术方向，不是最终实施规格。
