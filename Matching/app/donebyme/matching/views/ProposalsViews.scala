package donebyme.matching.views

import java.util.Date

import akka.actor._
import akka.persistence.query.{ PersistenceQuery, Offset }
import akka.persistence.query.scaladsl._
import akka.stream.{Materializer, ActorMaterializer}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.duration._

final case class QueryProposalView(proposalId: String)

final case class ProposalViewResult(proposalId: String, view: Option[ProposalView])

object ProposalView {
  def nothing = ProposalView("", 0, new Date())
}

final case class ProposalView(description: String, price: Long, expectedCompletion: Date)

object ProposalsViews {
  private var view: Option[ActorRef] = None
  
  def apply(): ActorRef = view.getOrElse { throw new IllegalStateException("Must create first.") }
  
  def create(system: ActorSystem): Unit = {
    if (view.isEmpty) {
      view = Some(system.actorOf(Props[ProposalsViews], "proposalsView"))
    }
  }
}

class ProposalsViews extends Actor {
  var views = Map[String,ProposalView]()
  var offset = Offset.sequence(0)

  implicit val materializer: Materializer = ActorMaterializer()(context.system)

  lazy val journal =
    PersistenceQuery(context.system)
        .readJournalFor("inmemory-read-journal")
        .asInstanceOf[ReadJournal with CurrentEventsByTagQuery]

  context.system.scheduler.schedule(5 seconds, 2 seconds, self, ProposalsViewsProjectionTick())

  def receive = {
    case query: QueryProposalView =>
      sender ! ProposalViewResult(query.proposalId, views.get(query.proposalId))
      
    case _: ProposalsViewsProjectionTick =>
      projectEvents
  }

  def projectEvents: Unit = {
    journal.currentEventsByTag(tag = "matching", offset).runForeach { envelope =>
  	    println(s"ProposalsViews: $envelope")

  	    project(envelope.event)

        offset = envelope.offset
    }
  }
  
  def project(event: Any): Unit = {
    import donebyme.matching.models._

    // events may arrive out of order, so operations must be idempotent

    event match {
      case e: ProposalSubmitted =>
        views = views + (e.proposalId -> ProposalView(e.description, e.price, e.expectedCompletion))
    }
  }
}

private final case class ProposalsViewsProjectionTick()
