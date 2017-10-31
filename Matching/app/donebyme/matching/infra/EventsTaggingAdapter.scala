package donebyme.matching.infra

import akka.persistence.journal.{Tagged, WriteEventAdapter}

class EventsTaggingAdapter extends WriteEventAdapter {
  val tag = Set("matching")

  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any = {
    Tagged(event, tag)
  }
}
