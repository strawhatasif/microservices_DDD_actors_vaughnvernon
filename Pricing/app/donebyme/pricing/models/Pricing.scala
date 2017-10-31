package donebyme.pricing.models

import akka.actor.{ActorSystem, ActorRef, Props}
import akka.persistence.PersistentActor
import java.util.{Date, UUID}

// commands
case class VerifyPrice(proposalId: String, description: String, price: Long)
case class RejectPrice(proposalId: String, description: String, price: Long)

// events
case class PriceVerified(proposalId: String, description: String, price: Long)
case class PriceRejected(proposalId: String, description: String, suggestedPrice: Long)

// results
case class VerifyPriceResult(proposalId: String, description: String, price: Long)
case class RejectPricingResult(proposalId: String, description: String, suggestedPrice: Long)

object Pricing {
  def toSubmit(system: ActorSystem): ActorRef = {
    val proposalId = nextId
    system.actorOf(Props(classOf[Pricing], proposalId), proposalId)
  }

  private def nextId: String = UUID.randomUUID.toString
}

class Pricing(proposalId: String) extends PersistentActor {
  override def persistenceId: String = proposalId

  var state = PricingState.nothing

  override def receiveCommand: Receive = {
    case command: VerifyPrice =>
      persist(PriceVerified(proposalId, command.description, command.price)) { event =>
        state = realized(event)
        sender ! VerifyPriceResult(proposalId, event.description, event.price)
      }
    case command: RejectPrice =>
      persist(PriceRejected(proposalId, command.description, command.price)) { event =>
        state = realized(event)
        sender ! RejectPricingResult(proposalId, event.description, event.suggestedPrice)
      }
  }

  override def receiveRecover: Receive = {
    case event: PriceVerified =>
      state = realized(event)
  }

  private def realized(event: PriceVerified): PricingState = {
    PricingState.submittedAs(event.description, event.price)
  }

  private def realized(event: PriceRejected): PricingState = {
    state.rejectedBecause(event.suggestedPrice)
  }
}

object PricingState {
  def nothing = PricingState("", 0, 0L)

  def apply(description: String, price: Long): PricingState =
    new PricingState(description, price)

  def resubmittedWith(description: String, price: Long): PricingState =
    PricingState(description, price)

  def submittedAs(description: String, price: Long): PricingState =
    PricingState(description, price)
}

case class PricingState(description: String, price: Long, suggestedPrice: Long) {
  def this(description: String, price: Long) =
    this(description, price, 0)

  def rejectedBecause(suggestedPrice: Long) = {
    PricingState(this.description, this.price, suggestedPrice)
  }
}
