package me.dan.lambda

import akka.actor.{Actor, ActorSystem, Props}

object Talkers extends App {

  val actorSystem = ActorSystem("Talkers")

  class Usher(name: String) extends Actor {
    def receive = {
      case message: String => HeavyWork.work(s"[$name]: $message")
    }
  }

  def newUsher(n: Int) = actorSystem.actorOf(Props(new Usher(s"bob$n")))

  val bobs = (0 to 3).map(newUsher _).toArray

  (0 to 7).foreach(n => {
    val actor = bobs(n % 4)
    actor ! n.toString
  })

  Thread.sleep(10000)
  actorSystem.terminate()

}
