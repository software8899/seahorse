// Copyright (c) 2015, CodiLime Inc.
//
// Owner: Wojciech Jurczyk

name := "deepsense-graph-json"

libraryDependencies ++= Dependencies.graphJson

// Fork to run all test and run tasks in JVM separated from sbt JVM
fork := true