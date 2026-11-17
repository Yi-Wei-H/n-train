# JMeter下单

下单是**写操作**，会真实扣库存/令牌并产生订单。测的不是"接口多快"，而是**锁+事务链路的产能**（成功订单 TPS）与**零超卖不变量**。`order-users.csv` 存 token 与乘车人 ID（**真实凭证，勿提交 git**）。

### 压测前放开三道闸（压完全部恢复）

| 闸 | 位置 | 现状 | 临时处理 |
|---|---|---|---|
| 限流 | member `OrderController#create` 三个 `@RateLimit`（USER/IP/DEVICE 各 10 次/分） | 单账号 10 TPS 封顶 | 注解注释或 count 调大，重启 member |
| 验证码 | Nacos `ntrain.member.captcha-threshold: 3`（60s 内第 4 单触发） | 第 4 单起必挂 | 配置改 1000000（发布即生效） |
| 令牌闸门 | DB `b_sk_token.remaining`（= 座位数×站对×1.2） | 耗尽后 SK_TOKEN_EXHAUSTED | 先查余量；不足则 `ntrain.sk-token.enabled=false` |

### 准备

1. 浏览器登录 → F12 → `localStorage['ntrain-web-token']`；`m_passenger` 查乘车人 ID → 填 `order-users.csv`（每行 `token,passengerId`，可多账号，CSV 循环复用）。
2. 核对库存（ntrain_business）：

```sql
SELECT COUNT(*) FROM b_daily_train_seat WHERE ride_date='2026-09-30' AND train_code='D901' AND seat_type=5;  -- 席别座位数=同区间可售上限
SELECT remaining FROM b_daily_train_ticket WHERE ride_date='2026-09-30' AND train_code='D901'
  AND from_station='AOP' AND to_station='CSQ' AND seat_type=5;
SELECT remaining FROM b_sk_token WHERE ride_date='2026-09-30' AND train_code='D901';
```

3. 确认 train-task 在跑：待支付订单靠它超时关单回补库存（本环境延迟消息发送失败，兜底扫描是唯一关单途径）。

### 运行与读数

```bash
jmeter -n -t doc/jmeter/order-create.jmx -Jhost=localhost -l order.jtl -e -o order-report
```

- **成功订单 TPS** = 锁+事务链路产能；JSON 断言 `$.code=0`，业务失败（TICKET_NOT_ENOUGH / SK_TOKEN_EXHAUSTED / DATA_CONFLICT）计入异常%，属预期结果看分布。
- 库存耗尽后剩余请求全部秒拒——**成功单数应恰好等于座位数**（不多卖一张）。
- business 日志"锁座开始→下单成功"时间差 = 锁等待+事务时长；"第 N/3 次尝试"日志统计重试分布。
- 100 线程抢同一车次必然大量 DATA_CONFLICT/快速失败（洪峰形态）；测纯产能降到 20~30 线程。

### 零超卖验证 SQL（压测后执行）

```sql
-- ① 成功订单数 == 初始座位数 - 剩余余票（同区间口径，不多卖不少卖）
SELECT (SELECT COUNT(*) FROM b_order WHERE ride_date='2026-09-30' AND train_code='D901' AND order_status=0) AS 待支付订单数,
       (SELECT remaining FROM b_daily_train_ticket WHERE ride_date='2026-09-30' AND train_code='D901'
          AND from_station='AOP' AND to_station='CSQ' AND seat_type=5) AS 剩余票;
-- ② 无重复占用（uk_order_item 唯一键兜底，查询应返回空）
SELECT order_no, COUNT(*) FROM b_seat_occupy WHERE ride_date='2026-09-30' AND train_code='D901'
  AND status=1 GROUP BY order_no HAVING COUNT(*) > 1;
-- ③ 位图一致性：每座位 sell 的 '1' 个数 = 覆盖它的有效占用区间长度（关单释放后归 0）
SELECT id, carriage_no, row_no, col_no, sell, LENGTH(sell) - LENGTH(REPLACE(sell,'1','')) AS 占用格数
  FROM b_daily_train_seat WHERE ride_date='2026-09-30' AND train_code='D901' AND seat_type=5
  AND LENGTH(sell) - LENGTH(REPLACE(sell,'1','')) > 0;
```

### 清理与多实例（可选）

- 压测产生的待支付订单由 train-task 超时关单（15 分钟 + 扫描周期）自动回补，关单后复核 remaining 已恢复；测试订单留在列表属正常数据。
- 坐实"多实例"表述：本地再起一个 business（`--server.port=8006`，共用 Redis/MySQL），网关 lb 分流两实例——分布式锁跨实例串行化的零超卖才是完整证据。
