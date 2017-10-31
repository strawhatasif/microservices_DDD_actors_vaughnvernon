package donebyme.pricing.infra

import akka.actor._
import donebyme.common.infra._
import donebyme.common.tools._
import donebyme.pricing.models._

class TopicsReaderConsumer extends Actor {
  def receive = {
    case consume: TopicReaderConsumerMessage =>
      val reader = new ObjectReader(consume.message)
      val messageType = reader.stringValue("messageType")
      
      messageType match {
        case "donebyme.pricing.models.ProposalSubmitted" =>
          if (reader.stringValue("message.category") == "") {
            println("################ PROPOSAL SUBMITTED")
            println("################ " + consume.message)
            
            val proposalId = reader.stringValue("message.proposalId")
            val description = reader.stringValue("message.description")
            val price = reader.longValue("message.price")
            val expectedCompletion = reader.dateValue("message.expectedCompletion")

            context.system.actorSelection(s"/user/${proposalId}").tell(VerifyPrice(proposalId, description, price), self)
          }
          
        case _ =>
          // not consumed here
      }
  }
}
