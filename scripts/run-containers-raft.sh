#!/usr/bin/env bash
# shellcheck disable=SC2086


iface="${iface:-enp0s8}"


wait4x_image="${wait4x_image:-atkrad/wait4x:2.12}"
rocketmq_image="${rocketmq_image:-dyrnq/rocketmq:5.3.0}"
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
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -Xms512m -Xmx512m -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/n1/logs:/home/rocketmq/logs \
-v $HOME/var/lib/n1/store:/home/rocketmq/store \
-p 9876:9876 \
--network mynet \
${rocketmq_image} mqnamesrv



cat > /tmp/broker.sed<<EOF
brokerClusterName=RaftCluster
brokerName=_BROKERNAME
listenPort=10911
namesrvAddr=_NAMESRVADDR
enableDLegerCommitLog=true
dLegerGroup=_BROKERNAME
dLegerPeers=
dLegerSelfId=
sendMessageThreadPoolNums=16
waitTimeMillsInSendQueue=600
brokerIP1=${ip4}
EOF

fastListenPort="10909"
listenPort="10911"
haListenPort="10912"

k=0;
for i in {1..6} ; do
name="q${i}";
broker_name="RaftNode00";



broker_name="RaftNode0${k}";
dLegerSelfId="n$((i-1-3*k))";
dLegerPeers="n0-${ip4}:$(((3*k+1)*1000 + haListenPort));n1-${ip4}:$(((3*k+2)*1000 + haListenPort));n2-${ip4}:$(((3*k+3)*1000 + haListenPort))"
# 11912,12912,13912
# 14912,15912,16912

if (( i % 3 == 0 )); then
  k=$((k+1))
fi





next_fastListenPort=$((fastListenPort+1000*i))
next_listenPort=$((listenPort+1000*i))
next_haListenPort=$((haListenPort+1000*i))
sed \
-e "s@_BROKERNAME@${broker_name}@g" \
-e "s@10911@${next_listenPort}@g" \
-e "s@dLegerPeers=@dLegerPeers=${dLegerPeers}@g" \
-e "s@dLegerSelfId=@dLegerSelfId=${dLegerSelfId}@g" \
-e "s@_NAMESRVADDR@n1:9876@" /tmp/broker.sed > $HOME/$name.conf


docker rm -f $name 2>/dev/null || true

mkdir -p $HOME/var/lib/$name/logs
mkdir -p $HOME/var/lib/$name/store

chown -R 3000:3000 $HOME/var/lib/$name 2>/dev/null || sudo chown -R 3000:3000 $HOME/var/lib/$name
docker run -d \
--name $name \
--restart always \
-e TZ="Asia/Shanghai" \
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -Xms1g -Xmx1g -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/$name/logs:/home/rocketmq/logs \
-v $HOME/var/lib/$name/store:/home/rocketmq/store \
-v $HOME/$name.conf:/etc/rocketmq/broker.conf \
-p ${next_fastListenPort}:${next_fastListenPort} \
-p ${next_listenPort}:${next_listenPort} \
-p ${next_haListenPort}:${next_haListenPort} \
--network mynet \
${rocketmq_image} mqbroker -c /etc/rocketmq/broker.conf
done

docker rm -f w1 2>/dev/null || true
docker run -d \
--name w1 \
--restart always \
--network mynet \
-e JAVA_OPTS="-Drocketmq.namesrv.addr=n1:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false" \
-p 28080:8080 \
styletang/rocketmq-console-ng
}


fun_add_mynet
fun_install_rocketmq
