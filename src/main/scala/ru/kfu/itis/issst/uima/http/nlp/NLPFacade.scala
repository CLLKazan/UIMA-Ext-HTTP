/**
 *
 */
package ru.kfu.itis.issst.uima.http.nlp

import akka.actor.Actor
import akka.actor.ActorLogging
import ru.kfu.itis.issst.uima.tokenizer.TokenizerAPI
import ru.kfu.itis.issst.uima.segmentation.SentenceSplitterAPI
import org.apache.uima.resource.metadata.impl.Import_impl
import ru.kfu.itis.cll.uima.util.PipelineDescriptorUtils
import scala.collection.JavaConversions._
import org.apache.uima.UIMAFramework
import org.apache.uima.analysis_engine.AnalysisEngine
import akka.actor.Status
import org.apache.uima.jcas.JCas
import ru.kfu.cll.uima.segmentation.fstype.Sentence
import org.uimafit.util.JCasUtil
import org.opencorpora.cas.Word
import ru.kfu.cll.uima.tokenizer.fstype.Token
import ru.ksu.niimm.cll.uima.morph.opencorpora.MorphCasUtils
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import org.opencorpora.cas.Word
import ru.kfu.cll.uima.segmentation.fstype.Sentence
import ru.kfu.cll.uima.tokenizer.fstype.Token

/**
 * @author Rinat Gareev (Kazan Federal University)
 *
 */
class NLPFacade extends Actor with ActorLogging {
  import NLPFacade._
  val nlpWorker: ActorRef = context.actorOf(Props[NLPWorker])
  val maxQueueSize = 10
  val taskQueue = scala.collection.mutable.Queue.empty[(ActorRef, NLPTask)]

  override def receive = {
    case msg: NLPTask =>
      if (taskQueue.size >= maxQueueSize) {
        // TODO
        sender ! Status.Failure(new IllegalStateException("Task queue is full"))
      }
      taskQueue enqueue ((sender, msg))
      nlpWorker ! WorkAvailable
    case GimmeWork => if (!taskQueue.isEmpty) {
      val (client, task) = taskQueue.dequeue
      sender ! DelegatedNLPTask(client, task)
    }
  }
}

class NLPWorker extends Actor with ActorLogging {

  import NLPFacade._

  private var engine: AnalysisEngine = null
  private var cas: JCas = null

  override def preStart() {
    val analyzers = Map(
      "tokenizer" -> TokenizerAPI.getAEImport(),
      "sentenceSplitter" -> SentenceSplitterAPI.getAEImport(),
      "pos-tagger" -> {
        val posTaggerImport = new Import_impl()
        posTaggerImport.setName("pos_tagger")
        posTaggerImport
      })
    val aggregateDesc = PipelineDescriptorUtils.createAggregateDescription(analyzers)
    engine = UIMAFramework.produceAnalysisEngine(aggregateDesc)
    cas = engine.newJCas()
  }

  override def postStop() {
    cas.release()
    cas = null
    engine.destroy()
    engine = null
  }

  override def receive = {
    case WorkAvailable => sender ! GimmeWork
    case DelegatedNLPTask(client, PerformPosTagging(text)) => {
      try {
        cas.setDocumentText(text)
        engine.process(cas)
        client ! toPlainText(cas)
      } catch {
        case ex: Exception => client ! Status.Failure(ex)
      } finally {
        cas.reset()
      }
      sender ! GimmeWork
    }
  }
}

object NLPFacade {
  trait NLPTask
  case class PerformPosTagging(text: String) extends NLPTask
  private[nlp] case object WorkAvailable
  private[nlp] case object GimmeWork
  private[nlp] case class DelegatedNLPTask(client: ActorRef, task: NLPTask)

  private[nlp] def toPlainText(jCas: JCas): String = {
    val result = new StringBuilder()
    val token2WordIdx = (for (word <- JCasUtil.select(jCas, classOf[Word]))
      yield word.getToken -> word).toMap
    for (sentence <- JCasUtil.select(jCas, classOf[Sentence])) {
      for (token <- JCasUtil.selectCovered(jCas, classOf[Token], sentence)) {
        result.append(escapeToken(token.getCoveredText()))
        result.append('\t')
        result.append(token.getType().getShortName())
        token2WordIdx.get(token) match {
          case Some(word) =>
            result.append('\t')
            result.append(MorphCasUtils.requireOnlyWordform(word).getPos)
          case None =>
        }
        // token delimiter
        result.append('\n')
      }
      // sentence delimiter
      result.append('\n')
    }
    result.toString
  }

  private def escapeToken(txt: String): String = txt.replaceAll("[\t\n\r]+", " ")
}