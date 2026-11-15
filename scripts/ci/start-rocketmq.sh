#!/usr/bin/env bash
set -euo pipefail

# Starts a minimal RocketMQ topology on the local host for CI integration tests:
#   - namesrv on 127.0.0.1:9876
#   - one master broker on 127.0.0.1:11911 (matches Addresses.BROKER_ADDR_1)
#   - one proxy on 127.0.0.1:8181 (matches Addresses.PROXY)
#
# Everything runs with host networking so the broker, proxy and the Maven test JVM on the
# runner share the same 127.0.0.1 address space. Containers are left running for the
# subsequent test step and discarded with the ephemeral runner.

rocketmq_image="${ROCKETMQ_IMAGE:-dyrnq/rocketmq:5.3.0-jdk21}"

namesrv_addr="127.0.0.1:9876"
broker_addr="127.0.0.1:11911"
proxy_addr="127.0.0.1:8181"

java_opt_ext="-Duser.home=/home/rocketmq -XX:+IgnoreUnrecognizedVMOptions -XX:-UseG1GC -XX:+UseZGC -Xms1g -Xmx1g -Xmn128m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=128m"

docker rm -f rmq-namesrv rmq-broker rmq-proxy >/dev/null 2>&1 || true

wait_for_port() {
  local host="$1"
  local port="$2"
  local name="$3"
  for _ in $(seq 1 60); do
    if (echo >"/dev/tcp/${host}/${port}") 2>/dev/null; then
      echo "ready: ${name} (${host}:${port})"
      return 0
    fi
    sleep 2
  done
  echo "timed out waiting for ${name} (${host}:${port})" >&2
  return 1
}

docker run -d \
  --name rmq-namesrv \
  --network host \
  -e TZ="Asia/Shanghai" \
  -e "JAVA_OPT_EXT=${java_opt_ext}" \
  "${rocketmq_image}" mqnamesrv

wait_for_port 127.0.0.1 9876 namesrv

cat >/tmp/rmq-broker.conf <<EOF
brokerClusterName=DefaultCluster
brokerName=ci-broker
brokerId=0
namesrvAddr=${namesrv_addr}
autoCreateTopicEnable=true
autoCreateSubscriptionGroup=true
enablePropertyFilter=true
listenPort=11911
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
waitTimeMillsInSendQueue=600
brokerIP1=127.0.0.1
messageDelayLevel=1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h 20s
EOF

docker run -d \
  --name rmq-broker \
  --network host \
  -e TZ="Asia/Shanghai" \
  -e "JAVA_OPT_EXT=${java_opt_ext}" \
  -v /tmp/rmq-broker.conf:/etc/rocketmq/broker.conf \
  "${rocketmq_image}" mqbroker -c /etc/rocketmq/broker.conf

wait_for_port 127.0.0.1 11911 broker

broker_registered=false
for _ in $(seq 1 60); do
  if docker exec rmq-broker ./mqadmin clusterList -n "${namesrv_addr}" 2>/dev/null | grep -q ci-broker; then
    broker_registered=true
    echo "ready: broker registered in cluster"
    break
  fi
  sleep 2
done
if [ "${broker_registered}" != "true" ]; then
  echo "timed out waiting for broker to register with namesrv" >&2
  exit 1
fi

cat >/tmp/rmq-proxy.json <<EOF
{
  "namesrvAddr": "${namesrv_addr}",
  "rocketMQClusterName": "DefaultCluster",
  "proxyClusterName": "DefaultCluster",
  "grpcServerPort": 8181,
  "remotingListenPort": 28081
}
EOF

docker run -d \
  --name rmq-proxy \
  --network host \
  -e TZ="Asia/Shanghai" \
  -e "JAVA_OPT_EXT=${java_opt_ext}" \
  -v /tmp/rmq-proxy.json:/etc/rocketmq/proxyConfig.json \
  "${rocketmq_image}" mqproxy --proxyConfigPath /etc/rocketmq/proxyConfig.json

wait_for_port 127.0.0.1 8181 proxy

update_topic() {
  docker exec rmq-broker ./mqadmin updateTopic "$@"
}

update_topic -c DefaultCluster -t delayTopic -n "${namesrv_addr}" -a +message.type=DELAY
update_topic -c DefaultCluster -t fifoTopic -n "${namesrv_addr}" -a +message.type=FIFO
update_topic -c DefaultCluster -t normalTopic -n "${namesrv_addr}" -a +message.type=NORMAL
update_topic -c DefaultCluster -t transTopic -n "${namesrv_addr}" -a +message.type=TRANSACTION
update_topic -c DefaultCluster -t demo-topic -n "${namesrv_addr}" -a +message.type=NORMAL
update_topic -t topic_a -n "${namesrv_addr}" --brokerAddr "${broker_addr}" -a +message.type=NORMAL
update_topic -t topic_b -n "${namesrv_addr}" --brokerAddr "${broker_addr}" -a +message.type=NORMAL

echo "RocketMQ is ready (namesrv=${namesrv_addr}, broker=${broker_addr}, proxy=${proxy_addr})"
