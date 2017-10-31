package donebyme.registry.infra

import akka.actor._
import akka.io.IO
import akka.io.Udp
import akka.util.ByteString
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import java.net.InetSocketAddress
import javax.inject._
import play.api._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import donebyme.registry.models._
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
class StartupRunner @Inject() (system: ActorSystem) extends Startup {
  val host = System.getProperty("http.host")
  val port = System.getProperty("http.port")
  val broadcastPorts = System.getProperty("broadcast.ports", "9999").split(",")
  
  Registry.create(system)
  
  val properties = Map[String,String](
		  ("home" -> 		  s"http://${host}:${port}/registry/service"),
		  ("register" ->	s"http://${host}:${port}/registry/service/:name/info"),
		  ("query" ->		  s"http://${host}:${port}/registry/service/:name/info")
      )
  
  Registry() ! RegisterService("Registry", properties)
  
  val addresses =
    for {
      port <- broadcastPorts
      address = (new InetSocketAddress(host, Integer.parseInt(port)))
    } yield address
    
  val info = Json.toJson(properties).toString
  val broadcaster = system.actorOf(Props(classOf[UDPBroadcaster], addresses.toSeq, info), "broadcaster")
}

class UDPBroadcaster(remotes: Seq[InetSocketAddress], info: String) extends Actor {
  import context.system
  
  IO(Udp) ! Udp.SimpleSender

  context.system.scheduler.schedule(5 seconds, 5 seconds, self, info)
  
  def receive = {
    case Udp.SimpleSenderReady =>
      println("BROADCASTER READY")
      context.become(ready(sender))
  }
 
  def ready(send: ActorRef): Receive = {
    case message: String =>
      println(s"BROADCASTER BROADCASTING: $info")
      remotes.foreach { remote => send ! Udp.Send(ByteString(message), remote) }
  }
}
