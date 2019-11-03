package EShop.lab4

import EShop.lab2.{Cart, CartActor, Checkout}
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor
import cats.syntax.option._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(val persistenceId: String) extends PersistentActor {

  import EShop.lab2.CartActor._

  implicit val ec: ExecutionContext = context.dispatcher

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  private def scheduleTimer = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    timer.foreach(_.cancel())
    event match {
      case CartExpired | CheckoutClosed => context.become(empty)
      case CheckoutCancelled(cart)      => context.become(nonEmpty(cart, scheduleTimer))
      case ItemAdded(item, cart)        => context.become(nonEmpty(cart.addItem(item), scheduleTimer))
      case CartEmptied                  => context.unbecome()
      case ItemRemoved(item, cart) =>
        val newCart = cart.removeItem(item)
        if (cart.contains(item)) {
          if (newCart.size == 0) {
            context.become(empty)
          } else {
            context.become(nonEmpty(newCart, scheduleTimer))
          }
        }
      case CheckoutStarted(_, cart) => context.become(inCheckout(cart))
    }
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty)) { updateState(_, None) }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, cart)) { updateState(_, timer.some) }
    case RemoveItem(item) =>
      persist(ItemRemoved(item, cart)) { updateState(_, timer.some) }
    case ExpireCart =>
      persist(CartEmptied) { updateState(_, timer.some) }
    case StartCheckout =>
      val checkout = context.actorOf(Checkout.props(context.self), "checkout")
      persist(CheckoutStarted(checkout, cart)) {
        import EShop.lab2.Checkout.StartCheckout
        checkout ! StartCheckout
        context.sender() ! CartActor.CheckoutStarted(checkout, cart)
        updateState(_, timer.some)
      }
    case GetItems => sender() ! cart.items
    case GetCart  => sender() ! cart
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      persist(CheckoutCancelled(cart)) { updateState(_, None) }
    case CloseCheckout =>
      persist(CheckoutClosed) { updateState(_, None) }
    case GetItems => sender() ! cart.items
  }

  override def receiveRecover: Receive = LoggingReceive {
    case event: Event => updateState(event, None)
  }

}
