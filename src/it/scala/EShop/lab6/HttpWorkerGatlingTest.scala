package EShop.lab6

import io.gatling.core.Predef.{Simulation, StringBody, jsonFile, rampUsers, scenario, _}
import io.gatling.http.Predef.http

import scala.concurrent.duration._
import scala.util.Random

class HttpWorkerGatlingTest extends Simulation {
  def random = Random.nextInt(5)

  val httpProtocol = http  //values here are adjusted to cluster_demo.sh script
    .baseUrls("http://localhost:9001")// "http://localhost:9002", "http://localhost:9003"
    .acceptHeader("text/plain,text/html,application/json,application/xml;")
    .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")

  val request = scenario("BasicSimulation")
    .feed(jsonFile(classOf[HttpWorkerGatlingTest].getResource("/data/work_data.json").getPath).random)
    .exec(
      http("work_basic")
        .post("/find-item")
        .body(StringBody("""{ "brand": "${brand}", "keyWords": [] }"""))
        .asJson
    )

  val scn = scenario("ProductCatalogHttpTest")
    .exec(request)
    .pause(random)
    .exec(request)
    .pause(random)
    .exec(request)
    .pause(random)
    .exec(request)
    .pause(random)
    .exec(request)

  setUp(
    scn.inject(rampUsers(15000).during(1.minutes))
  ).protocols(httpProtocol)
}
