package org.virtuslab.ideprobe.jsonrpc

import org.virtuslab.ideprobe.Close
import org.virtuslab.ideprobe.jsonrpc.JsonRpc._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.ClassTag

trait JsonRpcEndpoint extends AutoCloseable {
  protected def connection: JsonRpcConnection

  implicit protected def ec: ExecutionContext

  protected def handler: Handler

  protected def sendRequest[A: ClassTag, B: ClassTag](method: Method[A, B], parameters: A): Future[B] = {
    Future(method.encode(parameters)).flatMap { json =>
      method match {
        case Method.Notification(name) =>
          println(s"notification: ${name}")
          println(s"value: ${json}")
          connection.sendNotification(name, json)
          Future.unit.asInstanceOf[Future[B]]
        case Method.Request(name) =>
          println(s"request[$name]: $json")
          connection
            .sendRequest(name, json)
            .flatMap {
              case JsonRpc.Failure(error) =>
                val exception = {
                  val cause = new Exception(error.message)
                  val stackTrace = JsonRpc.gson.fromJson(error.data, classOf[Array[StackTraceElement]])
                  cause.setStackTrace(stackTrace)
                  new RemoteException(cause)
                }

                println(s"response: ${error.message}")
                Future.failed(exception)
              case response =>
                println(s"response: ${response.result}")
                Future(method.decode(response.result))
            }
      }
    }
  }

  lazy val listen: Unit =
    connection.onRequest { request =>
      handler(request.method, request.params)
        .map(connection.sendResponse(request, _))
        .recover(connection.sendError(request, _))
    }

  def close(): Unit = {
    Close(connection)
  }
}
