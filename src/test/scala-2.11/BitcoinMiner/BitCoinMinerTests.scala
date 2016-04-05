package BitcoinMiner

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration.Duration

class BitCoinMinerTests(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("BitCoinMinerSystem"))
  "A BitCoinMiner actor" must {
    "send CompletedMineWork(inputWork) after it has finished mining the work" in {
      val actorRef = TestActorRef[BitCoinMiner]
      val work = MineWork(4, new AlphaNumeric("0"), new AlphaNumeric("1"), System.currentTimeMillis())
      actorRef ! work
      expectMsg(CompletedMineWork(work))
    }

    "send BitCoin if it finds the Bitcoin in the assigned Work" in {
      val actorRef = TestActorRef[BitCoinMiner]
      val work = MineWork(8, new AlphaNumeric("000000000000000000000000013s83oa"), new AlphaNumeric("000000000000000000000000013s83od"), System.currentTimeMillis())
      actorRef ! work
      expectMsg(MineResult(new BitCoin("hsitas444;000000000000000000000000013s83oc", "0000000039220951c54b48dc667d5f567aaaa5f77fb81c0fa11392c06b1c61d3")))
      //After it finishes work it must send CompletedMineWork message
      expectMsg(CompletedMineWork(work))
    }

  }

}


class BitCoinMasterMinerTests(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("BitCoinMinerSystem"))
  "A BitCoinMasterMiner actor" must {
    "send appropriate work to the Local Workers" in {
      var workReceived = false
      val mockLocalMiner = TestActorRef(new Actor {
        def receive = {
          case MineWork(leadingZeroes, start, end, createdTime) =>
            if(leadingZeroes == 4 && start == new AlphaNumeric("0"))
              workReceived = true
        }
      })
      val actorRef = TestActorRef[BitCoinMasterMiner]
      within(Duration.create(200, TimeUnit.MILLISECONDS)) {
        expectNoMsg//will block for the rest of the 200ms, for the mock actor to receive the message
      }
      actorRef ! StartMining(4, false, List{mockLocalMiner})
      assert(workReceived)
    }

    "send mutually exclusive, incremental work to Workers" in {
      var worker1_end = new AlphaNumeric("0")
      var worker2_start = new AlphaNumeric("0")
      val mockLocalWorker1= TestActorRef(new Actor {
        def receive = {
          case MineWork(leadingZeroes, start, end, createdTime) => worker1_end = end
        }
      })
      val mockLocalWorker2= TestActorRef(new Actor {
        def receive = {
          case MineWork(leadingZeroes, start, end, createdTime) => worker2_start = start
        }
      })
      val actorRef = TestActorRef[BitCoinMasterMiner]
      within(Duration.create(200, TimeUnit.MILLISECONDS)) {
        expectNoMsg//will block for the rest of the 200ms, for the mock actor to receive the message
      }
      actorRef ! StartMining(4, false, List(mockLocalWorker1, mockLocalWorker2))
      assert(worker1_end + 1 == worker2_start)
    }

    "send the next batch of work on receiving GetWork message" in {
      val actorRef = TestActorRef[BitCoinMasterMiner]
      //Initializes the master
      actorRef ! StartMining(4, false, List())
      //Test getWork Message
      actorRef ! GetWork
      var response = expectMsgPF[MineWork](Duration.create(200,TimeUnit.MILLISECONDS))({
        //return the response received
        case MineWork(leadingZeroes, start, end, sentTime) => MineWork(leadingZeroes, start, end, sentTime)
      })
      assert(response.start == new AlphaNumeric("0"))
      val endVal = response.end
      assert(response.leadingZeroes == 4)

      //Get next batch of work
      actorRef ! GetWork
      response = expectMsgPF[MineWork](Duration.create(200,TimeUnit.MILLISECONDS))({
        //return the response received
        case MineWork(leadingZeroes, start, end, sentTime) => MineWork(leadingZeroes, start, end, sentTime)
      })
      assert(response.start == endVal + 1)
      assert(response.leadingZeroes == 4)

    }
  }

}

