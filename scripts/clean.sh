#!/usr/bin/env bash

docker ps -a --format="{{.Names}}" | xargs -r -I{} docker rm -f {}
sudo rm -rf /home/vagrant/var