package donebyme.pricing.views

import java.util.Date

import akka.actor._
import akka.persistence.query.{ PersistenceQuery, Offset }
import akka.persistence.query.scaladsl._
import akka.stream.{Materializer, ActorMaterializer}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import donebyme.pricing.models.{PriceVerified}

import scala.concurrent.duration._

final case class QueryPricingView(proposalId: String)
final case class QueryRejectedPricingView(proposalId: String)

final case class PricingViewResult(proposalId: String, view: Option[PricingView])
final case class RejectedPricingViewResult(proposalId: String, view: Option[RejectedPricingView])

object PricingView {
  def nothing = PricingView("", 0L)
}

object RejectedPricingView {
  def nothing = RejectedPricingView("", 0L)
}

final case class PricingView(description: String, price: Long)
final case class RejectedPricingView(description: String, suggestedPrice: Long)

object PricingViews {
  private var view: Option[ActorRef] = None

  def apply(): ActorRef = view.getOrElse { throw new IllegalStateException("Must create first.") }

  def create(system: ActorSystem): Unit = {
    if (view.isEmpty) {
      view = Some(system.actorOf(Props[PricingViews], "pricingView"))
    }
  }
}

class PricingViews extends Actor {
  var views = Map[String,PricingView]()
//  var rejectedViews = Map[String,RejectedPricingView]()

  var offset = Offset.sequence(0)

  implicit val materializer: Materializer = ActorMaterializer()(context.system)

  lazy val journal =
    PersistenceQuery(context.system)
        .readJournalFor("inmemory-read-journal")
        .asInstanceOf[ReadJournal with CurrentEventsByTagQuery]

  context.system.scheduler.schedule(5 seconds, 2 seconds, self, PricingViewsProjectionTick())

  def receive = {
    case pricingQuery: QueryPricingView =>
      sender ! PricingViewResult(pricingQuery.proposalId, views.get(pricingQuery.proposalId))

//    case rejectedQuery: QueryRejectedPricingView =>
//      sender ! RejectedPricingViewResult(rejectedQuery.proposalId, rejectedViews.get(rejectedQuery.proposalId))

    case _: PricingViewsProjectionTick =>
      projectEvents
  }

  def projectEvents: Unit = {
    journal.currentEventsByTag(tag = "matching", offset).runForeach { envelope =>
  	    println(s"PricingViews: $envelope")

  	    project(envelope.event)

        offset = envelope.offset
    }
  }

  def project(event: Any): Unit = {
    import donebyme.matching.models._

    // events may arrive out of order, so operations must be idempotent

    event match {
      case e: PriceVerified =>
        views = views + (e.proposalId -> PricingView(e.description, e.price))

//      case e: PriceRejected =>
//        views = views + (e.proposalId -> RejectedPricingView(e.description, e.suggestedPrice))
    }
  }
}

private final case class PricingViewsProjectionTick()
