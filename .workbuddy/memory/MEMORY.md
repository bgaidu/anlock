# KidsLock 项目长期记忆

## 环境关键点（构建 / 签名）
- JDK17：`/d/soft/myapp/kidslock/.workbuddy/tools/jdk17`（Temurin 17.0.20.1，免安装）。
  Bash 子 shell 不继承 JAVA_HOME，构建前需
  `export JAVA_HOME=/d/soft/myapp/kidslock/.workbuddy/tools/jdk17 && export PATH="$JAVA_HOME/bin:$PATH"`。
- SDK：`D:/soft/myapp/kidslock/android-sdk`（local.properties 已指向）。
- 代理：仅本地 `127.0.0.1:54260`；release 的 lintVital 需联网拉 lint-gradle，代理关时离线跳过：
  `./gradlew assembleRelease -x lintVitalAnalyzeRelease -x lintVitalRelease --offline`。
- 发布签名：环境变量 KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS(kidslock) / KEY_PASSWORD；
  keystore 在仓库外 `D:/soft/myapp/kidslock-release.keystore`，密码已验证可用（明文绝不入库）。

## 多科目分级题库（决策 D1–D4，详见 docs/PRD-多科目分级题库.md）
- D1 五年级默认；D2 默认仅拼音；D3 英语本期不做；D4 题库不足绝不无题可出。
- 约束：干扰项不可用占位；至少启用一科目；拼音字表与 CharacterBank 144 条一致；
  用 org.json 零依赖；科目数配置驱动；TV 用焦点+确认 toggle；不动计时/防绕过逻辑。

## ⚠️ 限流避免方案（多 agent 编排纪律，必须遵守）
根因：429 撞在**子代理模型 API 的并行突发调用**，与用户切换聊天模型无关（worker 用团队默认模型）。
重置窗口见 429 报错时间戳（如 2026-10-12 21:17 UTC+8）。
规则：
1. 本地/机械活（读、搜、改、构建、跑命令）我**直接做**，不派 agent（不占模型 API）。
2. 并发 agent **上限 2、优先 1**；架构师→工程师→QA **一律串行**，禁止并行 fan-out。
3. 续活用 `SendMessage` **复用同一 agent**，不反复 spawn 新 agent（每次新建都是一波突发）。
4. 派前给**一次性完整 spec**（路径/行号/精确改动/验收），让 agent 1–2 轮跑完，减少往返。
5. 遇 429 先读重置时间，**不立刻重试**；改为我直接执行，或等窗口过后再派。
6. 必须并行时**错开 launch**、保持每个 agent 精简，避开其他 agent 的突发期。
7. 若可用更高配额/独立池的模型，重活路由到那里（切换默认模型或有助于配额，需验证）。
