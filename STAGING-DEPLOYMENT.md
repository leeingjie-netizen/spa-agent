# staging 部署候选使用边界

仅适用于经授权的新 staging 环境。生产或 staging 只使用 compose.yaml；不得叠加 compose.acceptance.yaml。后者仅允许 test profile、acceptance 模式、明确授权标志及独立 spa_acceptance_14位数字库名。

1. 从 deploy/staging.env.example 创建私有 .env.staging，填写新环境凭据；生产使用独立项目、库、卷、密码及 .env.production。
2. 检查主机端口、Compose 项目和卷归属；不得复用业务卷，不得开放 MySQL/Redis/app 端口。
3. 在仓库根目录运行以下流程。init 仅启动 MySQL/Redis，只有新空卷才会自动建表；已有表会拒绝初始化。原 spa、恢复库及其他库名均不允许。

```sh
sh deploy/ops.sh check .env.staging
sh deploy/ops.sh init .env.staging
# 人工审核 deploy/grant-app.sh 后，明确批准账号权限变更：
sh deploy/ops.sh grant .env.staging --approve-privilege-changes
sh deploy/ops.sh start .env.staging
```

应用启动前会核验模式与 profile、模拟开关、配置库名、实际连接库名及账号授权。账号只能对目标库拥有审核过的 DML 权限；角色授权、跨库授权、DDL、ALL PRIVILEGES 和 GRANT OPTION 会被拒绝。若未执行明确批准的权限步骤，应用拒绝启动，不能靠关闭保护器继续。

数据库结构基线为 db/schema/bootstrap.sql，共28张空表，排除了历史备份表结构。db/migrations/V002至V006仅是待审核的历史增量定义，不自动运行；V001为原本机基线检查，已从候选区移除。禁止将空库基线重放到现有环境，禁止把本地验证、清理或恢复 SQL 当作发布迁移。

Compose使用已验证的镜像digest与 linux/amd64 平台，见 release/images.lock.json。候选版本为0.3.0-rc.1；应用镜像尚需由获授权的发布流程构建并记录digest。本轮未构建或启动新的容器、未执行数据库初始化或权限脚本。

HTTPS、域名、真实微信配置、镜像漏洞评估及首次目标环境实测需人工审核。没有真实在线支付验收，不允许直接正式营业。保留旧镜像，回滚仅更换应用并刷新同项目Nginx，不恢复或删除数据库卷。