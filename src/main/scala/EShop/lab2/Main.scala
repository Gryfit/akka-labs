package EShop.lab2

import akka.actor.{ActorSystem, Props}

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("MainSystem")
    testCart(system)
    testCheckout(system)
  }

  def testCart(system: ActorSystem): Unit = {
    import EShop.lab2.CartActor._
    val cart = system.actorOf(Props[CartActor], name = "cart")
    cart ! AddItem("XD")
    cart ! RemoveItem("XD")
    cart ! AddItem("XD2")
    cart ! StartCheckout
    cart ! CloseCheckout
  }

  def testCheckout(system: ActorSystem): Unit = {
    import EShop.lab2.Checkout._
    val checkout = system.actorOf(Props[Checkout], name = "checkout")
    checkout ! StartCheckout
    checkout ! SelectDeliveryMethod("Car")
    checkout ! SelectPayment("Card")
    checkout ! CancelCheckout
  }

}
