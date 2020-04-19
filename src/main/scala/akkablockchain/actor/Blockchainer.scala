package akkablockchain.actor

import akkablockchain.model._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

object Blockchainer {
  def props(accountKey: String, timeoutMining: Long, timeoutValidation: Long): Props =
    Props(classOf[Blockchainer], accountKey, timeoutMining, timeoutValidation)

  sealed trait Req
  case object GetTransactionQueue extends Req
  case object GetBlockchain extends Req
  case class SubmitTransactions(trans: Transactions, append: Boolean = true) extends Req
  case object Mining extends Req

  sealed trait Cmd
  case class AddTransactions(trans: Transactions, append: Boolean = true) extends Cmd
  case class UpdateBlockchain(block: Block) extends Cmd

  case class BusyException(message: String, cause: Throwable = None.orNull) extends Exception(message, cause)
}

class Blockchainer(accountKey: String, timeoutMining: Long, timeoutValidation: Long) extends Actor with ActorLogging {
  import Blockchainer._

  implicit val ec: ExecutionContext = context.dispatcher

  val tmoMining: Timeout = Timeout(timeoutMining.millis)
  val tmoValidation: Timeout = Timeout(timeoutValidation.millis)

  val timeoutPoW = timeoutMining * 9 / 10

  val mediator: ActorRef = DistributedPubSub(context.system).mediator
  val miner: ActorRef = context.actorOf(Miner.props(accountKey, timeoutPoW), "miner")
  val blockInspector: ActorRef = context.actorOf(BlockInspector.props, "blockInspector")

  private var transactionQueue: Queue[Transactions] = Queue.empty[Transactions]
  private var blockchain: Block = RootBlock

  mediator ! Subscribe("new-transactions", self)
  mediator ! Subscribe("new-block", self)

  override def receive: Receive = {
    // -- Blockchainer requests ------

    case GetTransactionQueue =>
      sender() ! transactionQueue

    case GetBlockchain =>
      sender() ! blockchain

    case SubmitTransactions(trans, append) =>
      if (trans.hasValidItems) {
        mediator ! Publish("new-transactions", AddTransactions(trans, append))
        log.info(s"[Req.SubmitTransactions] ${this}: $trans is published.")
      } else
        log.error(s"[Req.SubmitTransactions] ${this}: ERROR: $trans is invalid or already exists in queue!")

    case Mining =>
      if (transactionQueue.isEmpty) {
        log.error(s"[Req.Mining] ${this}: ERROR: Transaction queue is empty!")
      } else {
        val blockPrev = blockchain
        val (trans, remaining) = transactionQueue.dequeue
        transactionQueue = remaining

        (miner ? Miner.Mine(blockPrev, trans))(tmoMining).mapTo[Block] onComplete{
          case Success(block) =>
            mediator ! Publish("new-block", UpdateBlockchain(block))
            miner ! Miner.DoneMining
          case Failure(e) =>
            log.error(s"[Req.Mining] ${this}: ERROR: $e")
            e match {
              case _: BusyException => self ! AddTransactions(trans, append = false)  // No need to publish
              case _ => miner ! Miner.DoneMining
            }
        }
      }

    // -- Blockchainer commands ------

    case AddTransactions(trans, append) =>
      if (trans.hasValidItems && allNewToTransQ(trans, transactionQueue)) {
        if (append) {
          transactionQueue :+= trans
          log.info(s"[Cmd.AddTransactions] ${this}: Appended $trans to transaction queue.")
        } else {
          transactionQueue +:= trans
          log.info(s"[Cmd.AddTransactions] ${this}: Prepended $trans to transaction queue.")
        }
      } else
        log.error(s"[Cmd.AddTransactions] ${this}: ERROR: $trans is invalid or already exists in queue!")

    case UpdateBlockchain(block) =>
      (blockInspector ? BlockInspector.Validate(block))(tmoValidation).mapTo[Boolean] onComplete{
        case Success(isValid) =>
          if (isValid) {
            if (block.length > blockchain.length) {
              log.info(s"[Cmd.UpdateBlockchain] ${this}: $block is valid. Updating blockchain.")
              blockchain = block
            } else
              log.warning(s"[Cmd.UpdateBlockchain] ${this}: $block isn't longer than existing blockchain! Blockchain not updated.")
          } else
            log.error(s"[Cmd.UpdateBlockchain] ${this}: ERROR: $block is invalid!")
          blockInspector ! BlockInspector.DoneValidation

        case Failure(e) =>
          log.error(s"[Cmd.UpdateBlockchain] ${this}: ERROR: Validation of $block failed! $e")
          e match {
            case _: BusyException => // do nothing
            case _ => blockInspector ! BlockInspector.DoneValidation
          }
      }

    // -- PubSub acks ------

    case SubscribeAck(Subscribe("new-transactions", None, `self`)) =>
      log.info(s"[Ack] ${this}: Subscribing to 'new-transactions' ...")

    case SubscribeAck(Subscribe("new-block", None, `self`)) =>
      log.info(s"[Ack] ${this}: Subscribing to 'new-block' ...")
  }

  // -- Validations ------

  private def allNewToTransQ(trans: Transactions, transQ: => Queue[Transactions]): Boolean =
    ! transQ.map(_.id).contains(trans.id) &&
      ! trans.items.exists(item => transQ.flatMap(_.items.map(_.id)).contains(item.id))
}
