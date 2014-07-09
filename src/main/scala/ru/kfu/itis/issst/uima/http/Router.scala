/**
 *
 */
package ru.kfu.itis.issst.uima.http

import spray.routing.HttpServiceActor
import spray.http.MultipartFormData
import akka.pattern.ask
import akka.actor.ActorRef
import scala.concurrent.duration._
import akka.util.Timeout
import spray.http.StatusCodes
import ru.kfu.itis.issst.uima.http.nlp.NLPFacade

import nlp.NLPFacade._

/**
 * @author Rinat Gareev (Kazan Federal University)
 *
 */
class Router(private val nlpFacade: ActorRef) extends HttpServiceActor {

  private val nlpFacadeTimeoutSec = 30
  implicit val nlpFacadeTimeout = Timeout(nlpFacadeTimeoutSec seconds)
  import context.dispatcher

  private val route = path("pos-tagger") {
    post {
      formField("text".?) { textOpt =>
        textOpt match {
          case Some(text) => onSuccess((nlpFacade ? PerformPosTagging(text)).mapTo[String]) { taggedText =>
            complete(taggedText)
          }
          case None => complete(StatusCodes.BadRequest)
        }

      }
    }
  }
  override def receive = runRoute(route)
}