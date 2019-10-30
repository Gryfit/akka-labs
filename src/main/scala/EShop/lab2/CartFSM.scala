package EShop.lab2

import EShop.lab2.CartActor._
import EShop.lab2.CartFSM.Status
import akka.actor.{LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }

  def props() = Props(new CartFSM())
}

class CartFSM extends LoggingFSM[Status.Value, Cart] {
  import EShop.lab2.CartFSM.Status._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val cartTimerDuration: FiniteDuration = 1 seconds

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), _) =>
      goto(NonEmpty) using Cart(Seq(item))
  }

  when(NonEmpty, stateTimeout = cartTimerDuration) {
    case Event(AddItem(item), c: Cart) =>
      stay using c.addItem(item)
    case Event(RemoveItem(item), cart: Cart) if cart.size == 1 && cart.contains(item) => {
      import EShop.lab3.OrderManager.Empty
      context.sender() ! Empty
    }
    goto(Empty) using Cart.empty
  case Event(RemoveItem(item), cart: Cart) =>
      stay using cart.removeItem(item)
    case Event(StateTimeout, _) => {
      import EShop.lab3.OrderManager.Empty
      context.sender() ! Empty
    }
    goto(Empty) using Cart.empty
  case Event(StartCheckout, cart: Cart) =>
      val checkout = context.actorOf(CheckoutFSM.props(context.self), "checkout")
      import EShop.lab2.Checkout.StartCheckout
      checkout ! StartCheckout
      context.sender() ! CartActor.CheckoutStarted(checkout, cart)
      goto(InCheckout) using cart
    case Event(GetItems, cart: Cart) =>
      sender() ! cart.items
      stay()
    case Event(GetCart, cart: Cart) =>
      sender() ! cart
      stay()
  }

  when(InCheckout) {
    case Event(CancelCheckout, cart: Cart) =>
      goto(NonEmpty) using cart
    case Event(CloseCheckout, _) =>
      goto(Empty) using Cart.empty
  }

}
