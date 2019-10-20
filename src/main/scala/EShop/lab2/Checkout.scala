package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                                             extends Data
  case class Methods(delivery: Option[String], payment: Option[String]) extends Data
  case class SelectingDeliveryStarted(timer: Cancellable)               extends Data
  case class ProcessingPaymentStarted(timer: Cancellable)               extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor {

  private implicit val ec = context.system.dispatcher
  private val log         = Logging(context.system, this)

  private def scheduleCheckoutTimer = context.system.scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def schedulePaymentTimer  = context.system.scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  var paymentMethod: String  = ""
  var deliveryMethod: String = ""

  def receive: Receive = LoggingReceive {
    case StartCheckout => context.become(selectingDelivery(scheduleCheckoutTimer))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      // assign method somewhere?
      deliveryMethod = method
      timer.cancel()
      context.become(selectingPaymentMethod(scheduleCheckoutTimer))
    case ExpireCheckout | CancelCheckout =>
      paymentMethod = ""
      deliveryMethod = ""
      timer.cancel()
      context.become(cancelled)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(method) =>
      // assign method somewhere?
      paymentMethod = method
      timer.cancel()
      context.become(processingPayment(schedulePaymentTimer))
    case CancelCheckout | ExpireCheckout =>
      paymentMethod = ""
      deliveryMethod = ""
      timer.cancel()
      context.become(cancelled)
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpirePayment =>
      paymentMethod = ""
      deliveryMethod = ""
      timer.cancel()
      context.become(cancelled)
    case ReceivePayment =>
      timer.cancel()
      context.become(closed)
  }

  def cancelled: Receive = LoggingReceive {
    case _ => context.stop(self)
  }

  def closed: Receive = LoggingReceive {
    case _ => context.stop(self)
  }

}
