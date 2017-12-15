package me.dan.network

import java.net.{InetAddress, ServerSocket, Socket, SocketException}
import java.io._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global


case class Client(host: String, port: Int) {
  val ia = InetAddress.getByName(host)
  val socket = new Socket(ia, port)
  val in = new DataInputStream(socket.getInputStream())
  val out = new DataOutputStream(socket.getOutputStream())

  def receive {
    println(in.readUTF())
  }

  def close {
    out.close()
    in.close()
    socket.close()
  }
}

object ClientApp extends App {
  val client = Client(args(0), args(1).toInt)
  client.receive
  client.close

  Thread.sleep(3000)

  val closer = Client(args(0), 60000)
  closer.out.writeInt(0x00000001)
  closer.out.flush
  closer.close

}



case class Controller(socket: Socket) {
  val in = new DataInputStream(socket.getInputStream())
  val out = new DataOutputStream(socket.getOutputStream())

  def close {
    out.flush
    out.close
    in.close
    socket.close
  }

  def handle: Future[Int] = Future {
    val cmd = in.readInt
    println("Command " + cmd + " received.")
    cmd
  }
}

case class Handler(socket: Socket) {
  val in = new DataInputStream(socket.getInputStream())
  val out = new DataOutputStream(socket.getOutputStream())

  def close {
    out.flush
    out.close
    in.close
    socket.close
  }

  def handle: Future[Unit] = Future {
    out.writeUTF("ping")
    out.flush
  }

}

case class Server(port: Int = 0) {

  val listener = new ServerSocket(port)

  def accept: Future[Socket] = Future { listener.accept }

  def close = listener.close

  def getPort = listener.getLocalPort

  def isClosed = listener.isClosed
}


object ServerApp extends App {

  val controlServer = Server(60000)
  println("Control server at port " + controlServer.getPort)

  val server = Server()
  println("Bind at port " + server.getPort)

  sys.addShutdownHook {
    if(!server.isClosed) server.close
    if(!controlServer.isClosed) controlServer.close
  }

  val callback: PartialFunction[Socket, Unit] = {
    case socket: Socket => {
      val controller = new Controller(socket)
      Await.result(controller.handle, Duration.Inf) match {
        case 1 => {
          println("Received stop command.")
          controlServer.close
          server.close
        }
        case _ => {
          println("Received unknown command.")
          controlServer.accept.onSuccess(callback)
        }
      }
    }
  }

  controlServer.accept.onSuccess(callback)

  while(!server.isClosed) {
    val f = server.accept
    f.onSuccess {
        case socket: Socket => {
          val handler = Handler(socket)
          Await.ready(handler.handle, Duration.Inf)
          handler.close
        }
      }
    Await.ready(f, Duration.Inf)
  }

}
