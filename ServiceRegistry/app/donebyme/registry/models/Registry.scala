package donebyme.registry.models

import akka.actor._

object Registry {
  private var registry: Option[ActorRef] = None
  
  def apply(): ActorRef = registry.getOrElse { throw new IllegalStateException("Must create first.") }
  
  def create(system: ActorSystem): Unit = {
    if (registry.isEmpty) {
      registry = Some(system.actorOf(Props[Registry], "registry"))
    }
  }
}

case class QueryServiceInfo(name: String)
case class ServiceInfoResult(name: String, properties: Map[String,String])
case class RegisterService(name: String, properties: Map[String,String])
case class ServiceRegistered(name: String)

class Registry extends Actor {
  var services = Map[String,Map[String,String]]()
  
  def receive = {
    case register: RegisterService =>
      services = services + (register.name -> register.properties)
      
      sender ! ServiceRegistered(register.name)
      
    case query: QueryServiceInfo =>
      val properties =
        if (services.contains(query.name))
          services.get(query.name).get
        else
          Map[String,String]()
          
      sender ! ServiceInfoResult(query.name, properties)
  }
}
