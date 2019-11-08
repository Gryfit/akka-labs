package EShop.lab4

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab3.Payment
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor
import cats.syntax.option._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 2.seconds

  private def scheduleTimer = scheduler.scheduleOnce(timerDuration, self, Expire)

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    maybeTimer.foreach(_.cancel())
    event match {
      case CheckoutStarted           => context.become(selectingDelivery(scheduleTimer))
      case DeliveryMethodSelected(_) => context.become(selectingPaymentMethod(scheduleTimer))
      case CheckOutClosed            => context.become(closed)
      case CheckoutCancelled         => context.become(cancelled)
      case PaymentStarted(_)         => context.become(processingPayment(scheduleTimer))
    }
  }

  def receiveCommand: Receive = LoggingReceive {
    case StartCheckout => persist(CheckoutStarted) { updateState(_, scheduleTimer.some) }
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case SelectDeliveryMethod(method) =>
      persist(DeliveryMethodSelected(method)) { updateState(_, timer.some) }
    case Expire | CancelCheckout =>
      persist(CheckoutCancelled) { updateState(_, None) }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case SelectPayment(method) =>
      val paymentActor = context.actorOf(Payment.props(method, sender(), context.self))
      persist(PaymentStarted(paymentActor)) {
        sender() ! PaymentStarted(paymentActor)
        updateState(_, timer.some)
      }
    case CancelCheckout | Expire =>
      persist(CheckoutCancelled) { updateState(_, timer.some) }
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | Expire =>
      persist(CheckoutCancelled) { updateState(_, timer.some) }
    case ReceivePayment =>
      persist(CheckOutClosed) {
        cartActor ! CloseCheckout
        updateState(_, timer.some)
      }
  }

  def cancelled: Receive = LoggingReceive {
    case _ =>
      context.stop(self)
  }

  def closed: Receive = LoggingReceive {
    case _ =>
      context.stop(self)
  }
  override def receiveRecover: Receive = {
    case e: Event => updateState(e)
  }
}
