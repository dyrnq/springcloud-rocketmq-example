# springcloud-rocketmq-example

POC new features of rocketmq5

[English](README.md) | [中文](README.zh-CN.md)

| module               | desc                                                                                  |
| -------------------- | ------------------------------------------------------------------------------------- |
| spring-cloud-v2025-1 | spring-boot=4.1.0  <br/> spring-cloud=2025.1.2 <br/> spring-cloud-alibaba=2025.1.0    |
| spring-cloud-v2025   | spring-boot=3.5.9  <br/> spring-cloud=2025.0.1 <br/> spring-cloud-alibaba=2025.0.0    |
| spring-cloud-v2023   | spring-boot=3.3.13 <br/> spring-cloud=2023.0.6 <br/> spring-cloud-alibaba=2023.0.3.4  |
| spring-cloud-v2021   | spring-boot=2.7.18 <br/> spring-cloud=2021.0.9 <br/> spring-cloud-alibaba=2021.0.6.0  |
| sbs                  | spring-boot=3.5.9  <br/> rocketmq-spring-boot-starter=2.3.6                           |
| sbs-v5               | spring-boot=3.5.9  <br/> rocketmq-v5-client-spring-boot-starter=2.3.6                 |
| client               | rocketmq-client=5.5.0                                                                 |
| client-v5            | rocketmq-client-java=5.2.1                                                            |

| scripts                               | desc                                               |
| ------------------------------------- | -------------------------------------------------- |
| scripts/run-containers-controller.sh  | 1 namesrv, 1 controller, 2 group 1m1s, 2 proxy     |
| scripts/run-containers.sh             | 1 namesrv, 2 group 1m1s                            |
| scripts/run-containers-raft.sh        | 1 namesrv, 1 group dLeger(3)                       |
| scripts/run-containers-local-proxy.sh | 1 namesrv, 1 controller, 2 group 1m1s, local proxy |

## 文档

项目文档位于 [`docs/`](docs/README.md)：

- [`docs/local-development.md`](docs/local-development.md) — Vagrant 虚拟机拓扑、broker 脚本、如何启动所有测试依赖的运行环境。
- [`docs/modules.md`](docs/modules.md) — 9 个 Maven 模块、依赖矩阵、各模块覆盖的内容。
- [`docs/testing.md`](docs/testing.md) — 各模块的测试矩阵、关键模式（原生 V4 push consumer、delay 测试的 body marker 过滤、V5 SimpleConsumer 长轮询）、如何运行测试。
- [`docs/test-support.md`](docs/test-support.md) — `test-support` 共享模块（`Addresses`、`Topics`、`Groups`、`MessageFactory`、`ReceiptAwaiter`）。
- [`docs/formatting.md`](docs/formatting.md) — Spotless + google-java-format 配置说明。
- [`docs/ci.md`](docs/ci.md) — GitHub Actions 流水线。

## 虚拟机拓扑

`Vagrantfile` 定义了 3 台 VirtualBox 虚拟机，但**它们是"多种 RocketMQ 部署拓扑的替代实现"，不是"一个集群的三台机器"**——任意时刻只需要 1 台在跑就能完整覆盖所有 POC 模块。

| 主机名   | IP             | 配套脚本                                              | 跑的拓扑                                            |
| -------- | -------------- | ----------------------------------------------------- | --------------------------------------------------- |
| `server` | 192.168.88.123 | `run-containers-controller.sh` 或 `run-containers.sh` | controller 模式（含 proxy，最完整）/ 传统 2 组 1m1s |
| `raft`   | 192.168.88.128 | `run-containers-raft.sh`                              | DLedger Raft 模式                                   |
| `r1`     | 192.168.88.129 | （无）                                                | 备用                                                |

### 为什么可以一台一台起

- **模块代码只硬编码 `192.168.88.123`**：所有 `spring-cloud-*` / `sbs` / `client` 模块都用 namesrv `9876`；`sbs-v5` / `client-v5` 用 proxy `8181`。**没有任何模块引用 192.168.88.128 或 192.168.88.129**。
- **三台 VM 各自独立**：每台 VM 内部用 docker network `mynet` 把 namesrv / broker / controller / proxy 互联，三台 VM 之间不通讯。
- **资源占用**：单台 VM（按 Vagrantfile 原配置 16 GB / 4 vCPU）启动后约 12-14 GB resident，主机内存够（125 GB 总内存 / 51 GB available）。三台同时跑会触发 OOM。

### 推荐操作流程

```bash
# 1. 启动 server（默认 + controller 拓扑，能跑所有模块）
vagrant up server
vagrant ssh server

# 2. 在 server 上跑 controller 拓扑（含 proxy，支持 sbs-v5 / client-v5）
./scripts/run-containers-controller.sh

# 3. 测完想换 raft 拓扑？先销毁 server 再起 raft（不销毁的话两台 VM 共占约 32 GB，会很紧）
exit
vagrant destroy -f server
vagrant up raft
vagrant ssh raft
./scripts/run-containers-raft.sh
```

### 切换拓扑时的关键步骤

| 想测什么                                            | 在哪台 VM | 跑哪个脚本                                            | 模块代码需要改什么                                                      |
| --------------------------------------------------- | --------- | ----------------------------------------------------- | ----------------------------------------------------------------------- |
| spring-cloud-v2021/v2023/v2025/v2025-1、sbs、client | server    | `run-containers.sh` 或 `run-containers-controller.sh` | 无                                                                      |
| sbs-v5、client-v5（需要 Proxy gRPC）                | server    | `run-containers-controller.sh`                        | 无                                                                      |
| raft 主从选举验证                                   | raft      | `run-containers-raft.sh`                              | 把 namesrv 地址改成 `192.168.88.128:9876`（运行时参数即可，源码可不动） |

## 2m2s

```bash
root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin clusterList -n n1:9876
#Cluster Name           #Broker Name            #BID  #Addr                  #Version              #InTPS(LOAD)     #OutTPS(LOAD)  #Timer(Progress)        #PCWait(ms)  #Hour         #SPACE    #ACTIVATED
DefaultCluster          q1               0     192.168.88.123:11911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  0-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100          true
DefaultCluster          q1               1     192.168.88.123:12911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  2-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100         false
DefaultCluster          q3               0     192.168.88.123:13911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  1-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100          true
DefaultCluster          q3               1     192.168.88.123:14911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  3-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100         false


root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin updateTopic -n n1:9876 -c DefaultCluster -t hello -r 3 -w 3
create topic to 192.168.88.123:11911 success.
create topic to 192.168.88.123:13911 success.
TopicConfig [topicName=hello, readQueueNums=3, writeQueueNums=3, perm=RW-, topicFilterType=SINGLE_TAG, topicSysFlag=0, order=false, attributes={}]


root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin topicStatus -n n1:9876 -t hello
#Broker Name                      #QID  #Min Offset           #Max Offset             #Last Updated
q1                         0     0                     0                       
q1                         1     0                     0                       
q1                         2     0                     0                       
q3                         0     0                     0                       
q3                         1     0                     0                       
q3                         2     0                     0                       

root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin updateTopic -n n1:9876 -c DefaultCluster -t my -r 2 -w 2
create topic to 192.168.88.123:11911 success.
create topic to 192.168.88.123:13911 success.
TopicConfig [topicName=my, readQueueNums=2, writeQueueNums=2, perm=RW-, topicFilterType=SINGLE_TAG, topicSysFlag=0, order=false, attributes={}]

root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin topicStatus -n n1:9876 -t my
#Broker Name                      #QID  #Min Offset           #Max Offset             #Last Updated
q1                         0     0                     0                       
q1                         1     0                     0                       
q3                         0     0                     0                       
q3                         1     0                     0                       


mqadmin updateTopic -n n1:9876 -c DefaultCluster -t book -r 1 -w 1
create topic to 192.168.88.123:11911 success.
create topic to 192.168.88.123:13911 success.

root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin topicStatus -n n1:9876 -t book
#Broker Name                      #QID  #Min Offset           #Max Offset             #Last Updated
q1                         0     0                     0                       
q3                         0     0                     0                       
```

`-w [arg] -r [arg]` for every broker instance, broker default `defaultTopicQueueNums=8`.

## 2group-raft

> 1group-raft ≈ 1m2s

```bash
root@c48216352093:/opt/rocketmq/bin# mqadmin clusterList -n n1:9876
#Cluster Name           #Broker Name            #BID  #Addr                  #Version              #InTPS(LOAD)     #OutTPS(LOAD)  #Timer(Progress)        #PCWait(ms)  #Hour         #SPACE    #ACTIVATED
RaftCluster             RaftNode00              0     192.168.88.128:11911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  0-0(0.0w, 0.0, 0.0)               0  480099.47     0.0100          true
RaftCluster             RaftNode00              2     192.168.88.128:12911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  1-0(0.0w, 0.0, 0.0)               0  480099.47     0.0100         false
RaftCluster             RaftNode00              3     192.168.88.128:13911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  2-0(0.0w, 0.0, 0.0)               0  480099.47     0.0100         false
RaftCluster             RaftNode01              0     192.168.88.128:14911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  0-0(0.0w, 0.0, 0.0)               0  480099.47     0.0100          true
RaftCluster             RaftNode01              2     192.168.88.128:15911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  3-0(0.0w, 0.0, 0.0)               0  480099.47     0.0100         false
RaftCluster             RaftNode01              3     192.168.88.128:16911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  604757-0(0.0w, 0.0, 0.0)            0  480099.47     0.0100         false
```

## conclusion

- replication= slave+1
- 数据安全 = 多副本机制(ms或者raft,raft实质上也是ms,与ms不同的是实现了自动选举leader,可以理解为动态的ms)
- 高吞吐 ≈ 分区负载 = 多组集群
- 高可用 = （多组ms或者多组raft）&& master保活
- 同一组内ms或者同一组内raft,brokerName相同,brokerId不同,也就是说一组ms（或者raft)对外身份(brokerName)一致，对内身份(brokerId)不同

| 模式                                          | 说明                                                                                                                                                                                                                                                                                                                                                                                                             |
| --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 单组节点单副本模式                            |                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 多组节点（集群）单副本模式                    | 一个集群内全部部署 Master 角色，不部署Slave 副本，例如2个Master或者3个Master，这种模式的优缺点如下： <br/>优点：配置简单，单个Master宕机或重启维护对应用无影响，在磁盘配置为RAID10时，即使机器宕机不可恢复情况下，由于RAID10磁盘非常可靠，消息也不会丢（异步刷盘丢失少量消息，同步刷盘一条不丢），性能最高； <br/>缺点：单台机器宕机期间，这台机器上未被消费的消息在机器恢复之前不可订阅，消息实时性会受到影响。 |
| ~~多节点~~多组节点（集群）多副本模式-异步复制 | 每个Master配置一个Slave，有多组 Master-Slave，HA采用异步复制方式，主备有短暂消息延迟（毫秒级），这种模式的优缺点如下：  <br/>优点：即使磁盘损坏，消息丢失的非常少，且消息实时性不会受影响，同时Master宕机后，消费者仍然可以从Slave消费，而且此过程对应用透明，不需要人工干预，性能同多Master模式几乎一样；  <br/>缺点：Master宕机，磁盘损坏情况下会丢失少量消息。                                                |
| ~~多节点~~多组节点（集群）多副本模式-同步双写 | 每个Master配置一个Slave，有~~多对~~多组 Master-Slave，HA采用同步双写方式，即只有主备都写成功，才向应用返回成功，这种模式的优缺点如下：  <br/>优点：数据与服务都无单点故障，Master宕机情况下，消息无延迟，服务可用性与数据可用性都非常高；  <br/>缺点：性能比异步复制模式略低（大约低10%左右），发送单个消息的RT会略高，且目前版本在主节点宕机后，备机不能自动切换为主机。                                        |


## spring cloud spring boot version matrix

> https://spring.io/projects/spring-cloud


Table 1. Release train Spring Boot compatibility (see here for more detailed information).

| Release Train            | Spring Boot Generation                |
| ------------------------ | ------------------------------------- |
| 2025.1.x aka Oakwood     | 4.0.x                                 |
| 2025.0.x aka Northfields | 3.5.x                                 |
| 2024.0.x aka Moorgate    | 3.4.x                                 |
| 2023.0.x aka Leyton      | 3.3.x, 3.2.x                          |
| 2022.0.x aka Kilburn     | 3.0.x, 3.1.x (Starting with 2022.0.3) |
| 2021.0.x aka Jubilee     | 2.6.x, 2.7.x (Starting with 2021.0.3) |
| 2020.0.x aka Ilford      | 2.4.x, 2.5.x (Starting with 2020.0.3) |
| Hoxton                   | 2.2.x, 2.3.x (Starting with SR5)      |
| Greenwich                | 2.1.x                                 |
| Finchley                 | 2.0.x                                 |
| Edgware                  | 1.5.x                                 |
| Dalston                  | 1.5.x                                 |



## ref
- <https://rocketmq.apache.org/zh/docs/deploymentOperations/01deploy/#cluster%E6%A8%A1%E5%BC%8F%E9%83%A8%E7%BD%B2>
- <https://github.com/TencentCloud/rocketmq-demo>
- <https://github.com/apache/rocketmq-spring>
- <https://github.com/apache/rocketmq-clients>
- <https://github.com/alibaba/spring-cloud-alibaba>
- <https://github.com/apache/rocketmq>
