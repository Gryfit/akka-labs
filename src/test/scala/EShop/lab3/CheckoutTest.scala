package EShop.lab3

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab2.Checkout
import EShop.lab2.Checkout._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val cart     = TestProbe()
    val checkout = TestActorRef(new Checkout(cart.ref))
    checkout ! StartCheckout
    checkout ! SelectDeliveryMethod("test1")
    checkout ! SelectPayment("test1")
    checkout ! ReceivePayment
    checkout ! ReceivePayment
    cart.expectMsg(CloseCheckout)
  }

}
