# 余票查询接口压测手册（JMeter）

## 被测接口与三层对照

| 线程组 | 目标 | 端口 | 路径 | 测的是什么 |
|---|---|---|---|---|
| ①预热 | 网关 | 8000 | `/api/member/ticket/query` | 把缓存填热（1 线程 × 5 次） |
| ②主测 | 网关 | 8000 | `/api/member/ticket/query` | 生产全链路缓存命中路径 |
| ③对比 | member 直连 | 8001 | `/ticket/query` | ② − ③ = 网关转发开销 |
| ④基线 | business 直连 | 8005 | `/tk/ticket/query` | 无缓存裸 DB 查询，② − ④ = 缓存优化的价值 |

## 运行

```bash
curl "http://localhost:8000/api/member/ticket/query?rideDate=2026-09-30&fromStation=AOP&toStation=CSQ"  # 验证
jmeter -n -t doc/jmeter/ticket-query.jmx -Jhost=localhost -l result.jtl -e -o report                    # 压测+HTML报告
```

## 实测记录（2026-09-26，本机 → remotelist 公网链路）

| 组 | 路径 | 样本 | QPS  | avg | 中位 | P90 | P99 | 异常% |
|---|---|---|------|---|---|---|---|---|
| ② 网关→member（命中） | :8000 | 250,196 | 1390 | 65ms | 37ms | 158ms | 412ms | 0 |
| ③ member 直连（命中） | :8001 | 263,317 | 1463 | 62ms | 53ms | 127ms | 235ms | 0 |
| ④ business 直查（基线） | :8005 | 34,726 | 288  | 159ms | 136ms | 261ms | 552ms | 0 |

结论：缓存命中路径吞吐为无缓存基线的 ~4.8 倍（1390/288），平均耗时降 ~60%、中位数降 ~73%，稳态查询零落库；网关开销 <5%（②③ 差值在运行间波动内）。注意：同②组另一次运行曾测得 36ms avg / 2312 TPS——公网链路波动显著，结论以同批对照与中位数为准，不跨批比较绝对值。

## 结果判读与注意

- Redis/MySQL 都在远程服务器，绝对 QPS 只代表本条公网链路；优化结论看同批相对差。
- 缓存生效旁证：压②③期间 business 无查询日志、MySQL QPS≈0。
- member/business 每查询一条 info 日志，高 TPS 下日志膨胀且拖慢服务（已实测坐实，建议降 debug）。
- ④ 真打共享的 2C8G 阿里云 MySQL，保持 50 线程/2 分钟以内。
