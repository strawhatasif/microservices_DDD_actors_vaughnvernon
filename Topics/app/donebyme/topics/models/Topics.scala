package donebyme.topics.models

import akka.actor._

object Topics {
  private var topics: Option[ActorRef] = None
  
  def apply(): ActorRef = topics.getOrElse { throw new IllegalStateException("Must create first.") }
  
  def create(system: ActorSystem): Unit = {
    if (topics.isEmpty) {
      topics = Some(system.actorOf(Props[Topics], "topics"))
    }
  }
}

case class AppendMessage(topic: String, message: String)
case class MessageAppended(topic: String, messageId: Int, message: String)
case class QueryMessage(topic: String, messageId: Long)
case class MessageResult(topic: String, messageId: Long, message: String)
case class QueryCurrentLog(topic: String)
case class CurrentLogResult(topic: String, id: String, messages: Seq[LogMessage], previousId: String)
case class QueryLog(topic: String, id: String)
case class LogResult(topic: String, id: String, messages: Seq[LogMessage], nextId: String, previousId: String, archived: Boolean)

case class LogId(low: Long, high: Long) {
  def hasNext(count: Long): Boolean = high < count

  def hasPrevious: Boolean = low > 1
  
  def next: LogId = LogId(low + LogId.messagesPerLog, high + LogId.messagesPerLog)
  
  def previous: LogId = LogId(low - LogId.messagesPerLog, high - LogId.messagesPerLog)
  
  override def toString(): String = s"$low-$high"
}

object LogId {
  val messagesPerLog = 5

  def archived(rangeIdFormat: String, count: Long): LogId = {
    val id = fromRange(rangeIdFormat)
    
    if ((id.low - 1) % messagesPerLog != 0)
      throw new IllegalArgumentException("Topic log ID out of range.")
    
    if (id.high % messagesPerLog != 0)
      throw new IllegalArgumentException("Topic log ID out of range.")
    
    id
  }
  
  def current(count: Long): LogId = {
    var remainder = count % LogId.messagesPerLog
    
    if (remainder == 0 && count > 0)
      remainder = LogId.messagesPerLog
  
    val low = count - remainder + 1
    val high = low + LogId.messagesPerLog - 1
    
    LogId(low, high)
  }
  
  def fromRange(id: String): LogId = {
    val lowHigh = id.split("-")
    LogId(lowHigh(0).toLong, lowHigh(1).toLong)
  }
}

case class LogMessage(messageId: Long, message: String)

object LogMessage {
  def allFrom(id: LogId, rawMessages: Seq[String]): Seq[LogMessage] = {
    var index = id.low.toInt - 1
    val messages = rawMessages.slice(index, id.high.toInt)
    messages.map { message =>
      index += 1
      LogMessage(index, message)
    }
  }
}

class Topics extends Actor {

  var topics = Map[String,Seq[String]]()
  
  def receive = {
    case append: AppendMessage =>
      var messages =
        if (topics.contains(append.topic))
          topics.get(append.topic).get
        else
          Seq[String]()
      
      val messageId = messages.size + 1
      messages = messages :+ append.message
      topics = topics + (append.topic -> messages)
      
      println(s"TOPICS: APPENDING ID: ${messageId}: MESSAGE: ${append.message}")
      
      sender ! MessageAppended(append.topic, messageId, append.message)
      
    case query: QueryMessage =>
      var messages = topicMessages(query.topic)
      
      var message =
        if (query.messageId <= 0 || query.messageId > messages.size)
          ""
        else
          messages(query.messageId.toInt - 1)
      
      sender ! MessageResult(query.topic, query.messageId, message)
      
    case query: QueryCurrentLog =>
      val messages = topicMessages(query.topic)
      val id = LogId.current(messages.size)
      val previousId = if (id.hasPrevious) id.previous.toString else ""
      val log = LogMessage.allFrom(id, messages)
      
      sender ! CurrentLogResult(query.topic, id.toString, log, previousId)
      
    case query: QueryLog =>
      val messages = topicMessages(query.topic)
      val id = LogId.archived(query.id, messages.size)
      val currentId = LogId.current(messages.size)
      val nextId = if (id.hasNext(messages.size)) id.next.toString else ""
      val previousId = if (id.hasPrevious) id.previous.toString else ""
      val log = LogMessage.allFrom(id, messages)
      
      sender ! LogResult(query.topic, id.toString, log, nextId, previousId, id != currentId)
  }
  
  def topicMessages(name: String): Seq[String] = {
    if (topics.contains(name))
      topics.get(name).get
    else
      Seq[String]()
  }
}
