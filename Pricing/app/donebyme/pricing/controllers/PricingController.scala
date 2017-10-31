package donebyme.pricing.controllers

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
import donebyme.pricing.models.{Pricing,VerifyPrice,VerifyPriceResult,RejectPricingResult}
import donebyme.pricing.views.{PricingViews,PricingViewResult,RejectedPricingViewResult,QueryPricingView,QueryRejectedPricingView}

@Singleton
class PricingController @Inject()(system: ActorSystem) extends Controller {

  implicit val timeout = Timeout(5.seconds)

  // curl -D -H 'Content-Type: application/json' -X POST -d '{"description":"Test...","price":19999,"expectedCompletion":0}' http://localhost:9004/pricing/price

  def submit = Action.async(parse.tolerantText) { request =>
    val body = request.body
    val data: PricingData = PricingData.from(body)

    val price = Pricing.toSubmit(system)

    val future = price ? VerifyPrice(data.proposalId, data.description, data.price)

    future.mapTo[VerifyPriceResult].map { result =>
      val priceData = PricingData(result.proposalId, result.description, result.price)
      Created(Json.toJson(priceData)).withHeaders(LOCATION -> proposalLocation(result.proposalId))
    }
  }

  //  curl -X GET http://localhost:9004/pricing/price/:priceId

  def price(id: String) = Action.async { request =>

    val future = PricingViews() ? QueryPricingView(id)

    future.mapTo[PricingViewResult].map {
      case PricingViewResult(proposalId, Some(view)) =>
        val proposalData = PricingData(proposalId, view.description, view.price)
        Ok(Json.toJson(proposalData))

      case _ =>
        NotFound(s"No such price: $id")
    }
  }

  def rejectedPrice(id: String) = Action.async { request =>

    val future = PricingViews() ? QueryRejectedPricingView(id)

    future.mapTo[RejectedPricingViewResult].map {
      case RejectedPricingViewResult(proposalId, Some(view)) =>
        val proposalData = IdentifiedPricingData(proposalId, view.description, view.suggestedPrice)
        Ok(Json.toJson(proposalData))

      case _ =>
        NotFound(s"No such price: $id")
    }
  }

  private def proposalLocation(proposalId: String): String = s"/pricing/price/${proposalId}"
}

object PricingData {
  def from(body: String): _root_.donebyme.pricing.controllers.PricingData = {
    Json.parse(body).as[PricingData]
  }

  implicit val pricingDataWrites: Writes[PricingData] = (
    (JsPath \ "proposalId").write[String] and
      (JsPath \ "description").write[String] and
      (JsPath \ "price").write[Long]
    )(unlift(PricingData.unapply))

  implicit val pricingDataReads: Reads[PricingData] = (
    (JsPath \ "proposalId").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "price").read[Long]
    )(PricingData.apply _)
}

case class PricingData(proposalId: String, description: String, price: Long)

object IdentifiedPricingData {

  implicit val pricingDataWrites: Writes[IdentifiedPricingData] = (
    (JsPath \ "proposalId").write[String] and
      (JsPath \ "description").write[String] and
      (JsPath \ "suggestedPrice").write[Long]
    )(unlift(IdentifiedPricingData.unapply))

  implicit val pricingDataReads: Reads[IdentifiedPricingData] = (
    (JsPath \ "proposalId").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "suggestedPrice").read[Long]
    )(IdentifiedPricingData.apply _)
}

case class IdentifiedPricingData(proposalId: String, description: String, suggestedPrice: Long)
