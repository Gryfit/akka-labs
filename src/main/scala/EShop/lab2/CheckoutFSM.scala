package EShop.lab2

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab2.Checkout._
import EShop.lab2.CheckoutFSM.Status
import EShop.lab3.Payment
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM(cartActor))
}

class CheckoutFSM(cartActor: ActorRef) extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.CheckoutFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private val scheduler = context.system.scheduler

  startWith(NotStarted, Uninitialized)

  when(NotStarted) {
    case Event(StartCheckout, _) =>
      goto(SelectingDelivery) using Uninitialized
  }

  when(SelectingDelivery, stateTimeout = checkoutTimerDuration) {
    case Event(SelectDeliveryMethod(method), Uninitialized) =>
      goto(SelectingPaymentMethod) using Methods(delivery = Some(method), payment = None)
    case Event(StateTimeout | CancelCheckout, _) =>
      goto(Cancelled)
  }

  when(SelectingPaymentMethod, stateTimeout = checkoutTimerDuration) {
    case Event(SelectPayment(method), methods: Methods) =>
      val paymentActor = context.actorOf(Payment.props(method, sender(), context.self))
      sender() ! PaymentStarted(paymentActor)
      goto(ProcessingPayment) using Methods(delivery = Some(method), payment = methods.payment.orElse(None))
    case Event(CancelCheckout | StateTimeout, _) =>
      goto(Cancelled)
  }

  when(ProcessingPayment, stateTimeout = paymentTimerDuration) {
    case Event(ReceivePayment, _) =>
      goto(Closed)
    case Event(CancelCheckout | StateTimeout, _) =>
      goto(Cancelled)
  }

  when(Cancelled) {
    case Event(_, _) =>
      goto(Cancelled)
  }

  when(Closed) {
    case Event(_, _) =>
      goto(Closed)
  }

}
