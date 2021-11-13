#!/usr/bin/env bash

bin=`dirname ${0}`
bin=`cd ${bin}; pwd`
basedir=${bin}/..

cd ${basedir}

cat mypid | xargs kill -15
