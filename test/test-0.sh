#!/bin/bash
ROOT=/tmp/mnt
cd $ROOT
res1=$(pwd)
if [ "$res1" != "/tmp/mnt" ]; then
  echo "Error!"; exit 1
fi
echo "Success!"
