# springcloud-rocketmq-example

POC new features of rocketmq5

[English](README.md) | [中文](README.zh-CN.md)

| module               | desc                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------ |
| spring-cloud-v2025-1 | spring-boot=4.1.0  <br/> spring-cloud=2025.1.2 <br/> spring-cloud-alibaba=2025.1.0   |
| spring-cloud-v2025   | spring-boot=3.5.9  <br/> spring-cloud=2025.0.1 <br/> spring-cloud-alibaba=2025.0.0   |
| spring-cloud-v2023   | spring-boot=3.3.13 <br/> spring-cloud=2023.0.6 <br/> spring-cloud-alibaba=2023.0.3.4 |
| spring-cloud-v2021   | spring-boot=2.7.18 <br/> spring-cloud=2021.0.9 <br/> spring-cloud-alibaba=2021.0.6.0 |
| sbs                  | spring-boot=3.5.9  <br/> rocketmq-spring-boot-starter=2.3.6                          |
| sbs-v5               | spring-boot=3.5.9  <br/> rocketmq-v5-client-spring-boot-starter=2.3.6                |
| client               | rocketmq-client=5.5.0                                                                |
| client-v5            | rocketmq-client-java=5.2.1                                                           |

| scripts                               | desc                                               |
| ------------------------------------- | -------------------------------------------------- |
| scripts/run-containers-controller.sh  | 1 namesrv, 1 controller, 2 group 1m1s, 2 proxy     |
| scripts/run-containers.sh             | 1 namesrv, 2 group 1m1s                            |
| scripts/run-containers-raft.sh        | 1 namesrv, 1 group dLeger(3)                       |
| scripts/run-containers-local-proxy.sh | 1 namesrv, 1 controller, 2 group 1m1s, local proxy |

## VM Topology

The `Vagrantfile` defines 3 VirtualBox VMs, but **they are alternative RocketMQ deployment topologies, NOT 3 machines forming one cluster** — at any moment you only need one of them running to cover all POC modules.

| Hostname | IP             | Companion script                                      | Topology it runs                                                       |
| -------- | -------------- | ----------------------------------------------------- | ---------------------------------------------------------------------- |
| `server` | 192.168.88.123 | `run-containers-controller.sh` or `run-containers.sh` | Controller mode (with proxy, most complete) / classic 2 groups of 1m1s |
| `raft`   | 192.168.88.128 | `run-containers-raft.sh`                              | DLedger Raft mode                                                      |
| `r1`     | 192.168.88.129 | (none)                                                | Spare                                                                  |

### Why one VM at a time is enough

- **Module code only hard-codes `192.168.88.123`**: every `spring-cloud-*` / `sbs` / `client` module talks to namesrv on `9876`; `sbs-v5` / `client-v5` talk to proxy on `8181`. **No module references 192.168.88.128 or 192.168.88.129.**
- **Each VM is self-contained**: namesrv / broker / controller / proxy live inside one VM on the docker network `mynet`. The three VMs never talk to each other.
- **Resource footprint**: a single VM (per Vagrantfile default of 16 GB / 4 vCPU) needs ~12-14 GB resident after boot, which fits comfortably on a 125 GB host with 51 GB available. Running all three simultaneously will OOM the host.

### Recommended workflow

```bash
# 1. Boot server (default + controller topology, covers every module)
vagrant up server
vagrant ssh server

# 2. On server, run controller topology (includes proxy, supports sbs-v5 / client-v5)
./scripts/run-containers-controller.sh

# 3. Want to try raft next? Destroy server first, then bring up raft
#    (running both at once would consume ~32 GB and get tight)
exit
vagrant destroy -f server
vagrant up raft
vagrant ssh raft
./scripts/run-containers-raft.sh
```

### Topology-switch cheat sheet

| What you want to test                                     | On which VM | Run which script                                      | Source code change required                                              |
| --------------------------------------------------------- | ----------- | ----------------------------------------------------- | ------------------------------------------------------------------------ |
| spring-cloud-v2021 / v2023 / v2025 / v2025-1, sbs, client | server      | `run-containers.sh` or `run-containers-controller.sh` | none                                                                     |
| sbs-v5, client-v5 (need Proxy gRPC)                       | server      | `run-containers-controller.sh`                        | none                                                                     |
| Raft leader election verification                         | raft        | `run-containers-raft.sh`                              | change namesrv addr to `192.168.88.128:9876` (runtime arg, source stays) |

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

## Conclusion

- replication = slave+1
- **Data safety** = multi-replica mechanism (master-slave or Raft; Raft is essentially master-slave under the hood, but adds automatic leader election — think of it as a dynamic master-slave)
- **High throughput** ≈ partition load balancing = multiple broker groups
- **High availability** = (multiple master-slave groups OR multiple Raft groups) AND master liveness
- Within one master-slave (or one Raft) group, `brokerName` is the same and `brokerId` differs — i.e. one group shares the same external identity (`brokerName`) but has distinct internal identities (`brokerId`).

| Mode                                                       | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Single-group, single-replica                               |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| Multi-group (cluster), single-replica                      | All nodes in the cluster run as Master, with no Slave replicas — e.g. 2 or 3 Masters. <br/>**Pros:** Simple config. A single Master restart or downtime has no impact on applications. With RAID10 disks, even an unrecoverable machine failure loses no messages (a tiny amount with async flush, zero with sync flush). Highest performance. <br/>**Cons:** While a machine is down, the unconsumed messages on it cannot be consumed until the machine recovers, so message real-timeliness is affected.                                                                                                                                                                                                                                                                                              |
| ~~Multi-node~~ Multi-group (cluster), multi-replica — async replication | Each Master has one Slave, multiple groups of Master-Slave, HA uses async replication, master and slave have brief (millisecond) lag. <br/>**Pros:** Even on disk damage, very few messages are lost and message real-timeliness is unaffected. When a Master is down, consumers can still consume from the Slave, transparently to the application, no human intervention needed. Performance is on par with the multi-Master mode. <br/>**Cons:** A small number of messages may be lost when a Master is down AND the disk is corrupted.                                                                                                                                                                                                                                                                  |
| ~~Multi-node~~ Multi-group (cluster), multi-replica — sync dual-write | Each Master has one Slave, multiple groups of Master-Slave, HA uses sync dual-write — the application receives success only after both master and slave have written. <br/>**Pros:** No single point of failure for either data or service. When a Master is down, no message delay, service availability and data availability are both very high. <br/>**Cons:** Performance is slightly lower than async replication (~10% lower), single-message send RT is slightly higher, and in the current version the slave cannot automatically take over as the new master after the master fails.                                                                                                                                                                                                |


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
