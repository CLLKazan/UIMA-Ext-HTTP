/**
 *
 */
package ru.kfu.itis.issst.uima.http

import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import spray.can.Http
import ru.kfu.itis.cll.uima.util.Slf4jLoggerImpl
import ru.kfu.itis.issst.uima.http.nlp.NLPFacade

/**
 * @author Rinat Gareev (Kazan Federal University)
 *
 */
object Boot {
  Slf4jLoggerImpl.forceUsingThisImplementation()

  def main(args: Array[String]) {
    implicit val appActSystem = ActorSystem("uima-ext")
    val nlpFacade = appActSystem.actorOf(Props[NLPFacade], "nlp-facade")
    val router = appActSystem.actorOf(Props(classOf[Router], nlpFacade), "main-http-router")

    IO(Http) ! Http.Bind(router, interface = "localhost", port = 8080)
  }
}