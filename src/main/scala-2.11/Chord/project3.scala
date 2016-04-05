package Chord

import Chord.ChordMaster.SimulateChordNetwork
import akka.actor._

object project3 extends App{

  if(args.length < 2) {
    println("Invalid Arguments")
    showUsage()
  } else {
    try{
      val numNodes = args(0).toInt
      val numRequests = args(1).toInt
      val actorSystem = ActorSystem("ChordSystem")
      val master = actorSystem.actorOf(Props(new ChordMaster()), name="master")
      master ! SimulateChordNetwork(numNodes, numRequests)
    }catch{
      case e: NumberFormatException =>
        println("Error: numNodes and numRequests must be an Integer")
        showUsage()
        System.exit(1)
    }

  }

  def showUsage():Unit = {
    println("\tUsage:")
    println("\t\tsbt \"run numNodes numRequests\"")
  }

}





