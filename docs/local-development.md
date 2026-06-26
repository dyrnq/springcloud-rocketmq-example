# Local development

Every test in this repository talks to a real RocketMQ broker running in a Vagrant VM.
There is no embedded broker, no Testcontainers — the tests assume a broker reachable at
`192.168.88.123:9876` (V4, NameSrv) and `192.168.88.123:8181` (V5, Proxy gRPC).

The hard-coded addresses live in [test-support/Addresses.java](../test-support/src/main/java/com/dyrnq/rocketmq/testsupport/Addresses.java).
To redirect the entire test suite to a different VM, change those constants and bring the
broker up with the matching script on the new host.

## VM topology

`Vagrantfile` defines three VMs. They are **alternative RocketMQ deployment topologies,
not three machines forming one cluster** — at any moment you only need one running to
cover every POC module.

| Hostname | IP             | Companion script                                      | Topology it runs                                                       |
| -------- | -------------- | ----------------------------------------------------- | ---------------------------------------------------------------------- |
| `server` | 192.168.88.123 | `run-containers-controller.sh` or `run-containers.sh` | Controller mode (with proxy, most complete) / classic 2 groups of 1m1s |
| `raft`   | 192.168.88.128 | `run-containers-raft.sh`                              | DLedger Raft mode                                                      |
| `r1`     | 192.168.88.129 | (none)                                                | Spare                                                                  |

### Why one VM at a time is enough

- **Module code only hard-codes `192.168.88.123`**: every `spring-cloud-*` / `sbs` / `client`
  module talks to namesrv on `9876`; `sbs-v5` / `client-v5` talk to proxy on `8181`. **No
  module references 192.168.88.128 or 192.168.88.129.**
- **Each VM is self-contained**: namesrv / broker / controller / proxy live inside one VM
  on the docker network `mynet`. The three VMs never talk to each other.
- **Resource footprint**: a single VM (per Vagrantfile default of 16 GB / 4 vCPU) needs
  ~12-14 GB resident after boot, which fits comfortably on a 125 GB host with 51 GB
  available. Running all three simultaneously will OOM the host.

## Recommended workflow

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

## Topology-switch cheat sheet

| What you want to test                                     | On which VM | Run which script                                      | Source code change required                                              |
| --------------------------------------------------------- | ----------- | ----------------------------------------------------- | ------------------------------------------------------------------------ |
| spring-cloud-v2021 / v2023 / v2025 / v2025-1, sbs, client | server      | `run-containers.sh` or `run-containers-controller.sh` | none                                                                     |
| sbs-v5, client-v5 (need Proxy gRPC)                       | server      | `run-containers-controller.sh`                        | none                                                                     |
| Raft leader election verification                         | raft        | `run-containers-raft.sh`                              | change namesrv addr to `192.168.88.128:9876` (runtime arg, source stays) |

## Broker scripts

All scripts live in `scripts/` and are designed to be run **inside the Vagrant VM after
`vagrant ssh`**. They use Docker; the host's `iface` is `enp0s8` by default (override
with `--iface`).

| Script                              | What it brings up                                                              |
| ----------------------------------- | ------------------------------------------------------------------------------ |
| `run-containers.sh`                 | 1 namesrv (`n1`), 2 broker groups of 1m1s (q1, q3) on docker network `mynet`   |
| `run-containers-controller.sh`      | As above, plus 1 controller and 2 proxy instances — required for sbs-v5 / client-v5 |
| `run-containers-raft.sh`            | 1 namesrv + 1 DLedger-Raft group of 3 nodes                                     |
| `run-containers-local-proxy.sh`     | Controller mode + a local-only Proxy on the VM host                            |
| `clean.sh`                          | Stops and removes every container started by the above scripts                 |

The scripts auto-detect the host IPv4 of `iface` and write it into `brokerIP1=`, so they
work regardless of which `enp0s8`-equivalent interface the VM gets.

## Pre-created topics

`run-containers-controller.sh` (and `run-containers.sh`) pre-create these topics with
the matching `message.type` so the test suite can run without manual topic creation:

| Topic         | `message.type` | Used by                                       |
| ------------- | -------------- | --------------------------------------------- |
| `delayTopic`  | DELAY          | `client/DelayLevelTest`, `sbs/DelayLevelTest`, `sbs-v5/AsyncDelayTimestampTest`, `client-v5/DelayTimestampTest` |
| `fifoTopic`   | FIFO           | `client-v5/FifoMessageTest`, `sbs-v5/FifoMessageTest`           |
| `normalTopic` | NORMAL         | `client-v5/SyncSendTest` and other V5 normal-message tests       |
| `transTopic`  | TRANSACTION    | `client-v5/TransactionalSendTest`, `sbs-v5/TransactionalSendTest` |
| `demo-topic`  | (default)      | `sbs/*` and `client/*` sync/async/delay tests                  |
| `topic_a`     | (default)      | `client/SyncSendTest`                          |
| `topic_b`     | (default)      | `client/SyncSendTest` (broker 2)               |

`autoCreateTopicEnable=true` is set on the broker, so any topic not in this list is
auto-created on first produce with default 4 read / 4 write queues.

## Vagrantfile notes

- **Box**: `ubuntu/jammy64`
- **Per-VM resources**: 16 GB RAM, 4 vCPU, 128 MB VRAM, 500 GB primary disk
- **Provisioning**:
  1. `scripts/init.sh` — installs Docker and base packages
  2. Inline shell — rewrites `/etc/docker/daemon.json` to use `docker.m.daocloud.io` as
     a registry mirror (faster image pulls inside mainland China)
- **SSH key**: the repo intentionally ships `insecure_private_key` (the upstream Vagrant
  insecure key). Don't delete it — it's the only way `vagrant ssh` works on a freshly
  cloned repo without manual keypair setup.

## Reset / clean up

```bash
# Inside VM: stop and remove every container the scripts created
./scripts/clean.sh

# On host: tear down the VM entirely
vagrant destroy -f server
```