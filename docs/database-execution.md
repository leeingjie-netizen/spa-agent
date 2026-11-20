# 本地数据库执行记录

## 2026-11-16：排班基线核验

- 配置来源：`src/main/resources/application.yml`，实际连接 `localhost:3306/spa`。
- 实际身份：`spa / LAPTOP-V9GM60MP / 3306 / MySQL 8.0.35 / root@localhost`。本机开发实例，未连接生产库。
- VMware 的 `192.168.100.128:3307`、Redis `:6379` 在沙箱内连接失败，沙箱外 TCP 复核均可达。未把本机数据库配置切换成虚拟机数据库。
- 已检查全部现有表、字段、默认值、可空性、索引和授权。周规则、午休、来源和唯一索引均已存在，不重复建表/加字段。
- 保存并执行 `db/migrations/V001__verify_existing_schedule_baseline.sql`：10 项检查全部返回 1；这是只读基线脚本，无 DDL/DML。
- 备份：`backups/20261116T111437632Z/spa.sql`，同目录 `manifest.json` 保存身份、行数、SHA256。业务备份已被 Git 忽略。
- SHA256：`1DEC92833092ACCAD0D36C5622CA28695CCF4CA1EC367F8A40561B762C1E4E2E`。
- 验证方法：mysqldump 成功；逐条 INSERT 数量与备份前后 SELECT COUNT 一致。未做恢复演练，不能把文件校验等同于恢复成功。

| 表 | 备份行数 |
| --- | ---: |
| appointment | 13 |
| appointment_slot | 29 |
| spa_service | 5 |
| spa_user | 1 |
| staff | 3 |
| staff_schedule | 11 |
| staff_schedule_backup_20261024 | 11 |
| staff_service | 7 |
| staff_weekly_rule | 1 |

历史兼容：保留既有 MANUAL 默认值、全部 11 条人工排班以及已有预约和用户。没有清空、删除、重建表，没有覆盖业务记录。

测试后再次备份到 `backups/20261116T112105709Z`，SHA256 为 `5E19D5F4B383024CD179DA134051DE1920856CF1BA63DC2D661F6484164922FB`。逐条比较两份文件的全部 81 条 INSERT，内容完全一致（文件头时间不同，整文件哈希不同属正常）。

执行 `LocalDatabaseIntegrityTest` 3 项通过，SQL 先保存到 `db/verification/local_integrity_test.sql`。真实测试仅在事务中插入隔离负数 ID，验证唯一约束错误及整体回滚，无提交、DDL、UPDATE 或 DELETE；回滚后零残留断言通过。未修改既有业务记录。

## 后续迁移执行规范

先查询实际结构并保存差异 SQL；对待修改表执行备份和核验；仅对已确认的本地开发库执行新增表/字段/索引。检查重复键和历史兼容后才创建唯一索引。遇到删除、覆盖或无法证明安全的迁移，先停下说明风险并取得同意。

`scripts/backup-local-db.ps1` 只允许本次确认的 `127.0.0.1:3306/spa`，从当前进程的 `MYSQL_PWD` 读取密码，不打印密码。备份失败或行数变化会报错并阻止后续迁移；执行 SQL 后还须核对表、列、索引及业务数据。

恢复必须在另建的隔离验证库中演练。备份文件不含 DROP TABLE，但恢复可能遇到已有主键，因此严禁直接导入当前业务库覆盖记录。

## 2026-11-17 恢复与HTTP验收

V002～V005实际表均存在，未重复迁移。恢复前备份backups/20261117T004645520Z，核对导出行数。真实业务测试67通过，HTTP验收48断言通过，报告target/http-acceptance_e995875332de48fb.json。

夹具退役前备份backups/20261117T005435877Z，先保存并执行db/verification/recovery_fixture_retirement.sql，仅停用本次新建技师、服务和券，移除本次新建且reason精确匹配tag的两条全局营业日期覆盖；未删除历史预约或排班。保留新增订单/账本/审批审计记录。

退役后备份backups/20261117T005454002Z，对比恢复前85条原有导出INSERT记录全部相同。原13条预约、11条排班未变，新增3条预约、2条排班属于验收清单。验证未包含恢复演练。

## 2026-11-17 Dashboard接续与V006

V006仅新增 `schedule_exception_revocation` 审计表：执行前备份并确认表不存在；不修改/删除原例外。表主键exception_id，撤销按技师日锁同步，保留操作者、时间和原因，重复撤销幂等。原记录兼容和撤销后时段恢复真实MySQL事务测试通过。V002～V005未重放。

隔离恢复演练：backups/20261117T014654692Z恢复28表128条；V006后backups/20261117T020712535Z恢复29表128条，数据库spa_restore_20261117020722，逐条导出INSERT一致。隔离库保留，未覆盖spa；详见对应restore-drill.json及scripts/restore-drill.ps1。

统计SQL先保存db/verification/analytics_test.sql。修复严格模式DATE_FORMAT分组不一致，保留ONLY_FULL_GROUP_BY。模拟钱包充值不计入收款；仅PAY减REFUND，REAL与SIMULATED分离，BigDecimal/DECIMAL。

HTTP第一次22项后遇409被通用Advice转换500，事务回滚，清单ec726241f3414c63保留。修复后48项全部通过，清单8274d0470b614894。备份20261117T022100993Z验证后执行dashboard_fixture_retirement.sql，仅退役这两轮标记夹具；未付测试单通过业务API取消，保留订单/预约/排班/账本/审批。精确tag移除测试新建全局营业日期覆盖；不会影响历史日期。

退役后备份20261117T022304915Z。与恢复前20261117T004645520Z比较，85条原有INSERT全部仍存在且内容相同，原13预约/11排班不变。本轮模拟净收款240.00、真实0.00与独立SQL一致（target/dashboard-http.json，6条支付流水）。测试数据保留账本，不伪装真实运营。

一次备份恰与UI创建隔离账号并发，行数核验拒绝，未继续执行SQL；待账号创建稳定后重新备份成功才运行HTTP验收。

## 2026-11-18 最终验收夹具收尾

上轮acceptance_56389bdb10ed4023收尾中止并非数据归属被绕过：并发抢约实际由用户B赢得，而旧脚本只接受清单用户A。改为先验证清单两位用户的dev identity，再验证每笔订单/预约/技师/服务ID，全部核验后才执行业务取消和SQL。四项测试覆盖B赢家、外部用户、外部订单/预约、错误技师/服务，均通过。

先验证备份backups/20261117T162038547Z，复用已保存且内容完全相同的db/verification/acceptance_56389bdb10ed4023_retirement.sql，执行成功。测试技师/服务/券已停用；只删除精确tag和清单日期匹配的测试全局营业日期覆盖，不删除预约、排班、订单、支付、积分、钱包或审批记录。回执target/acceptance_56389bdb10ed4023_retirement-result.json。

收尾后备份backups/20261117T162111842Z，与初始backups/20261117T004645520Z的85条INSERT逐条对比：0条变化或缺失，原13预约、11排班完整不变。target/final-history-verification.json保存校验结果。未重复迁移V001～V006，未访问生产库。

收尾后只读HTTP16项通过：统一财务18条与独立SQL相同，真实累计净收款0.00、模拟累计360.00。后者为保留的测试业务账本，不是门店真实收入；充值未重复计入收入。


## 2026-11-18 BUG-001～013限定验证

无数据库结构变更，不重放V001～V006。执行范围先保存db/verification/bug001_013_verification.sql。前备份backups/20261118T032804103Z，后备份backups/20261118T033827427Z；均已核对导出行数。仅7项BUG数据库回归与8项原请假/排班事务回归；新建负ID技师/服务/排班、员工申请、预约及未付订单、合法表单新增全部回滚，无支付、取消、退款或积分调整操作。31项HTTP只读业务查询及预期被拒绝的非法表单/管理员申请，登录退出只改变技术Token。

247条业务、账户和余额等原始INSERT逐条相同，0变化/缺失，0新增持久业务行；仅技术spa_auth_token排除，见target/bugfix-data-preservation.json。历史兼容：13条2026年无关联订单旧预约可由新只读接口查询，保留原状态和金额；支付订单仍11条。积分/退款样本订单2122623908435218435：120−10−1=109，支付109、退款109；本轮仅查询，未补造订单或修改流水。未发生生产连接、清表、历史人工排班覆盖或DDL。


## 2026-11-18 四项复修限定验证

本轮业务后端和结构未修改。仅运行现有表单数据库用例invalidCatalogInputsReturn400QuicklyAndCreateNoRows：无效输入独立400、表数量不变，合法技师新增测试事务回滚。前备份20261118T065618547Z，后备份20261118T065721588Z；全部260条INSERT逐条相同（包含技术Token），0变化/缺失/新增，见target/bug014-data-preservation.json。未执行预约、支付、取消、退款、积分变更、DDL或重复迁移。隔离18081进程仅用于启动/停止管理测试，自动业务任务关闭且已停止；原18080保留。


## 2026-11-18 BUG-015 / UX-001 / UX-002只读展示验证

无DDL、结构迁移、业务写入、清理、补造订单、支付/取消/退款请求。新脚本scripts/verify-record-views.cjs只读SELECT/SHOW和现有GET接口，管理员登录/退出仅使用技术Token。所有schema指纹及248条非spa_auth_token记录前后一致（包括业务、账户、积分、余额）；报告target/bug015-readonly-http.json。24条预约、11真实交易，13无关联订单旧预约保留。样本预约2122623908372303873/订单2122623908435218435：预约120、原价120、优惠11、应付109、支付109、退款109，均实际字段，未重新计算或更新。Java数据查询及状态流转原样保留。
