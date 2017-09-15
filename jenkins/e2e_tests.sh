#!/usr/bin/env bash
# Copyright (c) 2016, CodiLime Inc.
#

if [[ "$#" -eq 0 ]]; then
  echo "error: no tests specified"
  echo "supported options:"
  echo "-b, --batch"
  echo "-s, --session"
  echo "-a, --all"
  echo "--cleanup-only"
  exit 1
fi

# parse parameters
RUN_BATCH_TESTS=false
RUN_SESSIONMANAGER_TESTS=false
CLEANUP_ONLY=false
while (( "$#" )); do
  key="$1"

  case $key in
    -b|--batch)
    RUN_BATCH_TESTS=true
    shift
    ;;
    -s|--session)
    RUN_SESSIONMANAGER_TESTS=true
    shift
    ;;
    -a|--all)
    RUN_BATCH_TESTS=true
    RUN_SESSIONMANAGER_TESTS=true
    ;;
    --cleanup-only)
    CLEANUP_ONLY=true
    shift
    ;;
    *)
    echo "error: unknown option $key"
    exit 1
    ;;
  esac
  shift
done

set -ex
# `dirname $0` gives folder containing script
cd `dirname $0`"/../"

RUN_ID="seahorse-e2e-tests"
export CLUSTER_ID=$RUN_ID # needed by spark-standalone-cluster-manage.sh
export NETWORK_NAME="sbt-test-$RUN_ID" # needed by spark-standalone-cluster-manage.sh

export BACKEND_TAG=`git rev-parse HEAD`

FRONTEND_TAG="${FRONTEND_TAG:-$SEAHORSE_BUILD_TAG}" # If FRONTEND_TAG not defined try to use SEAHORSE_BUILD_TAG
FRONTEND_TAG="${FRONTEND_TAG:-master-latest}" # If it's still undefined fallback to master-latest

SPARK_STANDALONE_MANAGEMENT="./seahorse-workflow-executor/docker/spark-standalone-cluster-manage.sh"
MESOS_SPARK_DOCKER_COMPOSE="testing/mesos-spark-cluster/mesos-cluster.dc.yml"

SPARK_VERSION="2.0.0"

## Make sure that when job is aborted/killed all dockers will be turned off
function cleanup {
  $SPARK_STANDALONE_MANAGEMENT down $SPARK_VERSION
  docker-compose -f $MESOS_SPARK_DOCKER_COMPOSE down
  deployment/docker-compose/docker-compose.py -f $FRONTEND_TAG -b $BACKEND_TAG -p $RUN_ID logs > docker-compose.log
  deployment/docker-compose/docker-compose.py -f $FRONTEND_TAG -b $BACKEND_TAG -p $RUN_ID down
}

cleanup
if $CLEANUP_ONLY ; then
  exit 0
fi

trap cleanup EXIT

./jenkins/scripts/sync_up_docker_images_with_git_repo.sh

## Start Seahorse dockers

deployment/docker-compose/docker-compose.py -f $FRONTEND_TAG -b $BACKEND_TAG -p $RUN_ID pull
# destroy dockercompose_default, so we can recreate it with proper id
deployment/docker-compose/docker-compose.py -f $FRONTEND_TAG -b $BACKEND_TAG -p $RUN_ID down
(
 cp jenkins/resources/a_e2etests_dev_override_build.sbt seahorse-sdk-example/a_e2etests_dev_override_build.sbt
 cd seahorse-sdk-example
 sbt clean assembly
 cd ../deployment/docker-compose
 mkdir -p jars
 cp -r ../../seahorse-sdk-example/target/scala-2.11/*.jar jars
 ./docker-compose.py -f $FRONTEND_TAG -b $BACKEND_TAG --generate-only --yaml-file docker-compose.yml
 ./docker-compose.py -f $FRONTEND_TAG -b $BACKEND_TAG -p $RUN_ID up -d
)

## Start Spark Standalone cluster dockers

$SPARK_STANDALONE_MANAGEMENT up $SPARK_VERSION

## Get and export Spark Standalone cluster IP
INSPECT_FORMAT="{{(index (index .NetworkSettings.Networks \"$NETWORK_NAME\").IPAddress )}}"
export SPARK_STANDALONE_MASTER_IP=$(docker inspect --format "$INSPECT_FORMAT" sparkMaster-$RUN_ID)

## Start Mesos Spark cluster dockers

testing/mesos-spark-cluster/build-cluster-node-docker.sh
docker-compose -f $MESOS_SPARK_DOCKER_COMPOSE up -d

export MESOS_MASTER_IP=10.254.0.2

## Run sbt tests
if $RUN_SESSIONMANAGER_TESTS ; then
  sbt e2etests/clean "e2etests/testOnly io.deepsense.e2etests.session.*"
fi
if $RUN_BATCH_TESTS ; then
  sbt e2etests/clean "e2etests/testOnly io.deepsense.e2etests.batch.*"
fi

SBT_EXIT_CODE=$?
exit $SBT_EXIT_CODE
