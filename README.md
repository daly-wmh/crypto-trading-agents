# 加密投研

采用 Java 21、Spring Boot 3.4、Spring AI 1.0、TimescaleDB（PostgreSQL 16）与 PgVector。前端采用 Vue 3、Element Plus、ECharts 和 Vite。

- **现货行情**：Binance 公开数据，默认提供 BTC、ETH、SOL、BNB、XRP、DOGE 的 USDT 交易对。现价和 15 分钟、1 小时、4 小时、1 日 K 线走 Binance WebSocket，更新时间以浏览器本地时区显示。K 线首屏和重连时查询历史数据，推送不可用时约每 5 秒 HTTP 轮询。
- **智能分析**：趋势技术面、成交与波动分析、多空双轮辩论、研究裁定、模拟风控、最终研报。节点、模型、提示词和辩论轮数可在「流水线」调整。分析进度通过 SSE 更新，节点报错后可从已完成阶段续跑。
- **模拟交易**：初始余额 100,000 USDT，支持最多 8 位小数的币数量。应用以公开报价模拟成交，双向手续费 0.1%。交易页在 K 线上方显示随现价更新的浮动盈亏。历史未实现盈亏按各周期收盘价估算。研报信号须经页面确认，一份报告在同一账户最多成交一次。
- **历史报告、顾问和经验库**：顾问可以调用行情、持仓和经验工具。PgVector 保存情境和复盘，供后续分析召回。每日复盘默认在 01:00 UTC 执行。
- **定时任务**：在「工作流 → 定时任务」查看下次复盘和最近 30 次执行，可修改 UTC Cron、暂停自动执行或立即复盘。计划和执行记录存入数据库，执行持仓复盘需要启用 PgVector。
- **新闻快讯**：顶部「新闻快讯」每分钟更新 CoinDesk、Cointelegraph、以太坊基金会、美国 SEC 和美联储货币政策 RSS。按发布时间显示符合筛选条件的快讯，标出类型、关键词和资产。浏览时按界面语言逐条生成标题、详细摘要和 AI 解读，中英文分别缓存，可在快讯下展开来源摘要。
- **个性化**：顶部「语言」切换简体中文和英文。「设置 → 个性化」切换昼夜模式及磨砂玻璃、实色效果，偏好保存在当前浏览器。

## 在 Windows 的 IntelliJ IDEA 中运行

准备 JDK 21、Node.js（含 npm）、Docker Compose 和 Ollama。先在 IDEA 中打开本目录的 `pom.xml` 并导入 Maven 项目，将 Project SDK 设为 JDK 21。

在 IDEA 的终端中，从项目根目录启动数据库，再准备默认的对话和嵌入模型。

```powershell
docker compose up -d --wait
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

确保 Ollama 服务已运行。可在 IDEA 中运行 `com.tradingagents.TradingAgentsApplication`，也可在项目根目录执行 `./mvnw.cmd spring-boot:run`。Flyway 会初始化数据库和默认流水线。后端监听 `127.0.0.1:8080`。

新开一个终端运行前端。

```powershell
cd frontend
npm ci
npm run dev
```

打开 [本地工作台](http://127.0.0.1:3000)。行情接口无需交易所 API Key。若使用 OpenAI 兼容的对话模型，在「系统设置」添加模型源，填写 Base URL、模型名及 API Key 所在的环境变量名。然后将该模型源设为全局默认。环境变量应配置在启动后端的终端或 IDEA 运行配置中。默认向量嵌入使用本地 Ollama 的 `nomic-embed-text`。

## 常用配置

| 环境变量 | 作用 |
| --- | --- |
| `CRYPTO_PAIRS` | 支持的 Binance 现货 USDT 交易对，逗号分隔 |
| `CRYPTO_MARKET_URL` | Binance 公开行情服务地址，默认 `https://data-api.binance.vision` |
| `CRYPTO_OLLAMA_URL`、`CRYPTO_OLLAMA_MODEL` | 默认 Ollama 服务地址和对话模型 |
| `CRYPTO_DB_URL`、`CRYPTO_DB_USER`、`CRYPTO_DB_PASSWORD` | 后端数据库连接 |
| `CRYPTO_DB_PORT` | Compose 将数据库映射到本机的端口，变更后同步更新 `CRYPTO_DB_URL` |
| `CRYPTO_PORT`、`CRYPTO_API_URL` | 后端端口和 Vite 代理地址，后者在前端进程中配置 |
| `CRYPTO_EMBEDDING_MODEL`、`CRYPTO_EMBEDDING_DIMENSIONS` | Ollama 嵌入模型和与之对应的向量维度 |
| `CRYPTO_REFLECTION_CRON` | 持仓复盘的默认 UTC Cron 表达式，页面保存的计划优先 |

后端测试运行 `./mvnw.cmd verify`。测试在隔离数据库中启动应用，模拟行情和模型接口，验证 K 线、分数数量成交、卖出限制、SSE 续跑和重复确认去重。前端可用 `npm run build` 验证。

## 来源

参考并改编自 [BlackmodeN/trading-agents-java](https://github.com/BlackmodeN/trading-agents-java/tree/1a13d92b9d00b81a4461618f4b90754cb70ed429)。上游 README 标注 Apache 2.0，许可证见 [LICENSE](LICENSE)。

行情使用 [Binance 公开现货接口](https://developers.binance.com/en/docs/products/spot/faqs/market_data_only)。
