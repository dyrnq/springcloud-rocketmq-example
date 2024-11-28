# springcloud-rocketmq-example

POC new features of rocketmq5

| module             | desc                                                                                 |
|--------------------|--------------------------------------------------------------------------------------|
| spring-cloud-v2023 | spring-boot=3.4.0  <br/> spring-cloud=2023.0.4 <br/> spring-cloud-alibaba=2023.0.1.3 |
| spring-cloud-v2021 | spring-boot=2.7.18 <br/> spring-cloud=2021.0.9 <br/> spring-cloud-alibaba=2021.0.6.0 |
| sbs                | spring-boot=3.4.0  <br/> rocketmq-spring-boot-starter=2.3.1                          |
| sbs-v5             | spring-boot=3.4.0  <br/> rocketmq-v5-client-spring-boot-starter=2.3.1                |
| client             | rocketmq-client=5.3.1                                                                |
| client-v5          | rocketmq-client-java=5.0.7                                                           |

| scripts                               | desc                                               |
|---------------------------------------|----------------------------------------------------|
| scripts/run-containers-controller.sh  | 1 namesrv, 1 controller, 2 group 1m1s, 2 proxy     |
| scripts/run-containers.sh             | 1 namesrv, 2 group 1m1s                            |
| scripts/run-containers-raft.sh        | 1 namesrv, 1 group dLeger(3)                       |
| scripts/run-containers-local-proxy.sh | 1 namesrv, 1 controller, 2 group 1m1s, local proxy |
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

| 模式                              | 说明                                                                                                                                                                                                                                                                                                                                                                                                             |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 单组节点单副本模式                |                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 多组节点（集群）单副本模式        | 一个集群内全部部署 Master 角色，不部署Slave 副本，例如2个Master或者3个Master，这种模式的优缺点如下： <br/>优点：配置简单，单个Master宕机或重启维护对应用无影响，在磁盘配置为RAID10时，即使机器宕机不可恢复情况下，由于RAID10磁盘非常可靠，消息也不会丢（异步刷盘丢失少量消息，同步刷盘一条不丢），性能最高； <br/>缺点：单台机器宕机期间，这台机器上未被消费的消息在机器恢复之前不可订阅，消息实时性会受到影响。 |
| ~~多节点~~多组节点（集群）多副本模式-异步复制 | 每个Master配置一个Slave，有多组 Master-Slave，HA采用异步复制方式，主备有短暂消息延迟（毫秒级），这种模式的优缺点如下：  <br/>优点：即使磁盘损坏，消息丢失的非常少，且消息实时性不会受影响，同时Master宕机后，消费者仍然可以从Slave消费，而且此过程对应用透明，不需要人工干预，性能同多Master模式几乎一样；  <br/>缺点：Master宕机，磁盘损坏情况下会丢失少量消息。                                                |
| ~~多节点~~多组节点（集群）多副本模式-同步双写 | 每个Master配置一个Slave，有~~多对~~多组 Master-Slave，HA采用同步双写方式，即只有主备都写成功，才向应用返回成功，这种模式的优缺点如下：  <br/>优点：数据与服务都无单点故障，Master宕机情况下，消息无延迟，服务可用性与数据可用性都非常高；  <br/>缺点：性能比异步复制模式略低（大约低10%左右），发送单个消息的RT会略高，且目前版本在主节点宕机后，备机不能自动切换为主机。                                                |



## ref
- <https://rocketmq.apache.org/zh/docs/deploymentOperations/01deploy/#cluster%E6%A8%A1%E5%BC%8F%E9%83%A8%E7%BD%B2>
- <https://github.com/TencentCloud/rocketmq-demo>
- <https://github.com/apache/rocketmq-spring>
- <https://github.com/apache/rocketmq-clients>
- <https://github.com/alibaba/spring-cloud-alibaba>
- <https://github.com/apache/rocketmq>
