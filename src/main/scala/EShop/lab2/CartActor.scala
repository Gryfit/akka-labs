package EShop.lab2

import EShop.lab2.CartActor.{AddItem, CancelCheckout, CloseCheckout, ExpireCart, RemoveItem, StartCheckout}
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command
  case object GetItems             extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props() = Props(new CartActor())
}

class CartActor extends Actor {

  implicit val ec: ExecutionContext = context.dispatcher

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      val cart = Cart(Seq(item))
      context.become(nonEmpty(cart, scheduleTimer))
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      val newCart = cart.addItem(item)
      timer.cancel()
      context.become(nonEmpty(newCart, scheduleTimer))
    case RemoveItem(item) if cart.size == 1 && cart.contains(item) =>
      timer.cancel()
      context.unbecome()
    case RemoveItem(item) if cart.size > 1 =>
      val newCart = cart.removeItem(item)
      timer.cancel()
      context.become(nonEmpty(newCart, scheduleTimer))
    case ExpireCart =>
      timer.cancel()
      context.unbecome()
    case StartCheckout =>
      timer.cancel()
      context.become(inCheckout(cart))
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      context.become(nonEmpty(cart, scheduleTimer))
    case CloseCheckout =>
      context.become(empty)
  }

}
