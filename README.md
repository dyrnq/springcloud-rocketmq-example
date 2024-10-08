# rocketmq

## 2m2s

```bash
root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin clusterList -n mqnamesrv1:9876
#Cluster Name           #Broker Name            #BID  #Addr                  #Version              #InTPS(LOAD)     #OutTPS(LOAD)  #Timer(Progress)        #PCWait(ms)  #Hour         #SPACE    #ACTIVATED
DefaultCluster          mqbroker1               0     192.168.88.123:11911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  0-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100          true
DefaultCluster          mqbroker1               1     192.168.88.123:12911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  2-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100         false
DefaultCluster          mqbroker3               0     192.168.88.123:13911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  1-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100          true
DefaultCluster          mqbroker3               1     192.168.88.123:14911   V5_3_0                 0.00(0,0ms)       0.00(0,0ms)  3-0(0.0w, 0.0, 0.0)               0  480097.14     0.0100         false


root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin updateTopic -n mqnamesrv1:9876 -c DefaultCluster -t hello -r 3 -w 3
create topic to 192.168.88.123:11911 success.
create topic to 192.168.88.123:13911 success.
TopicConfig [topicName=hello, readQueueNums=3, writeQueueNums=3, perm=RW-, topicFilterType=SINGLE_TAG, topicSysFlag=0, order=false, attributes={}]


root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin topicStatus -n mqnamesrv1:9876 -t hello
#Broker Name                      #QID  #Min Offset           #Max Offset             #Last Updated
mqbroker1                         0     0                     0                       
mqbroker1                         1     0                     0                       
mqbroker1                         2     0                     0                       
mqbroker3                         0     0                     0                       
mqbroker3                         1     0                     0                       
mqbroker3                         2     0                     0                       

root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin updateTopic -n mqnamesrv1:9876 -c DefaultCluster -t my -r 2 -w 2
create topic to 192.168.88.123:11911 success.
create topic to 192.168.88.123:13911 success.
TopicConfig [topicName=my, readQueueNums=2, writeQueueNums=2, perm=RW-, topicFilterType=SINGLE_TAG, topicSysFlag=0, order=false, attributes={}]

root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin topicStatus -n mqnamesrv1:9876 -t my
#Broker Name                      #QID  #Min Offset           #Max Offset             #Last Updated
mqbroker1                         0     0                     0                       
mqbroker1                         1     0                     0                       
mqbroker3                         0     0                     0                       
mqbroker3                         1     0                     0                       


mqadmin updateTopic -n mqnamesrv1:9876 -c DefaultCluster -t book -r 1 -w 1
create topic to 192.168.88.123:11911 success.
create topic to 192.168.88.123:13911 success.

root@f2b36aa77e34:/opt/rocketmq/bin# mqadmin topicStatus -n mqnamesrv1:9876 -t book
#Broker Name                      #QID  #Min Offset           #Max Offset             #Last Updated
mqbroker1                         0     0                     0                       
mqbroker3                         0     0                     0                       
```

## 2group-raft

> 1group-raft ≈ 1m2s

```bash
root@c48216352093:/opt/rocketmq/bin# mqadmin clusterList -n mqnamesrv1:9876
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
- 高吞吐= 多组ms或者多组raft
- 高可用= (多组ms或者多组raft) && master保活
- 同一组内ms或者同一组内raft,brokerName相同,brokerId不同,也就是说一组ms（或者raft)对外身份(brokerName)一致，对内身份(brokerId)不同
