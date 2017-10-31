package donebyme.pricing.infra

import akka.actor._
import akka.io.IO
import akka.io.Udp
import com.google.inject.AbstractModule
import java.net.InetSocketAddress
import javax.inject._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.libs.ws._
import donebyme.common.infra._
import donebyme.pricing.infra._
import donebyme.pricing.views._
import scala.concurrent.duration._

class StartupModule extends AbstractModule {
  override def configure() = {
    bind(classOf[Startup]).to(classOf[StartupRunner]).asEagerSingleton
  }
}

trait Startup {
  val zero = 0
}

@Singleton
class StartupRunner @Inject() (system: ActorSystem, ws: WSClient) extends Startup {
  val host = System.getProperty("http.host")
  val port = System.getProperty("http.port")
  val broadcastPort = System.getProperty("broadcast.port", "9990")

  PricingViews.create(system)

  TopicsFeeder.create(
    system,
    TopicFeederInfo(
      "ALL",
      Seq("matching"),
      ws))

  TopicsReader.create(
    system,
    TopicReaderInfo(
      "ALL",
      system.actorOf(Props[TopicsReaderConsumer], "topicReaderConsumer-ALL"),
      ws))

  val properties = Map[String,String](
    ("home" -> 		s"http://${host}:${port}/pricing"),
    ("verifyPrice" ->	s"http://${host}:${port}/pricing/price"),
    ("rejectPrice" ->		s"http://${host}:${port}/pricing/rejectedPrice"),
    ("price" ->		s"http://${host}:${port}/pricing/price/:id") ,
    ("rejectedPrice" ->		s"http://${host}:${port}/pricing/rejectedPrice/:id")
  )

  val address = new InetSocketAddress(host, Integer.parseInt(broadcastPort))
  val matchingServiceInfo = Json.toJson(properties).toString
  val heartbeat = system.actorOf(Props(classOf[Heartbeat], address, matchingServiceInfo, ws), "heartbeat")
}

class Heartbeat(address: InetSocketAddress, matchingServiceInfo: String, ws: WSClient) extends Actor {
  var registryInfo: Option[Map[String,String]] = None

  import scala.util.{Success, Failure}
  import context.system
  IO(Udp) ! Udp.Bind(self, address, List(Udp.SO.Broadcast(true)))

  context.system.scheduler.schedule(5 seconds, 5 seconds, self, matchingServiceInfo)

  def receive = {
    case Udp.Bound(local) =>
      context.become(ready(sender))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      val json = data.decodeString("UTF-8")
      registryInfo = Some(Json.parse(json).as[Map[String,String]])
      acquireTopicsInfo

    case info: String =>
      registerMatchingService

    case Udp.Unbind  =>
      socket ! Udp.Unbind

    case Udp.Unbound =>
      context.stop(self)
  }

  private def acquireTopicsInfo: Unit = {
    val url = registryInfo.get("query").replace(":name", "Topics")

    val request: WSRequest = ws.url(url)

    request.get() onComplete {
      case Failure(t) => println("MATCHING FAILED GET TOPICS INFO: " + t.getMessage)
      case Success(result) =>
        println("MATCHING GOT TOPICS INFO: " + result.body)
        val info = Json.parse(result.body.toString).as[Map[String,String]]
        val appenderLocation = info.get("append")
        if (appenderLocation.isDefined)
          TopicsFeeder() ! TopicAppenderLocation(appenderLocation.get)
        val currentLogLocation = info.get("currentLog")
        val messageLocation = info.get("message")
        if (currentLogLocation.isDefined && messageLocation.isDefined)
          TopicsReader() ! TopicReaderLocation(currentLogLocation.get, messageLocation.get)
    }
  }

  private def registerMatchingService: Unit = {

    if (registryInfo.isDefined) {
      val url = registryInfo.get("register").replace(":name", "Matching")

      val request: WSRequest = ws.url(url).withHeaders("Content-Type" -> "application/json")

      request.put(matchingServiceInfo) onComplete {
        case Failure(t) => println("MATCHING FAILED REGISTRATION: " + t.getMessage)
        case Success(result) => println("MATCHING REGISTERED INFO: " + result.body)
      }
    }
  }
}
