package donebyme.registry.controllers

import akka.pattern.ask
import akka.util.Timeout
import javax.inject._
import donebyme.registry.models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._

@Singleton
class RegistryController extends Controller {

  implicit val timeout = Timeout(5.seconds)
  
  // curl -H 'Content-Type: application/json' -X PUT -d '{"one":"1","two":"2","three":"3"}' http://localhost:9001/registry/service/Test/info
  
  def registerServiceInfo(name: String) = Action.async { request =>

    val info = request.body.asJson.get.as[Map[String,String]]
    
    val future = Registry() ? RegisterService(name, info)

    future.mapTo[ServiceRegistered].map { result =>
      Ok(s"Registered service info for ${result.name}")
    }
  }
  
  // curl -X GET http://localhost:9001/registry/service/Test/info
  
  def getServiceInfo(name: String) = Action.async {
    val future = Registry() ? QueryServiceInfo(name)

    future.mapTo[ServiceInfoResult].map { result =>
      Ok(Json.toJson(result.properties))
    }
  }
}
