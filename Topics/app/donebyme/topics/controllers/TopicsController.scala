package donebyme.topics.controllers

import akka.pattern.ask
import akka.util.Timeout
import javax.inject._

import donebyme.topics.models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent._
import scala.concurrent.duration._

@Singleton
class TopicsController extends Controller {

  implicit val timeout = Timeout(5.seconds)

  // curl -D -H 'Content-Type: application/json' -X POST -d '{"event":"payload2"}' http://localhost:9002/topics/Test
  
  def append(topic: String) = Action.async(parse.tolerantText) { request =>
    val message = request.body

    if (!message.isEmpty) {
      val future = Topics() ? AppendMessage(topic, message)

      future.mapTo[MessageAppended].map { appended =>
        Created(appended.message).withHeaders(LOCATION -> messageLocation(request, appended))
      }
    } else Future.successful(BadRequest("Message is empty."))
  }
  
  //  curl -X GET http://localhost:9002/topics/Test/logs/current
  
  def currentLog(topic: String) = Action.async { request =>
    val future = Topics() ? QueryCurrentLog(topic)
    
    future.mapTo[CurrentLogResult].map { result =>
      val log = MessageLog(result.topic, result.id, "", result.previousId, result.messages)
      val response = Ok(s"Messages: ${result.id}")
      log.headersFor(request, response)
    }
  }

  //  curl -X GET http://localhost:9002/topics/Test/logs/1-5
  
  def log(topic: String, id: String) = Action.async { request =>
    val future = Topics() ? QueryLog(topic, id)
    
    future.mapTo[LogResult].map { result =>
      val log = MessageLog(result.topic, result.id, result.nextId, result.previousId, result.messages)
      val response = Ok(s"Messages: ${result.id}")
      log.headersFor(request, response)
    }
  }
  
  // curl -X GET http://localhost:9002/topics/Test/messages/1
  
  def message(topic: String, id: Long) = Action.async {
    val future = Topics() ? QueryMessage(topic, id)
    
    future.mapTo[MessageResult].map { result =>
      if (result.message.isEmpty)
        NotFound("Message doesn't exist")
      else
    	Ok(result.message)
    }
  }
  
  private def messageLocation(
      request: Request[String],
      appended: MessageAppended): String = {
    
    s"/topics/${appended.topic}/messages/${appended.messageId}"
  }
}

case class MessageLog(
    topic: String,
    id: String,
    nextId: String,
    previousId: String,
    messages: Seq[LogMessage]) {
  
  implicit val logMessageWrites: Writes[LogMessage] = (
    (JsPath \ "messageId").write[Long] and
    (JsPath \ "message").write[String]
  )(unlift(LogMessage.unapply))

  def headersFor(request: Request[AnyContent], result: Result): Result = {
    val links = new StringBuilder()
    
    links.append(link(request, "self", id))
    
    if (!nextId.isEmpty)
      links.append("; ").append(link(request, "next", nextId))
    
    if (!previousId.isEmpty)
      links.append("; ").append(link(request, "previous", previousId))
    
    result.withHeaders("Link" -> links.toString)
  }
  
  private def link(request: Request[AnyContent], rel: String, id: String): String = {
    s"""<link rel="${rel}" href="http://${request.host}/topics/${topic}/logs/${id}" />"""
  }
}
