package Chord

import java.util.concurrent.TimeUnit

import Chord.ChordMaster.{RequestHopCount, AllRequestsSent, SimulateChordNetwork, NodeAddComplete}
import Chord.ChordNode.{StartRequests, Join}
import akka.actor.{Props, ActorRef, Actor}
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.duration.Duration

object ChordMaster{
  case class SimulateChordNetwork(numNodes:Int, numRequest:Int)
  case class RequestHopCount(count:Int)
  case class NodeAddComplete()
  case class AllRequestsSent()
}

class ChordMaster extends Actor {
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)
  implicit val ec = context.dispatcher
  private val M = 30
  private var node0: ActorRef = null
  private var completedNodes = 0
  private var hopCountsSum = 0.0
  private var hopCounts = 0.0
  private var numNodes = 0
  private var numRequests = 0
  private var addedNodes = 0
  private var nodeIdsList:IndexedSeq[Int] = null

  override def receive: Receive = {
    case NodeAddComplete =>
      addedNodes += 1
      if(addedNodes!= numNodes){
        val nodeId = nodeIdsList(addedNodes)
        val node = context.actorOf(Props(new ChordNode(nodeId, M)), name="node:"+ nodeId)
        node ! Join(node0)
      }else {
        println("All nodes added")
        var stabilizationWait = 0
        if(numNodes<=1000)
          stabilizationWait = 10
        else if(numNodes > 1000 && numNodes <=10000)
          stabilizationWait = 100
        else stabilizationWait = 600
        println("Waiting "+ stabilizationWait + " seconds for stabilization")
        context.system.scheduler.scheduleOnce(Duration.create(stabilizationWait,TimeUnit.SECONDS), new Runnable {
          override def run(): Unit = {
            nodeIdsList.foreach((nodeId) => context.actorSelection("../master/node:"+nodeId)! StartRequests(numRequests))
          }
        }) (context.system.dispatcher)
      }

    case SimulateChordNetwork(numNodes, numRequests) =>
      this.numNodes = numNodes
      this.numRequests = numRequests
      val nodeIds = new mutable.HashSet[Int]
      var randInt = 0
      nodeIds.add(ConsistentHasher.getRandomHashCode(M))
      while(nodeIds.size < numNodes){
        do {
          randInt = ConsistentHasher.getRandomHashCode(M)
        }while(nodeIds.contains(randInt))
        nodeIds.add(randInt)
      }
      nodeIdsList = nodeIds.toIndexedSeq
      val firstNode = nodeIds.head
      node0 = context.actorOf(Props(new ChordNode(firstNode, M)), name="node:"+ firstNode)
      node0 ! Join(null)

    case AllRequestsSent =>
      completedNodes +=1
      if(completedNodes == numNodes){
        println("Average hop count: " + hopCountsSum/hopCounts)
        System.exit(0)
      }

    case RequestHopCount(count) =>
      hopCounts +=1
      hopCountsSum += count
  }
}