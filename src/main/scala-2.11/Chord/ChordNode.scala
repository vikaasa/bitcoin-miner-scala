package Chord

import java.util.concurrent.TimeUnit

import Chord.ChordMaster.{AllRequestsSent, NodeAddComplete, RequestHopCount}
import Chord.ChordNode._
import akka.actor.{Cancellable, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.Duration
import scala.util.Random

object ChordNode{
  case class FindKey(key: Int, hopCount: Int)
  case class FindPredecessor(key: Int, hopCount: Int)
  case class FindResult(key: Int, owner: ChordNode, hopCount: Int)
  case class Join(networkNode: ActorRef)
  case class Notify(node: ChordNode)
  case class GetPredecessor()
  case class GetPredecessorResult(node: ChordNode)
  case class Stabilize()
  case class FixFingers()
  case class PrintInfo()
  case class StartRequests(numRequests:Int)
  case class RandomQuery()

}

class ChordNode(val index :Int, M: Int) extends Actor{
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)
  val waitTimeout = Duration.create(10, TimeUnit.SECONDS)
  implicit val ec = context.dispatcher
  var predecessor: ChordNode = this
  private val sizeLimit = math.pow(2, M).toInt
  private val fingerTable = new Array[FingerTableEntry](M)
  val actorRef = self
  val stabilizeDuration = Duration.create(10, TimeUnit.MILLISECONDS)
  val fixFingersDuration = Duration.create(20, TimeUnit.MILLISECONDS)
  val randQueryDuration = Duration.create(1, TimeUnit.SECONDS)
  var randomQueryTick:Cancellable = null
  var numRequests = 0
  var numRequestsSent = 0


  for( i <- 0 to M-1) {
    val start = (index + math.pow(2,i)).toInt % sizeLimit
    val interval = new Interval(start, (start + (math.pow(2,i).toInt)) % sizeLimit , M)
    fingerTable(i) = new FingerTableEntry(start, interval, this)
  }

  override def receive = {
    case FindPredecessor(key, hopCount) =>
      if(!Interval((index+1) % sizeLimit, (successor.index+1) % sizeLimit, M).contains(key)) {
        val node = closestPreceedingFinger(key);
        //if the same current node is present in fingerTable entry, in cases where there is only node
        if(node.index == index)
          sender() ! FindResult(key, this, hopCount)
        else node.actorRef forward FindPredecessor(key, hopCount +1)
      }
      else sender() ! FindResult(key, this, hopCount)

    //Equivalent to findSuccessor
    case FindKey(key, hopCount) =>
      if(!Interval((index+1) % sizeLimit, (successor.index+1) % sizeLimit, M).contains(key)) {
        val node = closestPreceedingFinger(key);
        //if the same current node is present in fingerTable entry, in cases where there is only node
        if(node.index == index)
          sender() ! FindResult(key, this.successor, hopCount)
        else node.actorRef forward FindKey(key, hopCount +1)
      }
      else sender() ! FindResult(key, this.successor, hopCount)


    case Join(networkNode) =>
      if(networkNode != null){
        predecessor = null
        fingerTable(0).node = null
        val future = networkNode ? FindKey(index,0)
        future onSuccess {
          case result =>
            val successor = result.asInstanceOf[FindResult].owner
            //Update all the fingertable entries to point to successor
            //These will get updated on subsequent FixFingers event
            for(i <- 0 to M-1)
              fingerTable(i).node = successor
            context.system.scheduler.schedule(Duration.create(0, TimeUnit.MILLISECONDS), stabilizeDuration,self, Stabilize) (context.system.dispatcher, self)
            context.system.scheduler.schedule(Duration.create(0, TimeUnit.MILLISECONDS), fixFingersDuration,self, FixFingers) (context.system.dispatcher, self)
        }
      }else {
        context.system.scheduler.schedule(Duration.create(0, TimeUnit.MILLISECONDS), stabilizeDuration,self, Stabilize) (context.system.dispatcher, self)
        context.system.scheduler.schedule(Duration.create(0, TimeUnit.MILLISECONDS), fixFingersDuration,self, FixFingers) (context.system.dispatcher, self)

      }
      sender() ! NodeAddComplete

    case Notify(node) =>
      if(predecessor == null || Interval(predecessor.index+1, index, M).contains(node.index))
        predecessor = node

    case GetPredecessor =>
      sender() ! GetPredecessorResult(predecessor)
    case Stabilize =>
      val future = successor.actorRef ? GetPredecessor
      future onSuccess {
        case result =>
          //x is current successor's predecessor
          val x = result.asInstanceOf[GetPredecessorResult]
          if(x.node != null && Interval(index+1, successor.index,M).contains(x.node.index))
            fingerTable(0).node = x.node
          successor.actorRef ! Notify(this)
      }
    case PrintInfo =>
      println(this.toString)
    case FixFingers =>
      //We don't udpate the successor finger
      val randFingerIndex = Random.nextInt(M-1) + 1
      val future = self ? FindKey(fingerTable(randFingerIndex).start, 0)
      future onSuccess {
        case result =>
          fingerTable(randFingerIndex).node = result.asInstanceOf[FindResult].owner
      }
    case RandomQuery =>
      numRequestsSent += 1
      if(successor!= null) {
        val future = self ? FindKey(ConsistentHasher.getRandomHashCode(M),0)
        future onSuccess {
          case FindResult(key,successor, hopCount) =>
            context.actorSelection("..") ! RequestHopCount(hopCount)
        }
        if(numRequestsSent >= numRequests){
          randomQueryTick.cancel()
          context.actorSelection("..") ! AllRequestsSent
        }
      }
    case StartRequests(numRequests) =>
      this.numRequests = numRequests
      randomQueryTick = context.system.scheduler.schedule(Duration.create(1, TimeUnit.SECONDS), randQueryDuration,self, RandomQuery) (context.system.dispatcher, self)
  }

  override def toString : String = {
    val sb = new StringBuilder()
    fingerTable.foreach(e => {sb.append(e.toString())
      sb.append(", ")
    })
    index.toString + "{ finger : {" + sb.toString()+ "}" + ", pred: " + predecessor.index + ", successor: "+successor.index +" }"
  }

  def successor : ChordNode = fingerTable(0).node



  private def closestPreceedingFinger(id: Int) : ChordNode = {
    for(i <- M-1 to 0 by -1){
      if(Interval((index+1) % sizeLimit, id, M).contains(fingerTable(i).node.index)){
        return fingerTable(i).node
      }
    }
    this
  }


}


class FingerTableEntry(val start: Int, val interval: Interval, var node: ChordNode){
  override def toString():String = {
    node.index.toString
  }
}

//Chord Ring Interval
object Interval{
  def apply(start:Int, end:Int, M:Int): Interval = {
    new Interval(start, end, M)
  }
}
//Defines the interval [start, end)
class Interval(val start:Int, val end:Int, val M:Int){
  val ringLimit = Math.pow(2, M);
  var zeroCrossOver = false;
  if(start > end)
    zeroCrossOver = true
  def contains(i: Int): Boolean = {
    var inRange = false;
    if(i >= start && i < end)
      inRange = true;
    else if(zeroCrossOver) {
      if ((i >= start && i < ringLimit) || (i >= 0 && i < end))
        inRange = true;
    }
    inRange
  }
}
