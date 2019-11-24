package EShop.lab3

import EShop.lab2.{Cart, CartActor, CartFSM}
import EShop.lab2.CartActor._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._

class CartFSMTest
  extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  implicit val timeout: Timeout = 1.second

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val cart = TestActorRef(new CartFSM())
    cart ! AddItem("TEST1")
    (cart ? GetItems).mapTo[Seq[Any]].futureValue shouldBe Seq("TEST1")
  }

  it should "be empty after adding and removing the same item" in {
    val cart = TestActorRef(new CartFSM())
    cart ! AddItem("TEST1")
    cart ! RemoveItem("TEST1")
    import EShop.lab3.OrderManager.Empty
    expectMsg(Empty)
  }

  it should "start checkout" in {
    val cart = TestActorRef(new CartFSM())
    cart ! AddItem("Test1")
    val c = (cart ? GetCart).mapTo[Cart].futureValue
    (cart ? StartCheckout).futureValue shouldBe CartActor.CheckoutStarted(cart.children.head, c)
  }
}
