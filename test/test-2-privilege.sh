#!/bin/bash
# utils
error() {
  echo -n "Error! $1 || at "; caller; exit 1;
}
rstr() {
  tr -dc A-Za-z0-9 </dev/urandom | head -c $1 ; echo ''
}

ROOT="/tmp/mnt"
cd $ROOT
res1=$(pwd)
if [ "$res1" != "$ROOT" ]; then
    error
fi

TEST="test-2"
echo "starting" $TEST

echo rm -rf $TEST
rm -rf $TEST || error
echo mkdir $TEST
mkdir $TEST || error
echo cd $TEST
cd $TEST || error

dir1="a"
dir2="b"
file1="x"
file2="y"
echo mkdir $dir1
mkdir $dir1 || error
echo mkdir $dir2
mkdir $dir2 || error
echo touch $file1
touch $file1 || error
echo touch $file2
touch $file2 || error
echo tree
tree || error
echo mv $file1 ${dir1}/${file1}
mv $file1 ${dir1}/${file1} || error
echo tree
tree || error

echo "Success!"
