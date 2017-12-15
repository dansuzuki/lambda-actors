package me.dan.lambda

import java.security.MessageDigest

object HeavyWork {

  def work(message: String) {
    val pref = "000"
    val r = scala.util.Random

    @scala.annotation.tailrec
    def step {
      val randomNum = r.nextInt
      val inBytes = s"$message$randomNum".getBytes
      val digest = MessageDigest.getInstance("MD5").digest(inBytes)
      if(!digest.startsWith(pref)) step
      else println(randomNum + " " + digest)
    }
    step
  }

}
