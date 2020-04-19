package akkablockchain.actor

import akkablockchain.model._

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Cancellable}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object TransactionFeeder {
  def props(recipient: ActorRef, interval: FiniteDuration): Props =
    Props(classOf[TransactionFeeder], recipient, interval)

  sealed trait Cmd
  case object Run extends Cmd
  case object StopRunning extends Cmd
  case object Tick extends Cmd
}

class TransactionFeeder(recipient: ActorRef, interval: FiniteDuration) extends Actor with ActorLogging {
  import TransactionFeeder._

  implicit val ec: ExecutionContext = context.dispatcher

  val numOfAccounts = 10
  val maxTransItems = 3

  val keyPath = "src/main/resources/keys/"

  // Keypairs as PKCS#8 PEM files already created to save initial setup time
  // (0 until numOfAccounts).foreach(i => generateKeyPairPemFiles(s"${keyPath}account${i}"))
  val keyFiles = List.tabulate(numOfAccounts)(i => s"account${i}_public.pem")

  override def receive: Receive = idle

  def idle: Receive = {
    case Run =>
      val cancellable = context.system.scheduler.
        schedule(randomFcn.nextInt(1, 4).seconds, interval, self, Tick)
      context.become(running(cancellable))

    case _ =>
      // Do nothing
  }

  def running(cancellable: Cancellable): Receive = {
    case Run =>
      log.error(s"[TransactionFeeder] Run received but $this is already running!")

    case Tick =>
      recipient ! Blockchainer.SubmitTransactions(generateTrans(numOfAccounts, maxTransItems, keyPath, keyFiles))

    case StopRunning =>
      cancellable.cancel
      log.info(s"[TransactionFeeder] StopRunning received. $this stopped!")
      context.become(idle)
  }

  private def generateTrans(numOfAccounts: Int, maxTransItems: Int, keyPath: String, keyFiles: List[String]): Transactions = {
    def genTransItem: TransactionItem = {
      val idx = distinctRandomIntPair(0, numOfAccounts)
      val accountFrom = Account.fromKeyFile(s"${keyPath}${keyFiles(idx(0))}", s"User${idx(0)}")
      val accountTo = Account.fromKeyFile(s"${keyPath}${keyFiles(idx(1))}", s"User${idx(1)}")
      val amount = 1000L + randomFcn.nextInt(0, 5) * 500L

      TransactionItem(accountFrom, accountTo, amount, System.currentTimeMillis)
    }

    val numOfTransItems = randomFcn.nextInt(1, maxTransItems + 1)
    val transItems = Array.tabulate(numOfTransItems)(_ => genTransItem)

    Transactions(transItems, System.currentTimeMillis)
  }

  private def randomFcn = java.util.concurrent.ThreadLocalRandom.current

  private def distinctRandomIntPair(lower: Int, upper: Int): List[Int] = {
    val rand1 = randomFcn.nextInt(lower, upper)
    val rand2 = randomFcn.nextInt(lower, upper)
    if (rand1 != rand2)
      List(rand1, rand2)
    else
      List(rand1, if (rand2 < upper - 1) rand2 + 1 else lower)
  }
}
