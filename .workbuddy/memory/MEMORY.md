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

## 多科目分级题库（决策 D1–D6，详见 docs/PRD-多科目分级题库.md）
- D1 **默认年级 = 三年级(3)**（D6 修订：原五年级）；D2 **默认科目 = {拼音, 数学}**（D6 修订：原仅拼音）；
  D3 英语已落地本期；D4 题库不足绝不无题可出；D5 语文(ciyu)已落地本期；
  D6 = 出题数量规则（科目顺序表 + cap=⌈N/M⌉）+ 默认基线变更 + 存量按值迁移。
- 解锁题数选项 **5/10/15**（默认 5；原 3/5/10 默认 3，「3题」已删、`btnUnlock15` 已加）。
- 出题数量硬约束（N>5，M=启用科目数，cap=⌈N/M⌉）：数学 ≤ cap、英语 ≤ cap、拼音+语文合计 ≤ cap；
  N=5 时仅每科 ≥1、无上限。上限永不突破，宁可顺序表 < N（引擎循环重洗，绝不无题可出）。
- `QuestionEngine.create()` **先按 isAvailable(grade) 过滤**，再以"有效科目集"算顺序表与 cap；
  全不可用则强制回退 PinyinProvider（内置 144 条）→ 守住"永不 null"契约。
  副作用：勾选了但当前年级无题库的科目被**静默跳过**（如英语低年级），暂无 UI 提示（待定）。
- 约束：干扰项不可用占位；至少启用一科目；拼音字表与 CharacterBank 144 条一致；
  用 org.json 零依赖；科目数配置驱动；TV 用焦点+确认 toggle；不动计时/防绕过逻辑。
- ⚠️ 改 Java 后**必须实跑 `assembleDebug --offline`**：新增集合类等极易漏 import，肉眼审不出
  （2026-10-13 因漏 `import java.util.LinkedHashSet` 构建 FAILED）。
- ⚠️ 改默认值/规则时，PRD、设计文档、README、代码注释要**一并同步**，否则多处残留旧值自相矛盾。

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

## 换机 / 把项目带回家的清单（2026-10-13 整理）
- 仓库外**必须单独拷贝**的三样（.gitignore 已排除，`git clone` 拿不到）：
  ① 发布密钥库 `D:/soft/myapp/kidslock-release.keystore`（release 签名唯一入口，由环境变量 `KEYSTORE_FILE` 指向；
     `app/build.gradle` 只在 KEYSTORE_FILE+KEYSTORE_PASSWORD 都存在时才签名，否则 release 不签名）
  ② Android SDK `./android-sdk/`（约 277M）
  ③ 免安装 JDK17 `./.workbuddy/tools/jdk17`
- **Gradle 缓存不在项目里**：在 `C:\Users\<用户名>\.gradle`（约 528M，含 gradle-8.5 发行包 + 全部依赖）。
  想在家**离线**构建必须一起拷，或用 `--gradle-user-home` 指向；否则首次构建需联网下载依赖。
- `local.properties` 被 gitignore 且写死绝对路径 `sdk.dir=D:/soft/myapp/kidslock/android-sdk`，
  换盘符/换机器后**必须改**（或删掉改用 `ANDROID_HOME`）；`gradle.properties` 的
  `android.overridePathCheck=true` 让 SDK 放非标准路径也能过。
- 到家验证：`export JAVA_HOME=<项目>/.workbuddy/tools/jdk17 && ./gradlew assembleDebug --offline`
  （`preBuild` 会自动跑 `verifyQuestionBanks`，校验拼音144条/英语/语文词表，可当冒烟）。
- ⚠️ 长时间未 push 会积压：`git log origin/main..HEAD` 检查（2026-10-13 曾积压 22 个提交）。
  origin = `https://github.com/bgaidu/kidslock.git`（公开仓库，push 前确认敏感内容未入库）。

## 真机网络 ADB 调试（已打通，2026-10-12）
- 荣耀 GDI-W09（Android 14）：`adb pair ip:配对端口`+配对码 → `adb connect ip:连接端口`；端口会变；
  沙箱 daemon 每条命令后被回收，**命令开头必须重连**；`/sdcard` 参数需 `MSYS_NO_PATHCONV=1`。
- ⚠️ 调试期间无线调试通知触发守护的外来窗口防御（HOME+悬浮窗强盖循环），点击全被吞。
  破解：`appops set com.kidslock.app SYSTEM_ALERT_WINDOW deny`+force-stop+重拉
  （勿 settings delete 禁守护，有自愈）。测完还原 allow。
