package EShop.lab3

import EShop.lab2.{CartActor, CartFSM, Checkout}
import EShop.lab2.CartActor.{CancelCheckout, GetCart}
import EShop.lab3.OrderManager._
import akka.actor.{FSM, LoggingFSM, Props}

class OrderManagerFSM extends LoggingFSM[State, Data] {

  startWith(Uninitialized, Empty)

  when(Uninitialized) {
    case Event(AddItem(id: String), _) =>
      val cartRef = context.actorOf(Props[CartFSM], "cart")
      import EShop.lab2.CartActor.AddItem
      cartRef ! AddItem(id)
      sender() ! Done
      goto(Open) using CartData(cartRef)
  }

  when(Open) {
    case Event(Empty, _) =>
      goto(Uninitialized) using Empty
    case Event(RemoveItem(id: String), CartData(cartRef)) =>
      cartRef ! RemoveItem(id)
      sender() ! Done
      stay()
    case Event(AddItem(id: String), CartData(cartRef)) =>
      cartRef ! AddItem(id)
      sender() ! Done
      stay()
    case Event(Buy, CartData(cartRef)) =>
      import EShop.lab2.CartActor.StartCheckout
      cartRef ! StartCheckout
      goto(InCheckout) using CartDataWithSender(cartRef, sender())
  }

  when(InCheckout) {
    case Event(CartActor.CheckoutStarted(checkoutRef, cart), CartDataWithSender(cartRef, senderRef)) =>
      senderRef ! Done
      goto(InCheckout) using InCheckoutData(checkoutRef)
    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), InCheckoutData(checkoutRef)) =>
      checkoutRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutRef ! Checkout.SelectPayment(payment)
      goto(InPayment) using InPaymentData(sender())
    case Event(CancelCheckout, _) =>
      goto(Open) using CartData(context.child("cart").get)
  }

  when(InPayment) {
    case Event(Checkout.PaymentStarted(paymentRef), InPaymentData(senderRef)) =>
      senderRef ! Done
      goto(InPayment) using InPaymentData(paymentRef)
    case Event(Pay, InPaymentData(paymentRef)) =>
      import EShop.lab3.Payment.DoPayment
      paymentRef ! DoPayment
      goto(InPayment) using InPaymentDataWithSender(paymentRef, sender())
    case Event(Payment.PaymentConfirmed, InPaymentDataWithSender(paymentRef, sender)) =>
      sender ! Done
      goto(Finished)
  }

  when(Finished) {
    case _ =>
      sender ! "order manager finished job"
      stay()
  }

}
