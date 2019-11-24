package EShop.lab3

import EShop.lab2.Checkout.ReceivePayment
import EShop.lab3.Payment._
import akka.actor.{ActorRef, FSM, LoggingFSM, PoisonPill, Props}

object PaymentFSM {
  def props(method: String, orderManager: ActorRef, checkout: ActorRef) =
    Props(new PaymentFSM(method, orderManager, checkout))

}

class PaymentFSM(
  method: String,
  orderManager: ActorRef,
  checkout: ActorRef
) extends LoggingFSM[State, Data] {

  startWith(WaitingForPayment, Empty)

  when(WaitingForPayment) {
    case Event(DoPayment, _) =>
      println("PAYMENT DONE")
      checkout ! ReceivePayment
      sender() ! PaymentConfirmed
      self ! PoisonPill
      goto(WaitingForPayment)
  }

}
