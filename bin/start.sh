#!/usr/bin/env bash

bin=`dirname ${0}`
bin=`cd ${bin}; pwd`
basedir=${bin}/..
LIB_DIR=${basedir}/lib
LOG_DIR=${basedir}/logs
CONF_DIR=${basedir}/conf

cd ${basedir}

if [ ! -d ${LOG_DIR} ]; then
  mkdir -p ${LOG_DIR}
fi

if [ "$#" -eq 0 ]; then
    echo "missing conf file name. example: start.sh app.conf"
    exit 2
fi

JVM_PARAMS="-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"

if [ $# -eq 2 ]; then
  JVM_PARAMS="$JVM_PARAMS -Dserver.jar=$2"
fi

#jdk 11 version required
if [ -z ${JDK11+x} ]; then
  echo "JDK11 is not set, use default java";
  echo $JAVA_HOME
else
  echo "JDK11 is set '$JDK11'";
  export JAVA_HOME=$JDK11
  export PATH=$JAVA_HOME/bin:$PATH
fi

nohup java -cp conf:lib/*:jars/*:target/classes ${JVM_PARAMS} \
  org.eurekaka.bricks.server.MainApp $1 \
  >> ${LOG_DIR}/out.log 2>&1 &

echo $! > mypid
