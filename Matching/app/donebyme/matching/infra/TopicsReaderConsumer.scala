package donebyme.matching.infra

import akka.actor._
import donebyme.common.infra._
import donebyme.common.tools._
import donebyme.matching.models._

class TopicsReaderConsumer extends Actor {
  def receive = {
    case consume: TopicReaderConsumerMessage =>
      val reader = new ObjectReader(consume.message)
      val messageType = reader.stringValue("messageType")
      
      messageType match {
        case "donebyme.pricing.models.PricingRejected" =>
          if (reader.stringValue("message.category") == "") {
            println("################ PRICING REJECTED")
            println("################ " + consume.message)
            
            val proposalId = reader.stringValue("message.proposalId")
            val suggestedPrice = reader.longValue("message.suggestedPrice")

            context.system.actorSelection(s"/user/${proposalId}").tell(RejectPricing(proposalId, suggestedPrice), self)
          }
          
        case _ =>
          // not consumed here
      }
  }
}
