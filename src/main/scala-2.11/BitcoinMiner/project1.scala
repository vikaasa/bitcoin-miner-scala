package BitcoinMiner

import java.io.{File, PrintWriter, Serializable}
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.event.Logging

import scala.collection.mutable
import scala.concurrent.duration.Duration

object project1 extends App{
  val system = ActorSystem("BitCoinMinerSystem")
  if(args.length < 1){
    println("Invalid Arguments..")
    println("\tUsage:")
    println("\t\tsbt \"run {number of leading zeroes} [resume_flag]\"")
    println("\t\tsbt \"run {HostName/IP address of the remote master miner}\"")

  } else if (args(0).contains(".")){
   //Init a RemoteWorkers and Connect to MasterMiner
    for(i <- 1 to Runtime.getRuntime().availableProcessors()){
      val remoteMiner = system.actorOf(Props[BitCoinMiner], name = "RemoteMiner:"+i)
      remoteMiner ! ConnectRemoteActor(args(0))
    }
  }else{
   //Init a Master
    val leadingZeroes = args(0).toInt
    var loadSavedData = false
    if(args.length == 2){
      if(args(1).toBoolean)
        loadSavedData = true
    }
    var localMiners = new mutable.ListBuffer[ActorRef]()
    for(i <- 1 to Runtime.getRuntime().availableProcessors()){
      val localMiner = system.actorOf(Props[BitCoinMiner], name = "LocalMiner:"+i)
      localMiners += localMiner
    }

    val master = system.actorOf(Props(new BitCoinMasterMiner()),name = "BitCoinMasterMiner")
    system.scheduler.schedule(Duration.create(15, TimeUnit.MINUTES), Duration.create(15, TimeUnit.MINUTES), master, WorkProgressHeartBeat)(system.dispatcher,master)
    master ! StartMining(leadingZeroes, loadSavedData, localMiners.toList)
  }
}

class BitCoinMiner extends Actor{
  private val log = Logging(context.system, this)
  private val hashGen = MessageDigest.getInstance("SHA-256")
  private var masterActor : ActorSelection = null
  private val gatorIdPrefix = "hsitas444;"

  override def receive: Receive = {
    case MineWork(leadingZeroes, start, end, createdTime) =>
      val leadingZeroesStr = "0" * leadingZeroes
      var currentNounce = new AlphaNumeric(start.toString())
      val work = MineWork(leadingZeroes,start,end, createdTime)
      log.debug("StartedWork: "+ work)
      while(currentNounce <= end){
        val inputStr = gatorIdPrefix + currentNounce
        val hash = hashGen.digest(inputStr.getBytes("UTF-8"))
        val hashStr = byteArrayToHexString(hash)
        if (hashStr.startsWith(leadingZeroesStr)){
          sender ! MineResult(new BitCoin(inputStr, hashStr))
        }
        currentNounce = currentNounce + 1
      }
      log.debug("CompletedWork: "+ work + "took "+ (System.currentTimeMillis()- work.sentTime)/1000.0 + "seconds")
      sender ! CompletedMineWork(work)

    case ConnectRemoteActor(actorIp) =>
      masterActor = context.actorSelection("akka.tcp://BitCoinMinerSystem@"+actorIp+":2552/user/BitCoinMasterMiner")
      masterActor ! GetWork
  }
  private def byteArrayToHexString(buf: Array[Byte]): String = buf.map("%02x" format _).mkString
}

class BitCoinMasterMiner extends Actor{
  private val log = Logging(context.system, this)
  private var leadingZeroes: Int = 0
  private var startTime = 0L
  private val workLoadSize = BigInt(10000000)
  private val workTimeoutDuration = 3600 * 1000; //1 hour
  private val progressFileName = "miningProgress.txt"

  private val inProgressWorks = new mutable.HashSet[MineWork]()
  private val abandonedWorks = new mutable.HashSet[MineWork]()
  private var startNounce = new AlphaNumeric("0")
  private var currentWorkingAlphaNum = startNounce

  //Keeps track of alphanumeric(starting from 0) upto which the Master is certain that work has been completed.
  private var completedWorkAlphanum = startNounce - 1
  //To store the out of order completed works so that when the next expecting work gets completed,
  //we can keep update completedWorkAlphaNum
  private var completedWorks = new mutable.ListBuffer[MineWork]()


  override def receive = {
    case StartMining(leadingZeroes: Int, loadSavedData: Boolean, localMiners: List[ActorRef])=>
      this.leadingZeroes = leadingZeroes
      if(loadSavedData){
        startNounce = loadProgress()
        currentWorkingAlphaNum = startNounce
        completedWorkAlphanum = startNounce - 1
      }

      //Send work to all the workers
      startTime = System.currentTimeMillis()
      localMiners.foreach((localMiner) => {
        localMiner ! genWork(leadingZeroes)
      })

    case MineResult(bitCoin: BitCoin) =>
      println(bitCoin.coin + "\t" + bitCoin.hash)
    case CompletedMineWork(work) =>
      //update the work completed progress
      log.debug("Work from " + work.start + " to "+ work.end+" complete")
      inProgressWorks.remove(work)
      sender ! genWork(leadingZeroes)
      processCompletedWork(work)
      saveProgess()

    case GetWork =>
      log.debug("Get Worked called by:" + sender)
      sender ! genWork(leadingZeroes)

    case WorkProgressHeartBeat =>
      inProgressWorks.foreach((e)=>{
        if(System.currentTimeMillis()> e.sentTime + workTimeoutDuration)
          abandonedWorks.add(e)
      })
      abandonedWorks.foreach((e) => {
        if(inProgressWorks.contains(e)) {
          log.warning("Abandoned work: " + e)
          inProgressWorks.remove(e)
        }
      })

  }

  private def genWork(leadingZeroes: Int): MineWork = {
    if(abandonedWorks.size>0){
      val abandonedWork = abandonedWorks.head
      abandonedWorks.remove(abandonedWork)
      val work = MineWork(abandonedWork.leadingZeroes, abandonedWork.start, abandonedWork.end, System.currentTimeMillis())
      inProgressWorks.add(work)
      log.warning("Resending abandoned work: "+ work)
      work
    }else {
      val work = MineWork(leadingZeroes, currentWorkingAlphaNum, currentWorkingAlphaNum + workLoadSize, System.currentTimeMillis())
      inProgressWorks.add(work)
      currentWorkingAlphaNum = currentWorkingAlphaNum + workLoadSize + 1
      work
    }
  }

  private def processCompletedWork(mineWork: MineWork): Unit ={
    if(mineWork.start != completedWorkAlphanum + 1){
      //Buffer the completed work item until we receive the work with start = completedWorkAlphanum + 1
      completedWorks += mineWork
      //Keep the completedWorks sorted so that when expected work item arrives,
      //we can move the completedWorkAlphanum efficiently
      completedWorks = completedWorks.sortWith((x,y)=>{
        x.start < y.start
      })
    }

    var i=0
    while (i < completedWorks.length && (completedWorkAlphanum + 1) == completedWorks(i).start){
      completedWorkAlphanum = completedWorks(i).end
      i+=1
    }
    //Remove all the continues work items less than current value of completedWorkAlphanumeric
    val (x, y) = completedWorks.splitAt(i)
    completedWorks = y
    //Note that scala ListBuffer handles the scenario when i > ListBuffer.length
  }

  private def saveProgess(): Unit = {
    val file = new File(progressFileName)
    val pw = new PrintWriter(file)
    try{
      pw.write(completedWorkAlphanum.toString())
    }catch{
      case e: Exception =>
        log.error(e, "Failed to Save Progress")
    }finally pw.close
  }

  private def loadProgress(): AlphaNumeric = {
    var startNounce = "0"
    try{
      val source = scala.io.Source.fromFile(progressFileName)
      startNounce = try source.mkString finally source.close()
      startNounce = startNounce.trim();
    }catch{
      case e: Exception =>
        log.error(e, "Failed to Load Progress")
    }
    new AlphaNumeric(startNounce)
  }

}



@SerialVersionUID(1L)
class BitCoin (val coin: String, val hash: String) extends Serializable{
  def ==(that: BitCoin): Boolean = this.coin.equals(that.coin) && this.hash.equals(that.hash)
}
@SerialVersionUID(2L)
case class MineWork(leadingZeroes: Int, start: AlphaNumeric, end: AlphaNumeric, sentTime: Long) extends Serializable
@SerialVersionUID(3L)
case class MineResult(bitCoin: BitCoin) extends Serializable
case class StartMining(leadingZeroes: Int, loadSavedData: Boolean, localMiners: List[ActorRef])
case class ConnectRemoteActor(actorIp: String)
@SerialVersionUID(6L)
case class GetWork() extends Serializable
@SerialVersionUID(7L)
case class CompletedMineWork(work :MineWork) extends Serializable
case class WorkProgressHeartBeat()
@SerialVersionUID(9L)
class AlphaNumeric(val alphaNumVal: String) extends Serializable{
  private val alphaNumChars = (('A' to 'Z') ++ ('a' to 'z') ++ ( '0' to '9')).toSet
  private val radix = 36
  private val minDisplayStrLen = 32
  if(!isAlphanumeric(alphaNumVal))
    throw new IllegalArgumentException("Not an alphanumeric Value")

  private var bigIntVal = BigInt(alphaNumVal, radix)

  def isAlphanumeric(s: String): Boolean =  {
    var str = s
    if(str.startsWith("-"))
      str = str.substring(1)
    str.forall(alphaNumChars.contains(_))
  }

  def <(that: AlphaNumeric): Boolean = bigIntVal < that.bigIntVal
  def <=(that: AlphaNumeric): Boolean = bigIntVal <= that.bigIntVal
  def >(that: AlphaNumeric): Boolean = bigIntVal > that.bigIntVal
  def >=(that: AlphaNumeric): Boolean = bigIntVal >= that.bigIntVal
  def ==(that: AlphaNumeric): Boolean = bigIntVal == that.bigIntVal
  def +(that: AlphaNumeric): AlphaNumeric = new AlphaNumeric((bigIntVal + that.bigIntVal).toString(radix))
  def -(that: AlphaNumeric): AlphaNumeric = new AlphaNumeric((bigIntVal - that.bigIntVal).toString(radix))
  def +(that: BigInt): AlphaNumeric = new AlphaNumeric((bigIntVal + that).toString(radix))
  def -(that: BigInt): AlphaNumeric = new AlphaNumeric((bigIntVal - that).toString(radix))
  override def toString(): String= {
    var s = bigIntVal.toString(radix)
    if(s.length() < minDisplayStrLen){
      if(bigIntVal < 0)
        s = "-" + ("0" * (minDisplayStrLen-s.length)) + s.substring(1)
      else s = ("0" * (minDisplayStrLen-s.length)) + s
    }
    s
  }

}
