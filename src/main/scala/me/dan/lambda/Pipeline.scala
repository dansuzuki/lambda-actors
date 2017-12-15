package me.dan.lambda

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.{Tweet}
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage
import com.danielasfregola.twitter4s.entities.streaming.common.{DisconnectMessage, LimitNotice, WarningMessage}

import org.joda.time.DateTime

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global


/**
 * Example implementation of lambda architecture using actor pattern
 */
object Pipeline extends App {
  println("This is the pipeline")

  val actorSystem = ActorSystem("LambdaPipeline")

  case class TweetPlace(timestamp: Long, user: String, place: String)




  class DataSinker extends Actor {
    import com.ibm.couchdb._

    val typeMapping = TypeMapping(classOf[TweetPlace] -> "TweetPlace")

    val couch = CouchDb("127.0.0.1", 5984)
    val db = couch.db("tweet_places", typeMapping)

    def receive = {
      case tp: TweetPlace => try {
          val task = db.docs.create(tp)
          task.run
        } catch {
          case e: Exception => e.printStackTrace()
        }

      case _ => { }
    }
  }

  class SpeedAggregator extends Actor {

    val places = {
      val _m = collection.mutable.Map[(Long, String), Int]()
      _m.withDefault(k => 0)
    }

    var ctr: Long = 0L
    def receive = {
      case tp: TweetPlace => {
        places((timeGroup(tp.timestamp, 60), tp.place)) += 1

        ctr = ctr + 1
        if(ctr % 50 == 0) render
        if(ctr % 1000 == 0) window
      }
      case _ => { }
    }

    def render {
      println("------------------------------")

      // for the current hour only
      val start = timeGroup(System.currentTimeMillis, 60)

      places
        .filter(_._1._1 >= start)
        .map(kv => (kv._1._2, kv._2))
        .toList
        .sortBy(_._2)
        .reverse
        .take(20)
        .foreach(kv => println("%-70s %10d".format(kv._1, kv._2)))
      println("------------------------------\n\n")
    }

    def timeGroup(tm: Long, minutes: Int): Long = {
      val millis = minutes * 60 * 1000L
      (tm / millis) * millis
    }

    /**
     * apply the sliding window function on places
     */
    def window {
      val now = DateTime.now().getMillis()
      val start = timeGroup(System.currentTimeMillis, 60)

      places
        .filter(_._1._1 < start)
        .keys
        .foreach(k => places.remove(k))

    }
  }

  val dataSinker = actorSystem.actorOf(Props[DataSinker], name = "DataSinker")
  val speedAggregator = actorSystem.actorOf(Props[SpeedAggregator], name = "SpeedAggregator")

  def streamFunc: PartialFunction[StreamingMessage, Unit] = {
    case tweet: Tweet => {
      val timestamp = tweet.created_at.getTime()
      val user = tweet.user.map(_.screen_name).getOrElse("").trim
      val place = tweet.place.map(_.full_name).getOrElse("").trim
      val tp = TweetPlace(timestamp, user, place)
      dataSinker ! tp
      speedAggregator ! tp
    }

    case dm: DisconnectMessage => { println(dm) }
    case lm: LimitNotice => { println(lm) }
    case wm: WarningMessage => { println(wm) }

    case _ => { println("Catch all handler") }
  }



  val client = TwitterStreamingClient()
  val locs = Seq(116.930, 4.650, 126.600, 20.840)
  val stream = client.filterStatuses(locations = locs)(streamFunc)

  /** cleanup stuffs */
  sys.addShutdownHook {
    println("Closing the stream...")
    stream foreach(_.close())

    println("Terminating the actor system...")
    actorSystem.terminate()
  }

} /** end of Pipeline */
