package Chord

import java.security.MessageDigest

import scala.util.Random

object ConsistentHasher{
  def getHash(inputStr:String, M:Int):Int = {
    val hashBytes = MessageDigest.getInstance("SHA-1").digest(inputStr.getBytes("UTF-8"))
    val first4Bytes = hashBytes.slice(0,4)
    var hashCode = 0;
    for (i <- first4Bytes.indices)
      hashCode = (hashCode << 8) + (first4Bytes(i)& 0xff);

    val mask = 0xffffffff >>> 32-M
    hashCode = hashCode & mask
    hashCode
  }

  def getRandomHashCode(M:Int):Int = {
    getHash(Random.alphanumeric.take(32).mkString, M)
  }
}