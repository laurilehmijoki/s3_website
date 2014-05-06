package s3.website

import s3.website.model.Config
import scala.collection.parallel.{ForkJoinTaskSupport, ParSeq}
import scala.concurrent.forkjoin.ForkJoinPool

class Utils(implicit config: Config) {
  def toParSeq[T](seq: Seq[T]): ParSeq[T] = {
    val parallelSeq: ParSeq[T] = seq.par
    parallelSeq.tasksupport_=(new ForkJoinTaskSupport(new ForkJoinPool(config.concurrency_level)))
    parallelSeq
  }
}
