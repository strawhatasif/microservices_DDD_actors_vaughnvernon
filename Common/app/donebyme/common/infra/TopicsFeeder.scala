package donebyme.common.infra

import akka.actor._
import akka.persistence.query.{ PersistenceQuery, Offset }
import akka.persistence.query.scaladsl._
import akka.stream.{Materializer, ActorMaterializer}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws._
import scala.concurrent.duration._

final case class TopicAppenderLocation(location: String)

final case class TopicFeederInfo(topicName: String, readTags: Seq[String], ws: WSClient)

object TopicsFeeder {
  private var feeder: Option[ActorRef] = None
  
  def apply(): ActorRef = feeder.getOrElse { throw new IllegalStateException("Must create first.") }
  
  def create(system: ActorSystem, info: TopicFeederInfo): Unit = {
    if (feeder.isEmpty) {
      feeder = Some(system.actorOf(Props(classOf[TopicsFeeder], info), s"topicFeeder-${info.topicName}"))
    }
  }
}

class TopicsFeeder(info: TopicFeederInfo) extends Actor {
  var location: String = ""
  var offset: Offset = Offset.sequence(0)
  
  import donebyme.common.tools.ObjectSerializer
  val serializer = new ObjectSerializer(true)
  
  context.system.scheduler.schedule(5 seconds, 2 seconds, self, FeedTick())

  def receive = {
    case loc: TopicAppenderLocation =>
      location = loc.location.replace(":topic", info.topicName)
      
    case _: FeedTick =>
      if (!location.isEmpty)
        feedTopic
  }
  
  def feedTopic: Unit = {
    implicit val materializer: Materializer = ActorMaterializer()(context.system)

    lazy val journal =
      PersistenceQuery(context.system)
          .readJournalFor("inmemory-read-journal")
          .asInstanceOf[ReadJournal with CurrentEventsByTagQuery]

    for (tag <- info.readTags)
      readJournal(journal, tag)
  }
  
  def feed(serializedEvent: String) {
    import scala.util.{Success, Failure}
    
    println(s"*********** TOPICS FEEDER READY TO POST TO: ${location} WITH: ${serializedEvent}")
    
    val request: WSRequest = info.ws.url(location).withHeaders("Content-Type" -> "application/json")
    
    request.post(serializedEvent) onComplete {
      case Failure(t) => println("TOPICS FEEDER FAILED APPEND: " + t.getMessage)
      case Success(result) => println("TOPICS FEEDER SUCCESSFULLY APPENDED: " + result.body)
    }
  }
  
  def readJournal(journal: CurrentEventsByTagQuery, readTag: String)(implicit materializer: Materializer): Unit = {
    journal.currentEventsByTag(tag = readTag, offset = offset).runForeach { envelope =>
  	  println(s"TopicFeeder: $envelope")

      feed(serializer.serialize(MessageEnvelope(envelope.event.getClass.getName, envelope.event)))

      offset = envelope.offset
    }
  }
}

private case class MessageEnvelope(messageType: String, message: Any)

private final case class FeedTick()
