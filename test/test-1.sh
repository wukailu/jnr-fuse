#!/bin/bash
# utils
error() {
  echo -n "Error! $1 || at "; caller; exit 1;
}
rstr() {
  tr -dc A-Za-z0-9 </dev/urandom | head -c $1 ; echo ''
#  cat /dev/urandom | head -n 10 | md5sum | head -c 10
#  for j in $(seq 1 $1); do
#    printf "%s" cat /dev/urandom | head -n 10 | md5sum | head -c 1
#  done
#  printf "\n"
}

ROOT="/tmp/mnt"
cd $ROOT
res1=$(pwd)
if [ "$res1" != "/tmp/mnt" ]; then
    error
fi

TEST="test-1"
echo "starting" $TEST

if [ -d $TEST ]; then
  rmdir $TEST || error
fi
mkdir $TEST || error
cd $TEST || error

# generate n different file
for i in {1..40}; do
  echo try length $i
  context=$(rstr $[$i * $i * $i * $i])
#  echo $context
  echo $context > ${i}.txt || error
  res2=$(cat ${i}.txt)
  if [ "$res2" != "$context" ]; then
    error "result is not correct."
  fi
done

echo "Success!"
