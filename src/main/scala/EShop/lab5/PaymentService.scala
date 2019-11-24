package EShop.lab5

import EShop.lab5.PaymentService.{PaymentClientError, PaymentServerError, PaymentSucceeded}
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.pattern.PipeToSupport

object PaymentService {

  case object PaymentSucceeded // http status 200
  class PaymentClientError extends Exception // http statuses 400, 404
  class PaymentServerError extends Exception // http statuses 500, 408, 418

  def props(method: String, payment: ActorRef) = Props(new PaymentService(method, payment))

}

class PaymentService(method: String, payment: ActorRef) extends Actor with ActorLogging with PipeToSupport {
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)
  private val URI  = getURI

  override def preStart(): Unit = http.singleRequest(HttpRequest(uri = URI)).pipeTo(self)

  override def receive: Receive = LoggingReceive {
    receiveSuccess orElse
      receiveClientError orElse
      receiveSeverError orElse { case Status.Failure(exception) => throw exception }
  }

  private def receiveSuccess: Receive = {
    case HttpResponse(StatusCodes.OK, _, _, _) => payment ! PaymentSucceeded
  }

  private def receiveSeverError: Receive = {
    case HttpResponse(StatusCodes.InternalServerError | StatusCodes.RequestTimeout | StatusCodes.ImATeapot, _, _, _) =>
      throw new PaymentServerError
  }
  private def receiveClientError: Receive = {
    case HttpResponse(StatusCodes.BadRequest | StatusCodes.NotFound, _, _, _) =>
      throw new PaymentClientError
  }

  private def getURI: String = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }

}
