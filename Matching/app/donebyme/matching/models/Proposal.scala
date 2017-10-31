package donebyme.matching.models

import akka.actor.{ActorSystem, ActorRef, Props}
import akka.persistence.PersistentActor
import java.util.{Date, UUID}

// commands
case class SubmitProposal(description: String, price: Long, expectedCompletion: Date)
case class RejectPricing(proposalId: String, suggestedPrice: Long)

// events
case class ProposalSubmitted(proposalId: String, description: String, price: Long, expectedCompletion: Date)
case class ProposalResubmitted(proposalId: String, description: String, price: Long, expectedCompletion: Date)
case class ProposalPricingRejected(proposalId: String, suggestedPrice: Long)

// results
case class SubmitProposalResult(proposalId: String, description: String, price: Long, expectedCompletion: Date)
case class RejectPricingResult(proposalId: String, suggestedPrice: Long)

object Proposal {
  def toSubmit(system: ActorSystem): ActorRef = {
    val proposalId = nextId
    system.actorOf(Props(classOf[Proposal], proposalId), proposalId)
  }

  private def nextId: String = UUID.randomUUID.toString
}

class Proposal(proposalId: String) extends PersistentActor {
  override def persistenceId: String = proposalId

  var state = ProposalState.nothing

  override def receiveCommand: Receive = {
    case command: SubmitProposal =>
      persist(ProposalSubmitted(proposalId, command.description, command.price, command.expectedCompletion)) { event =>
        state = realized(event)
        sender ! SubmitProposalResult(proposalId, event.description, event.price, event.expectedCompletion)
      }
    case command: RejectPricing =>
      persist(ProposalPricingRejected(proposalId, command.suggestedPrice)) { event =>
        state = realized(event)
        sender ! RejectPricingResult(proposalId, event.suggestedPrice)
      }
  }

  override def receiveRecover: Receive = {
    case event: ProposalSubmitted =>
      state = realized(event)
  }

  private def realized(event: ProposalSubmitted): ProposalState = {
    ProposalState.submittedAs(event.description, event.price, event.expectedCompletion)
  }

  private def realized(event: ProposalPricingRejected): ProposalState = {
    state.rejectedBecause(event.suggestedPrice)
  }
}

object ProposalState {
  def nothing = ProposalState("", 0, new Date())

  def apply(description: String, price: Long, expectedCompletion: Date): ProposalState =
    new ProposalState(description, price, expectedCompletion)

  def resubmittedWith(description: String, price: Long, expectedCompletion: Date): ProposalState =
    ProposalState(description, price, expectedCompletion)

  def submittedAs(description: String, price: Long, expectedCompletion: Date): ProposalState =
    ProposalState(description, price, expectedCompletion)
}

case class ProposalState(description: String, price: Long, expectedCompletion: Date, suggestedPrice: Long) {
  def this(description: String, price: Long, expectedCompletion: Date) =
    this(description, price, expectedCompletion, 0)

  def rejectedBecause(suggestedPrice: Long) = {
    ProposalState(this.description, this.price, this.expectedCompletion, suggestedPrice)
  }
}
