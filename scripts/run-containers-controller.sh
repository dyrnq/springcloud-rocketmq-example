#!/usr/bin/env bash
# shellcheck disable=SC2086


iface="${iface:-enp0s8}"


wait4x_image="${wait4x_image:-atkrad/wait4x:2.12}"
rocketmq_image="${rocketmq_image:-dyrnq/rocketmq:5.3.0-jdk21}"
proxy="${proxy:-}"

while [ $# -gt 0 ]; do
    case "$1" in
        --iface|-i)
            iface="$2"
            shift
            ;;
        --proxy)
            proxy="$2"
            shift
            ;;
        --*)
            echo "Illegal option $1"
            ;;
    esac
    shift $(( $# > 0 ? 1 : 0 ))
done

ip4=$(/sbin/ip -o -4 addr list "${iface}" | awk '{print $4}' |cut -d/ -f1 | head -n1);



command_exists() {
  command -v "$@" > /dev/null 2>&1
}



fun_add_mynet(){
  docker network inspect mynet &>/dev/null || docker network create --subnet 172.18.0.0/16 --gateway 172.18.0.1 --driver bridge mynet
}


fun_install_rocketmq(){

docker rm -f n1 2>/dev/null || true

mkdir -p $HOME/var/lib/n1/logs
mkdir -p $HOME/var/lib/n1/store

chown -R 3000:3000 $HOME/var/lib/n1 2>/dev/null || sudo chown -R 3000:3000 $HOME/var/lib/n1

docker run -d \
--name n1 \
--restart always \
-e TZ="Asia/Shanghai" \
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -XX:+IgnoreUnrecognizedVMOptions -XX:-UseG1GC -XX:+UseZGC -XX:+PrintFlagsFinal -Xms1g -Xmx1g -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/n1/logs:/home/rocketmq/logs \
-v $HOME/var/lib/n1/store:/home/rocketmq/store \
-p 9876:9876 \
--network mynet \
${rocketmq_image} mqnamesrv



docker rm -f c1 2>/dev/null || true

mkdir -p $HOME/var/lib/c1/logs
mkdir -p $HOME/var/lib/c1/store
mkdir -p $HOME/var/lib/c1/DLedgerController

chown -R 3000:3000 $HOME/var/lib/c1 2>/dev/null || sudo chown -R 3000:3000 $HOME/var/lib/c1

cat >$HOME/c1.conf<<EOF
controllerDLegerGroup = group1
controllerDLegerPeers = n0-${ip4}:9878
controllerDLegerSelfId = n0
EOF

docker run -d \
--name c1 \
--restart always \
-e TZ="Asia/Shanghai" \
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -XX:+IgnoreUnrecognizedVMOptions -XX:-UseG1GC -XX:+UseZGC -XX:+PrintFlagsFinal -Xms1g -Xmx1g -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/c1/logs:/home/rocketmq/logs \
-v $HOME/var/lib/c1/store:/home/rocketmq/store \
-v $HOME/var/lib/c1/DLedgerController:/home/rocketmq/DLedgerController \
-v $HOME/c1.conf:/etc/rocketmq/controller.conf \
-p 9878:9878 \
--network mynet \
${rocketmq_image} mqcontroller -c /etc/rocketmq/controller.conf


cat > /tmp/broker.sed<<EOF
brokerClusterName=DefaultCluster
brokerName=_BROKERNAME
brokerId=0
namesrvAddr=_NAMESRVADDR
autoCreateTopicEnable=true
autoCreateSubscriptionGroup=true
listenPort=10911
# Broker的角色
# ASYNC_MASTER 异步复制Master
# SYNC_MASTER 同步双写Master
# SLAVE
brokerRole=ASYNC_MASTER
# 刷盘方式
# ASYNC_FLUSH 异步刷盘
# SYNC_FLUSH 同步刷盘
flushDiskType=ASYNC_FLUSH
waitTimeMillsInSendQueue=600
brokerIP1=${ip4}
#messageDelayLevel=1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
messageDelayLevel=1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h 20s
EOF

fastListenPort="10909"
listenPort="10911"
haListenPort="10912"

for i in {1..4} ; do
name="q${i}";
broker_name="q${i}";
brokerId=-1;
brokerRole="SLAVE"
if (( i % 2 == 0 )); then
  broker_name="q$((i-1))";
fi

next_fastListenPort=$((fastListenPort+1000*i))
next_listenPort=$((listenPort+1000*i))
next_haListenPort=$((haListenPort+1000*i))
sed \
-e "s@_BROKERNAME@${broker_name}@g" \
-e "s@10911@${next_listenPort}@g" \
-e "s@brokerId=0@brokerId=${brokerId}@g" \
-e "s@brokerRole=ASYNC_MASTER@brokerRole=${brokerRole}@g" \
-e "s@_NAMESRVADDR@n1:9876@" /tmp/broker.sed > $HOME/$name.conf

## https://rocketmq.apache.org/zh/docs/deploymentOperations/03autofailover#broker-%E9%83%A8%E7%BD%B2
(
echo ""
echo "enableControllerMode = true"
echo "controllerAddr = c1:9878"
echo "allAckInSyncStateSet=true"
echo "#syncBrokerMetadataPeriod=5000"
echo ""
) >> $HOME/$name.conf

docker rm -f $name 2>/dev/null || true

mkdir -p $HOME/var/lib/$name/logs
mkdir -p $HOME/var/lib/$name/store

chown -R 3000:3000 $HOME/var/lib/$name 2>/dev/null || sudo chown -R 3000:3000 $HOME/var/lib/$name
docker run -d \
--name $name \
--restart always \
-e TZ="Asia/Shanghai" \
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -XX:+IgnoreUnrecognizedVMOptions -XX:-UseG1GC -XX:+UseZGC -XX:+PrintFlagsFinal -Xms1g -Xmx1g -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/$name/logs:/home/rocketmq/logs \
-v $HOME/var/lib/$name/store:/home/rocketmq/store \
-v $HOME/$name.conf:/etc/rocketmq/broker.conf \
-p ${next_fastListenPort}:${next_fastListenPort} \
-p ${next_listenPort}:${next_listenPort} \
-p ${next_haListenPort}:${next_haListenPort} \
--network mynet \
${rocketmq_image} mqbroker -c /etc/rocketmq/broker.conf
done
}


fun_install_rocketmq_proxy(){


#########################  setup 2 instances proxy ##############################
grpcServerPort=8081
remotingListenPort=8080
for i in {1..2} ; do
name="p${i}";

next_grpcServerPort=$((grpcServerPort+100*i))
next_remotingListenPort=$((remotingListenPort+100*i))

(
echo "{"
echo "\"namesrvAddr\": \"n1:9876\","
echo "\"rocketMQClusterName\": \"DefaultCluster\","
echo "\"proxyClusterName\": \"DefaultCluster\","
echo "\"grpcServerPort\": ${next_grpcServerPort},"
echo "\"remotingListenPort\": ${next_remotingListenPort}"
echo "}"
) > $HOME/$name.json
cat < $HOME/$name.json
docker rm -f $name 2>/dev/null || true

mkdir -p $HOME/var/lib/$name/logs
mkdir -p $HOME/var/lib/$name/store

chown -R 3000:3000 $HOME/var/lib/$name 2>/dev/null || sudo chown -R 3000:3000 $HOME/var/lib/$name
docker run -d \
--name $name \
--restart always \
-e TZ="Asia/Shanghai" \
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -XX:+IgnoreUnrecognizedVMOptions -XX:-UseG1GC -XX:+UseZGC -XX:+PrintFlagsFinal -Xms1g -Xmx1g -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/$name/logs:/home/rocketmq/logs \
-v $HOME/var/lib/$name/store:/home/rocketmq/store \
-v $HOME/$name.json:/etc/rocketmq/proxyConfig.json \
-p ${next_grpcServerPort}:${next_grpcServerPort} \
-p ${next_remotingListenPort}:${next_remotingListenPort} \
--network mynet \
${rocketmq_image} mqproxy --proxyConfigPath /etc/rocketmq/proxyConfig.json
done

}

fun_updateTopic(){

#  ./mqadmin updateTopic -c DefaultCluster -t delayTopic -n n1:9876 -a +message.type=DELAY
#  ./mqadmin updateTopic -c DefaultCluster -t fifoTopic -n n1:9876 -a +message.type=FIFO
#  ./mqadmin updateTopic -c DefaultCluster -t normalTopic -n n1:9876 -a +message.type=NORMAL
#  ./mqadmin updateTopic -c DefaultCluster -t transTopic -n n1:9876 -a +message.type=TRANSACTION
#  ./mqadmin updateTopic -c DefaultCluster -t demo-topic -n n1:9876 -a +message.type=NORMAL

docker exec -i q1 bash -c "./mqadmin updateTopic -c DefaultCluster -t delayTopic -n n1:9876 -a +message.type=DELAY"
docker exec -i q1 bash -c "./mqadmin updateTopic -c DefaultCluster -t fifoTopic -n n1:9876 -a +message.type=FIFO"
docker exec -i q1 bash -c "./mqadmin updateTopic -c DefaultCluster -t normalTopic -n n1:9876 -a +message.type=NORMAL"
docker exec -i q1 bash -c "./mqadmin updateTopic -c DefaultCluster -t transTopic -n n1:9876 -a +message.type=TRANSACTION"
docker exec -i q1 bash -c "./mqadmin updateTopic -c DefaultCluster -t demo-topic -n n1:9876 -a +message.type=NORMAL"
}

fun_install_rocketmq_dashboard(){
#docker rm -f w1 2>/dev/null || true
#docker run -d \
#--name w1 \
#--restart always \
#--network mynet \
#-e JAVA_OPTS="-Drocketmq.namesrv.addr=n1:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false" \
#-p 28080:8080 \
#styletang/rocketmq-console-ng


dashboard_url="https://repo.maven.apache.org/maven2/org/apache/rocketmq/rocketmq-dashboard/2.0.0/rocketmq-dashboard-2.0.0.jar"
dashboard_url="${dashboard_url/repo.maven.apache.org\/maven2/repo.huaweicloud.com/repository/maven}"

curl \
--retry 5 \
-o $HOME/rocketmq-dashboard-2.0.0.jar \
-fSL -# \
"${dashboard_url}"

docker rm -f w1 2>/dev/null || true;
docker run -d \
--name w1 \
--restart always \
--network mynet \
-v $HOME:/data \
-p 28080:8080 \
--entrypoint="" \
eclipse-temurin:8u422-b05-jre-noble \
java -jar /data/rocketmq-dashboard-2.0.0.jar \
--rocketmq.config.namesrvAddrs=n1:9876 \
--rocketmq.config.namesrvAddr=n1:9876
}

fun_add_mynet
fun_install_rocketmq
echo "sleep 5s" && sleep 5s;
fun_install_rocketmq_proxy
fun_install_rocketmq_dashboard
fun_updateTopic
