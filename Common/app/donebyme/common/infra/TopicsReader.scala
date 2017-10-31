package donebyme.common.infra

import akka.actor._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws._
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

final case class TopicReaderConsumerMessage(messageId: Long, message: String)

final case class TopicReaderInfo(topicName: String, consumer: ActorRef, ws: WSClient)

final case class TopicReaderLocation(currentLogLocation: String, messageLocation: String)

object TopicsReader {
  private var reader: Option[ActorRef] = None
  
  def apply(): ActorRef = reader.getOrElse { throw new IllegalStateException("Must create first.") }
  
  def create(system: ActorSystem, info: TopicReaderInfo): Unit = {
    if (reader.isEmpty) {
      reader = Some(system.actorOf(Props(classOf[TopicsReader], info), s"topicReader-${info.topicName}"))
    }
  }
}

class TopicsReader(info: TopicReaderInfo) extends Actor {
  var currentLogLocation: String = ""
  var messageLocation: String = ""
  var highestMessageId = 0L
  
  context.system.scheduler.schedule(5 seconds, 2 seconds, self, ReadTick())

  def receive = {
    case loc: TopicReaderLocation =>
      currentLogLocation = loc.currentLogLocation.replace(":topic", info.topicName)
      messageLocation = loc.messageLocation.replace(":topic", info.topicName)
      
    case _: ReadTick =>
      if (!currentLogLocation.isEmpty)
        consumeAllUnread
  }
  
  def consumeAllUnread: Unit = {
    var end = false
    
    do {
      highestMessageId += 1
      
      val message = readMessage(highestMessageId)
      
      if (message.isDefined) {
        info.consumer ! TopicReaderConsumerMessage(highestMessageId, message.get)
      } else {
        end = true
        highestMessageId -= 1
      }
      
    } while (!end)
  }
  
  def readMessage(messageId: Long): Option[String] = {
    val messageLocationURL = messageLocation.replace(":id", messageId.toString)
    
    println(s"*********** TOPICS READER READY TO GET MESSAGE: ${messageId} AT URL: ${messageLocationURL}")
    
    val request: WSRequest = info.ws.url(messageLocationURL)
    
    // should be redesigned rather than block.
    // I attempted to use scala.async but had
    // trouble with compiler versions. see:
    // https://github.com/scala/async
    val result = Await.result(request.get(), 5 seconds)
    
    println(s"TOPICS READER SUCCESSFULLY GOT STATUS: ${result.status} BODY: ${result.body}")
      
    if (result.status == 200)
      Some(result.body)
    else
      None
  }
}

private final case class ReadTick()
