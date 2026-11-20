# 本轮界面展示检查点（2026-11-18）

BUG-015、UX-001、UX-002已修改并通过60项前端与35项只读联调；实际浏览器布局/缩放待人工。后端Java、数据库结构、状态机及资金计算未变，248条非Token业务/账户记录指纹一致。原003/005/006/014已获用户确认，未回归。

仅同步本机18080运行资源，四项HTTP响应SHA256与源码/target一致；未重启服务、未生成包含本轮UI的新JAR、未部署生产。现有旧JAR不代表当前UI，未来发布前须重新构建并完成验收。本轮不进入2.4B。详见[最新BUG/UX记录](bugfix-001-013.md)。

# 四项复修检查点（2026-11-18）

只处理003/005/006/014与本地启动版本排查，已确认功能不重做。前端50项通过、真实DB表单1项回滚通过；260条全库INSERT不变。微信运营图一种模拟器已实测，后台Toast、空表单卡顿及新登录导航待人工。原18080 PID52868未重启、8080无监听，资源HTTP与源码/target一致。隔离18081启停已实测并停止。

新增状态/安全停止与后台就绪启动方式见[BUG记录](bugfix-001-013.md)，关闭窗口不等于停止服务。未部署生产，2.4B继续暂停，原上线缺口仍有效。

# 本轮BUG修复检查点（2026-11-18）

13项BUG：5项已通过、8项待人工复核、0项已知未解决代码问题，详见[BUG汇总和验收清单](bugfix-001-013.md)。暂不能进入2.4B，也不具备正式上线条件。本轮仅本地开发修复，无生产部署。

JDK17编译/打包通过；Java实际不同测试72通过、24跳过，前端46通过，限定HTTP31通过；当前OSS图片GET200/jpeg但微信最终显示待验。247条受保护记录逐条不变，新测试数据事务回滚；本轮没有迁移或历史支付退款变更。新增只读接口和后台抽屉已加入包检查。当前18080已重载，AI配置正常且本轮未调用模型；为本轮复核，进程自动排班和订单超时任务临时关闭，常规启动配置未改。

以下历史准备记录保留；旧统计/页面状态以本轮BUG记录为准，原生产前缺口仍需逐项验收。

# 本地演示与部署准备（2026-11-18）

**可以进行本地开发演示，尚未达到正式上线条件；未执行生产部署。** 实际页面验收仍有缺口，见frontend-acceptance.md和acceptance-checklist.md。

本轮电脑操控恢复检查仍报browser access host unavailable，未继续不可靠的浏览器输入；财务最终布局及其他剩余页面保留待人工验收。具体访问地址、步骤和预期结果已写入acceptance-checklist.md。

## 如何启动与登录

- 使用JDK17：当前目录D:\devlop\JDK17；根目录执行 `./scripts/run-local.ps1`，默认18080。已有监听时复用服务，不重复启动。IDEA的8080是独立服务，小程序开发配置访问18080。
- 后台：`http://127.0.0.1:18080/admin/index.html`。管理员用户名和密码取忽略的.env.local的SPA_AUTH_ADMIN_USERNAME、SPA_AUTH_ADMIN_PASSWORD；当前用户名admin。哈希配置使用本人设置的密码，可运行set-local-admin-password.ps1交互设置，设置后重启。未重置现有密码。
- 普通消费者使用小程序。仅local/test profile加SPA_AUTH_DEV_LOGIN_ENABLED=true开放开发测试身份，不能自报管理员/技师；生产禁用。技师网页选择“技师”，使用管理员绑定的账号，仅进入自身请假工作入口。
- 演示服务、会员资产和保留测试订单可直接查看。完整点击预约需可用未来排班；可按人工验收清单新建专用测试技师/服务，不改原有人工排班。

## 当前验证证据

| 检查 | 结果 | 证据 |
|---|---|---|
| JDK17全量业务 | 87项：81通过、6旧演示跳过、0失败 | target/meeting-final-java.log |
| 前端/结构/财务行为 | 33项通过；收尾归属保护另4项通过 | target/final-acceptance-node.log，共37项 |
| 真实HTTP完整闭环 | 50项通过 | target/meeting-http.log及http-acceptance_56389bdb10ed4023.json |
| 收尾后财务HTTP | 16项通过，18条统一账本与独立SQL一致 | target/final-finance-http.log、finance-http.json |
| 独立财务对账 | 真实累计0.00，模拟累计360.00；充值不计服务收入 | target/dashboard-http.json |
| 订单费用 | 原价120、优惠11、金额109、100积分 | target/final-order-http.log |
| JAR | 最新构建成功；运行资源完整，本地application配置/密钥文件未入包 | target/final-acceptance-package.log、release-archive-verification.json |
| 历史兼容 | 原85条INSERT完全相同；13预约/11排班保留 | target/final-history-verification.json |
| 恢复演练 | 隔离库29表128条导出逐条一致 | backups/20261117T020712535Z/restore-drill.json |

Java主业务源码未在本轮新增改写，87项是上轮最新完整运行结果；本轮重跑新增收尾测试、前端测试、只读HTTP和打包。编译、HTTP与渲染分开记账。

## AI、支付和生产开关

- AI配置从spring.ai.openai.api-key读取DASHSCOPE_API_KEY，进程继承/.env.local可供本地启动。已修复错误属性检查，真实微信工具只读服务查询已成功，42px按钮和安全Markdown已实际显示。多轮确认预约完整验收仍未完成；本轮未产生收费模型请求。
- `./scripts/run-local.ps1 -CheckAiConfiguration`只检查存在状态；管理员`/api/admin/ai/status`提供脱敏运行时状态。不要再用“返回尚未配置”冒充AI成功。
- MOCK/MOCK_WALLET只允许显式local/test，prod/production明确禁用；测试余额不代表现金、不可提现。真实微信支付/回调/退款渠道未实现验收。
- 真实微信登录适配已实现，仍需SPA_WECHAT_APP_ID/SPA_WECHAT_APP_SECRET和真实微信环境验收。
- spa.scheduling.enabled默认关闭；启用后补排只新增缺失日期，保留已有AUTO/MANUAL。订单超时关闭任务释放未付预约。

## 数据库与收尾

本轮仅本机127.0.0.1:3306/spa；V001基线及V002～V006实际存在，未重放。开发Redis为192.168.100.128:6379。可通过SPRING_DATASOURCE_*、SPRING_DATA_REDIS_*配置覆盖，但本轮未连接生产库。

已修复收尾脚本对并发赢家固定用户A的错误假设，改为清单身份及订单/预约/技师/服务多重核验；四项归属保护测试通过。本轮执行前备份20261117T162038547Z，执行后备份20261117T162111842Z。仅停用acceptance_56389bdb10ed4023的新建技师/服务/券、撤掉其精确tag的测试营业日期覆盖，全部业务审计记录保留。实际回执及SQL见database-execution.md。

## 打包与外部配置

`mvn -o -DskipTests package`使用JDK17；`./scripts/verify-release-archive.ps1`检查最终JAR。该命令跳过重复测试，前述完整业务测试单独记录。JAR已排除本地application*.yml/yaml/properties和密钥文件；必须显式提供外部配置，不能靠含开发密码的内置配置启动。

`config/application.production.example.yml`仅为准备模板，不自动加载或部署。未来由用户单独安排发布时，显式使用prod profile与外部配置路径，提供数据库/Redis/微信/管理员PBKDF2哈希等凭据；模板模拟登录/支付关闭，排班自动补排初始关闭。应用数据库账号使用最小权限，DDL由单独迁移账号执行。

## 正式发布前仍缺少

1. 财务最终布局、状态筛选和分页；后台服务/技师/订单/运营页面完整浏览器验收。
2. 微信完整点击预约至评价、全状态、软键盘、真机及多尺寸验收。
3. 真实微信登录、真实支付/退款/回调与渠道资质；可配置的完整会员权益。
4. AI多轮确认预约实测，含失败、重复确认及身份隔离；控制收费调用次数。
5. 生产HTTPS、域名白名单、限流/防暴力登录、监控告警、日志留存、最小权限及生产备份恢复演练。
6. 目标部署环境的外部配置、迁移顺序和启动/回滚验收。本地隔离恢复不等于生产灾备验收。

当前不允许用真实用户资金运营，不宣称正式上线就绪。
