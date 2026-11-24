# staging 发布候选审计报告

日期：2026-11-24。结论：PASS，范围为安全发布候选与提交前工程检查；不代表批准部署或正式营业。

## 分支、工作区和保护结果

发布工作区：D:/code/SPA/spa-agent-staging。分支：release/staging-v0.3.0-rc.1，基于远端 main 的211d6c2。发布分支不跟踪main，后续授权推送必须显式指定发布分支。没有提交、推送、合并，也没有创建或移动标签。

原工作区保留main/9811398。原目录1396个文件逐文件SHA256及Git工作状态一致；原docs、备份、测试证据和未跟踪文件均保留。Git worktree新增的管理元数据属于预期变化，不纳入源码内容指纹。旧标签v0.2.0-rc.1继续指向9811398。

190个业务、原有测试和小程序源文件按统一换行比较一致。新改动集中在发布保护、配置、构建和安全初始化。没有复制原始application.yml、真实.env、target、备份、Token或历史文档。

## 原BLOCKED项处理

| 项目 | 结果 |
|---|---|
| 文档和公开风险 | PASS：沿用上游删除docs的历史；仅新增脱敏发布说明，不恢复历史报告目录 |
| 忽略规则 | PASS：排除私人配置、target、backups、历史验证SQL、密钥及证书；不再放行docs/delivery |
| SQL边界 | PASS：仅允许新空库结构初始化；拒绝原spa、恢复库、错误模式及非空目标；旧验证/收尾/恢复脚本从候选区移除，原目录保留 |
| 权限变更 | PASS：授权脚本不在自动初始化目录，必须人工审核后显式批准；账号未满足DML-only限制时应用拒绝启动 |
| 普通应用保护 | PASS：环境处理器在数据源创建前校验，实际数据源完成身份和授权检查后才交给业务Bean使用；不依赖测试SPI |
| 环境隔离 | PASS：staging/production关闭开发登录及模拟支付；acceptance需明确授权、test profile及专用库名，不能覆盖生产库 |
| Linux兼容 | PASS：Shell为UTF-8 LF且语法通过；.gitattributes强制LF；5个Shell和mvnw的100755模式在私有临时索引验证 |
| 镜像固定 | PASS：Java17、Maven3.9.4、MySQL8、Redis7.4、Nginx1.28均固定为第三轮实际验证镜像的RepoDigest，限定linux/amd64 |

## 初始化和运维边界

空库基线现为28张表，不包含历史备份表结构。首次初始化先验证模式、实际库名和空库状态。V002至V006仅保留为需要人工审查的增量定义，不自动执行。没有DROP、TRUNCATE、DELETE或数据UPDATE被加入自动初始化流程。

MySQL镜像会按配置创建初始账号；在运维明确批准收紧账号权限前，应用保护器拒绝高权限账号启动。GRANT/REVOKE必须通过独立命令审批，不得关闭保护器绕过。具体流程见STAGING-DEPLOYMENT.md。

## 实际验证

- Maven离线verify及RC打包成功，版本0.3.0-rc.1；129项中68 PASS、61 SKIPPED、0失败。9项新增部署保护测试通过。
- 61项真实MySQL/Redis集成测试本轮明确关闭，未连接或写入数据库，不能将SKIPPED计作通过。第三轮120项通过是历史证据，仍保留在原目录。
- 可执行JAR实际拒绝指向原spa的staging配置，拒绝发生在数据源初始化之前。新数据源后处理器的真实身份拒绝路径使用模拟JDBC对象验证，不冒充真实MySQL实测。
- Node行为回归45 PASS；小程序结构/行为19 PASS。
- staging、production、acceptance和专用Redis四组Compose config --quiet通过；使用未部署的校验占位环境，没有创建容器。
- Git文件扫描、git diff --check、新增文本编码、Shell LF及语法检查通过；无密钥原文写入报告。
- JAR排除本地配置和私钥文件；SHA256见release/verification.json。

详细原始日志和私有索引证明在被忽略的target/release-audit内，不属于提交候选。发布文件白名单、迁移清单、排除清单、镜像锁和执行模式计划位于release目录。

## 提交前状态

真实Git索引为空。执行权限仅在私有临时索引中准备和验证，没有git add或实际索引模式变更。用户授权暂存后，应按release/executable-modes.json把相同100755模式应用到真实索引，再复核差异；不能以Windows文件属性代替Git模式。

## 仍需人工审核

1. 审核新空库流程与显式账号授权命令，确认未来目标项目、卷、数据库、凭据和服务器归属；本轮没有执行初始化或权限变更。
2. 新增启动保护、空库初始化及权限流程尚未在新的真实MySQL环境正向实测，首次staging必须受控验证；本轮没有重建或修改任何旧测试数据库及Docker卷。
3. 本轮没有构建候选应用镜像；需后续授权构建并记录新应用镜像digest，不能把上轮应用镜像当作当前候选。
4. 镜像锁仅适用于已验证linux/amd64，目标服务器架构、镜像漏洞和依赖漏洞需审核；未宣称通过完整CVE或渗透测试。
5. 私有凭据、PBKDF2哈希、域名、HTTPS和微信配置由用户提供并审核。真实支付、微信真机、长稳压测与云环境验收仍不在本轮通过范围。

本轮停止在可授权进行Git暂存和提交的候选状态。未执行commit、push、merge、reset、clean或标签操作。