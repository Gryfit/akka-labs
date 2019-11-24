package EShop.lab5
import java.net.InetAddress

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import ProductCatalog.GetItems

import scala.concurrent.duration._

class ProductCatalogRemoteTest extends AsyncFlatSpec with Matchers {

  implicit val timeout: Timeout = 3.second

  "A remote Product Catalog" should "return search results" in {
    val config = ConfigFactory.load()
    val query  = GetItems("gerber", List("cream"))

    val actorSystem = ActorSystem("ProductCatalog", config.getConfig("productcatalog").withFallback(config))
    val actor       = actorSystem.actorOf(ProductCatalog.props(new SearchService()), "productcatalog")
    val remoteAddr  = RemoteAddressExtension(actorSystem).address
    val remotePath  = actor.path.toStringWithAddress(remoteAddr)

    println("ADDR:\t " + remoteAddr + "\t remotePath:\t" + remotePath)



    val anotherActorSystem = ActorSystem("another")
    val productCatalog = anotherActorSystem.actorSelection(remotePath)

    for {
      productCatalogActorRef <- productCatalog.resolveOne()
      items                  <- (productCatalogActorRef ? query).mapTo[ProductCatalog.Items]
    } yield {
      assert(items.items.size == 10)
    }
  }

  class RemoteAddressExtensionImpl(system: ExtendedActorSystem) extends Extension {
    def address = system.provider.getDefaultAddress
  }

  object RemoteAddressExtension extends ExtensionId[RemoteAddressExtensionImpl] with ExtensionIdProvider {
    override def lookup                                               = RemoteAddressExtension
    override def createExtension(system: ExtendedActorSystem)         = new RemoteAddressExtensionImpl(system)
    override def get(system: ActorSystem): RemoteAddressExtensionImpl = super.get(system)
  }

}
