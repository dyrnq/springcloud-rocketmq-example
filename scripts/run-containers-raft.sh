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

docker rm -f mqnamesrv1 2>/dev/null || true

mkdir -p $HOME/var/lib/mqnamesrv1/logs
mkdir -p $HOME/var/lib/mqnamesrv1/store

chown -R 3000:3000 $HOME/var/lib/mqnamesrv1 || sudo chown -R 3000:3000 $HOME/var/lib/mqnamesrv1

docker run -d \
--name mqnamesrv1 \
--restart always \
-e TZ="Asia/Shanghai" \
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -Xms512m -Xmx512m -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/mqnamesrv1/logs:/home/rocketmq/logs \
-v $HOME/var/lib/mqnamesrv1/store:/home/rocketmq/store \
-p 9876:9876 \
--network mynet \
${rocketmq_image} mqnamesrv



cat > /tmp/broker.sed<<EOF
brokerClusterName=RaftCluster
brokerName=_BROKERNAME
listenPort=10911
namesrvAddr=_NAMESRVADDR
#storePathRootDir=/rocketmq/store
#storePathCommitLog=/rocketmq/store/commitlog
enableDLegerCommitLog=true
dLegerGroup=_BROKERNAME
dLegerPeers=
dLegerSelfId=
sendMessageThreadPoolNums=16
brokerIP1=${ip4}
EOF

fastListenPort="10909"
listenPort="10911"
haListenPort="10912"

for i in {1..6} ; do
name="mqbroker${i}";
broker_name="RaftNode00";


if [ $i -lt 4 ]; then
  broker_name="RaftNode00";
  dLegerPeers="n0-${ip4}:11912;n1-${ip4}:12912;n2-${ip4}:13912"
  dLegerSelfId="n$((i-1))";
fi
if [ $i -gt 3 ] && [ $i -lt 7 ] ; then
  broker_name="RaftNode01";
  dLegerPeers="n0-${ip4}:14912;n1-${ip4}:15912;n2-${ip4}:16912"
  dLegerSelfId="n$((i-1-3*1))";
fi


next_fastListenPort=$((fastListenPort+1000*i))
next_listenPort=$((listenPort+1000*i))
next_haListenPort=$((haListenPort+1000*i))
sed \
-e "s@_BROKERNAME@${broker_name}@g" \
-e "s@10911@${next_listenPort}@g" \
-e "s@dLegerPeers=@dLegerPeers=${dLegerPeers}@g" \
-e "s@dLegerSelfId=@dLegerSelfId=${dLegerSelfId}@g" \
-e "s@_NAMESRVADDR@mqnamesrv1:9876@" /tmp/broker.sed > $HOME/$name.conf


docker rm -f $name 2>/dev/null || true

mkdir -p $HOME/var/lib/$name/logs
mkdir -p $HOME/var/lib/$name/store

chown -R 3000:3000 $HOME/var/lib/$name || sudo chown -R 3000:3000 $HOME/var/lib/$name
docker run -d \
--name $name \
--restart always \
-e TZ="Asia/Shanghai" \
-e JAVA_OPT_EXT="-Duser.home=/home/rocketmq -Xms512m -Xmx512m -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m" \
-v $HOME/var/lib/$name/logs:/home/rocketmq/logs \
-v $HOME/var/lib/$name/store:/home/rocketmq/store \
-v $HOME/$name.conf:/etc/rocketmq/broker.conf \
-p ${next_fastListenPort}:${next_fastListenPort} \
-p ${next_listenPort}:${next_listenPort} \
-p ${next_haListenPort}:${next_haListenPort} \
--network mynet \
${rocketmq_image} mqbroker -c /etc/rocketmq/broker.conf
done

docker rm -f rmqconsole 2>/dev/null || true
docker run -d \
--name rmqconsole \
--restart always \
--network mynet \
-e JAVA_OPTS="-Drocketmq.namesrv.addr=mqnamesrv1:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false" \
-p 28080:8080 \
styletang/rocketmq-console-ng
}


fun_add_mynet
fun_install_rocketmq
