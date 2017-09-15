/**
  * Copyright (c) 2016, CodiLime Inc.
  */

package io.deepsense.e2etests

import java.net.URL
import java.util.UUID

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scalaz.Scalaz._
import scalaz._

import akka.actor.ActorSystem
import akka.util.Timeout
import org.scalactic.source.Position
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}

import io.deepsense.commons.models.ClusterDetails
import io.deepsense.commons.utils.Logging
import io.deepsense.deeplang.CatalogRecorder
import io.deepsense.deeplang.catalogs.CatalogPair
import io.deepsense.deeplang.catalogs.doperable.DOperableCatalog
import io.deepsense.deeplang.catalogs.doperations.DOperationsCatalog
import io.deepsense.graph.nodestate.name.NodeStatusName
import io.deepsense.models.json.graph.GraphJsonProtocol.GraphReader
import io.deepsense.models.json.workflow.WorkflowJsonProtocol
import io.deepsense.models.workflows.{Workflow, WorkflowInfo}
import io.deepsense.sessionmanager.rest.client.SessionManagerClient
import io.deepsense.sessionmanager.service.Status
import io.deepsense.workflowmanager.client.WorkflowManagerClient


trait SeahorseIntegrationTestDSL extends Matchers with Eventually with Logging with WorkflowJsonProtocol {

  val CatalogPair(operablesCatalog, operationsCatalog) = CatalogRecorder.catalogs

  override val graphReader = new GraphReader(operationsCatalog)

  import scala.concurrent.ExecutionContext.Implicits.global

  protected val httpTimeout = 10 seconds
  protected val workflowTimeout = 30 minutes

  protected val baseUrl = new URL("http", "localhost", 33321, "")
  protected val workflowsUrl = new URL(baseUrl, "/v1/workflows/")
  protected val sessionsUrl = new URL(baseUrl, "/v1/sessions/")

  private val userId = UUID.fromString("dd63e120-548f-4ac9-8fd5-092bad7616ab")
  private val userName = "Seahorse test"

  implicit val as: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = httpTimeout

  implicit val patience = PatienceConfig(
    timeout = Span(60, Seconds),
    interval = Span(10, Seconds)
  )

  val wmclient = new WorkflowManagerClient(
    workflowsUrl,
    userId,
    userName,
    None
  )

  val smclient = new SessionManagerClient(
    sessionsUrl,
    userId,
    userName,
    None
  )

  def ensureSeahorseIsRunning(): Unit = {
    eventually {
      logger.info("Waiting for Seahorse to boot up...")
      val smIsUp = Await.result(smclient.fetchSessions(), httpTimeout)
      val wmIsUp = Await.result(wmclient.fetchWorkflows(), httpTimeout)
    }
  }

  def createSessionSynchronously(id: Workflow.Id, clusterDetails: ClusterDetails): Unit = {
    smclient.createSession(id, clusterDetails)

    eventually {
      Await.result(
        smclient.fetchSession(id).map { s =>
          s.status shouldBe Status.Running
        }, httpTimeout)
    }
  }

  def assertAllNodesCompletedSuccessfully(workflow: WorkflowInfo): Unit = {
    val numberOfNodesFut = calculateNumberOfNodes(workflow.id)

    val nodesResult: Validation[String, String] = eventually {

      val nodesStatuses = smclient.queryNodeStatuses(workflow.id)
      Await.result(
        for {
          nsr <- nodesStatuses
          errorNodeStatuses = nsr.nodeStatuses.getOrElse(Map.empty).getOrElse(NodeStatusName.Failed, 0)
          completedNodes = nsr.nodeStatuses.getOrElse(Map.empty).getOrElse(NodeStatusName.Completed, 0)
          numberOfNodes <- numberOfNodesFut
        } yield {
          checkCompletedNodesNumber(
            errorNodeStatuses,
            completedNodes,
            numberOfNodes,
            workflow.id,
            workflow.name
          )
        }, httpTimeout)
    }(PatienceConfig(timeout = workflowTimeout, interval = 5 seconds), implicitly[Position])

    nodesResult match {
      case Success(_) =>
      case Failure(nodeReport) =>
        fail(s"Some nodes failed for workflow id: ${workflow.id}. name: ${workflow.name}'. Node report: $nodeReport")
    }
  }

  def checkCompletedNodesNumber(errorNodeStatuses: Int,
                                completedNodes: Int,
                                numberOfNodes: Int,
                                workflowID: Workflow.Id,
                                workflowName: String): Validation[String, String] = {
    if (errorNodeStatuses > 0) {
      s"Errors: $errorNodeStatuses nodes failed for workflow id: $workflowID. name: $workflowName".failure
    } else {
      logger.info(s"$completedNodes nodes completed." +
        s" Need all $numberOfNodes nodes completed for workflow id: $workflowID. name: $workflowName")

      if (completedNodes > numberOfNodes) {
        logger.error(
          s"""FATAL. INVESTIGATE
              |
              |Number of completed nodes is larger than number of nodes
            """.
            stripMargin)
      }

      completedNodes shouldEqual numberOfNodes

      "All nodes completed".success
    }
  }

  private def calculateNumberOfNodes(workflowId: Workflow.Id): Future[Int] = {
    wmclient.fetchWorkflow(workflowId).map(_.graph.nodes.size)
  }

}
