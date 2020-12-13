#!/bin/bash
# utils
error() {
  echo -n "Error! $1 || at "; caller; exit 1;
}
rstr() {
  tr -dc A-Za-z0-9 </dev/urandom | head -c $1 ; echo ''
}

ROOT="/tmp/mnt"
TEST="test-3"
echo "starting" $TEST

cp hello.cpp $ROOT || error
cd $ROOT || error

rm -rf $TEST || error
mkdir $TEST || error
mv hello.cpp $TEST || error
cd $TEST || error

g++ hello.cpp || error
./a || error

echo "Success!"
