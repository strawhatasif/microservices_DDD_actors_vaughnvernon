package donebyme.matching.controllers

import java.util.Date

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import javax.inject._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.duration._
import donebyme.matching.models._
import donebyme.matching.views.{ProposalViewResult, ProposalsViews, QueryProposalView}
import donebyme.pricing.models.{Pricing,VerifyPrice}

@Singleton
class ProposalsController @Inject() (system: ActorSystem) extends Controller {

  implicit val timeout = Timeout(5.seconds)

  // curl -D -H 'Content-Type: application/json' -X POST -d '{"description":"Test...","price":19999,"expectedCompletion":0}' http://localhost:9003/matching/proposals

  def submit = Action.async(parse.tolerantText) { request =>
    val body = request.body
    val data: ProposalData = ProposalData.from(body)

    val proposal = Proposal.toSubmit(system)

    val future = proposal ? SubmitProposal(data.description, data.price, data.expectedCompletionAsDate)

    future.mapTo[SubmitProposalResult].map { result =>
      val proposalData = IdentifiedProposalData(result.proposalId, result.description, result.price, result.expectedCompletion.getTime)
      val proposalId = result.proposalId
      val description = result.description
      val priceToSubmit = result.price
      Created(Json.toJson(proposalData)).withHeaders(LOCATION -> proposalLocation(result.proposalId))
    }

    val price = Pricing.toSubmit(system)

    val pricingFuture = price ? VerifyPrice(proposalId, description, priceToSubmit)

    //pricingFuture.mapTo
  }

  //  curl -X GET http://localhost:9002/matching/proposals/:proposalId
  
  def proposal(id: String) = Action.async { request =>

    val future = ProposalsViews() ? QueryProposalView(id)

    future.mapTo[ProposalViewResult].map {
      case ProposalViewResult(proposalId, Some(view)) =>
        val proposalData = IdentifiedProposalData(proposalId, view.description, view.price, view.expectedCompletion.getTime)
        Ok(Json.toJson(proposalData))
      case _ =>
        NotFound(s"No such proposal: $id")
    }
  }

  private def proposalLocation(proposalId: String): String = s"/matching/proposals/${proposalId}"
}

object ProposalData {
  def from(body: String): _root_.donebyme.matching.controllers.ProposalData = {
    Json.parse(body).as[ProposalData]
  }

  implicit val proposalDataWrites: Writes[ProposalData] = (
      (JsPath \ "description").write[String] and
      (JsPath \ "price").write[Long] and
      (JsPath \ "expectedCompletion").write[Long]
      )(unlift(ProposalData.unapply))

  implicit val productDataReads: Reads[ProposalData] = (
      (JsPath \ "description").read[String] and
      (JsPath \ "price").read[Long] and
      (JsPath \ "expectedCompletion").read[Long]
      )(ProposalData.apply _)
}

case class ProposalData(description: String, price: Long, expectedCompletion: Long) {
  def expectedCompletionAsDate = new Date(expectedCompletion)
}

object IdentifiedProposalData {

  implicit val proposalDataWrites: Writes[IdentifiedProposalData] = (
      (JsPath \ "proposalId").write[String] and
      (JsPath \ "description").write[String] and
      (JsPath \ "price").write[Long] and
      (JsPath \ "expectedCompletion").write[Long]
      )(unlift(IdentifiedProposalData.unapply))

  implicit val productDataReads: Reads[IdentifiedProposalData] = (
      (JsPath \ "proposalId").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "price").read[Long] and
      (JsPath \ "expectedCompletion").read[Long]
      )(IdentifiedProposalData.apply _)
}

case class IdentifiedProposalData(proposalId: String, description: String, price: Long, expectedCompletion: Long)
