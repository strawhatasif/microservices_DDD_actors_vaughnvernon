package donebyme.topics.infra

import akka.actor._
import akka.io.IO
import akka.io.Udp
import com.google.inject.AbstractModule
import java.net.InetSocketAddress
import javax.inject._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.libs.ws._
import donebyme.topics.models._
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
  val broadcastPort = System.getProperty("broadcast.port", "9999")
  
  Topics.create(system)
  
  val properties = Map[String,String](
		  ("home" -> 		    s"http://${host}:${port}/topics"),
		  ("append" ->		  s"http://${host}:${port}/topics/:topic"),
		  ("message" ->     s"http://${host}:${port}/topics/:topic/messages/:id"),
		  ("currentLog" ->	s"http://${host}:${port}/topics/:topic/logs/current"),
		  ("log" ->			    s"http://${host}:${port}/topics/:topic/logs/:low-:high")
      )
  
  val address = new InetSocketAddress(host, Integer.parseInt(broadcastPort))
  val topicServiceInfo = Json.toJson(properties).toString
  val heartbeat = system.actorOf(Props(classOf[Heartbeat], address, topicServiceInfo, ws), "heartbeat")
}

class Heartbeat(address: InetSocketAddress, topicServiceInfo: String, ws: WSClient) extends Actor {
  var registryInfo: Option[Map[String,String]] = None
  
  import context.system
  IO(Udp) ! Udp.Bind(self, address, List(Udp.SO.Broadcast(true)))
 
  context.system.scheduler.schedule(5 seconds, 5 seconds, self, topicServiceInfo)
  
  def receive = {
    case Udp.Bound(local) =>
      context.become(ready(sender))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      val json = data.decodeString("UTF-8")
      registryInfo = Some(Json.parse(json).as[Map[String,String]])
      
    case info: String =>
      registerTopicsService
      
    case Udp.Unbind  =>
      socket ! Udp.Unbind
    
    case Udp.Unbound =>
      context.stop(self)
  }
  
  private def registerTopicsService: Unit = {
    import scala.util.{Success, Failure}
    
    if (registryInfo.isDefined) {
      val url = registryInfo.get("register").replace(":name", "Topics")
      
      val request: WSRequest = ws.url(url).withHeaders("Content-Type" -> "application/json")

      request.put(topicServiceInfo) onComplete {
          case Failure(t) => println("TOPICS FAILED REGISTRATION: " + t.getMessage)
          case Success(result) => println("TOPICS REGISTERED INFO: " + result.body)
      }
    }
  }
}
