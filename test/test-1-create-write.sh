#!/bin/bash
# utils
error() {
  echo -n "Error! $1 || at "; caller; exit 1;
}
rstr() {
  # random
#  tr -dc A-Za-z0-9 </dev/urandom | head -c $1 ; echo ''
  # deterministic
  tr '\0' '\141' </dev/zero | head -c $1 ; echo ''
#  openssl rand -base64 5000000
#  cat /dev/urandom | head -n 10 | md5sum | head -c 10
#  for j in $(seq 1 $1); do
#    printf "%s" cat /dev/urandom | head -n 10 | md5sum | head -c 1
#  done
#  printf "\n"
}

ROOT="/tmp/mnt"
cd $ROOT
res1=$(pwd)
if [ "$res1" != "$ROOT" ]; then
    error
fi

TEST="test-1"
echo "starting" $TEST

rm -rf $TEST || error
mkdir $TEST || error
cd $TEST || error

# generate n different file
for i in {1..40}; do
  echo "case" $i
  context=$(rstr $[$i * $i * $i * $i])"END"
#  echo $context
  echo "$context" > ${i}.txt || error
  cmp -s <(echo "$context") ${i}.txt || error "result is not correct."
done

echo "Success!"
