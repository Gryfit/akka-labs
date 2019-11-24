package EShop.lab5

import EShop.lab5.API.Query
import EShop.lab5.ProductCatalog.{GetItems, Items}
import EShop.lab5.ProductCatalogApp.config
import akka.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object API {
  case class Query(brand: String, keyWords: List[String])
  case class Item(id: String, name: String, brand: String, price: BigDecimal, count: Int)
  case class Items(items: List[Item])

}
trait APIFormatter extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val queryFormat = jsonFormat2(API.Query)
  implicit val itemFormat  = jsonFormat5(API.Item)
  implicit val itemsFormat = jsonFormat1(API.Items)
}

object ProductCatalogAPI extends App {
  val server = new APIServer
  server.startServer("localhost", 9001)
}

class APIServer extends HttpApp with APIFormatter {
  private val config = ConfigFactory.load()
  val APIActorSystem = ActorSystem("api", config.getConfig("api").withFallback(config))

  val productCatalog: ActorSelection =
    APIActorSystem.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog")
  val APIHandler: ActorRef              = APIActorSystem.actorOf(Props(new APIHandler(productCatalog)), "handler")
  private implicit val timeout: Timeout = Timeout(10 seconds)
  override protected def routes: Route = {
    path("find-item") {
      post {
        entity(as[API.Query]) { query =>
          complete((APIHandler ? query).mapTo[API.Items])
        }
      }
    }
  }
}
class APIHandler(productCatalog: ActorSelection) extends Actor {
  private implicit val timeout: Timeout     = Timeout(5 seconds)
  private implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case q: API.Query =>
      val http = sender()
      for {
        value <- (productCatalog ? GetItems(q.brand, q.keyWords)).mapTo[Items]
        response = Items.toAPI(value)
        _        = println(response)
      } yield http ! response
  }
}
