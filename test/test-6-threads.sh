#!/bin/bash
set -m # Enable Job Control

# utils
error() {
  echo -n "Error! $1 || at "; caller; exit 1;
}
rstr() {
  tr -dc A-Za-z0-9 </dev/urandom | head -c $1 ; echo ''
}


ROOT="/tmp/mnt"
TEST="test-6"
echo "starting" $TEST

cd $ROOT || error

rm -rf $TEST || error
mkdir $TEST || error
cd $TEST || error

child_process() {
  n=${1:-10}
  c=${2:-"a"}
#  echo "($n, $c)"
  for i in $(seq 1 $n); do
#    echo $i
    echo -n $c >> x.txt
    sleep 0.1
  done
}

num=50

for i in $(seq 1 $num); do
  echo -n "fork process $i: "
  c=$(printf "\x$(printf %x $[64 + $i])")
  echo $i, $c
  child_process $i $c &
done

sleep $[$num / 3 + 2]

#cat x.txt || error
res=$(cat x.txt) || error
echo ${#res}
echo $[($num + 1) * $num / 2]
if [ ${#res} != $[($num + 1) * $num / 2] ]; then error; fi

echo "Success!"
