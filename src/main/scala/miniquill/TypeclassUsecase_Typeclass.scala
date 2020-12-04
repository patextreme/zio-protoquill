package miniquill

import simple.SimpleMacro._
import scala.language.implicitConversions
import miniquill.quoter.Dsl._
import scala.compiletime.{erasedValue, summonFrom, constValue}

object TypeclassUsecase_Typeclass {
  import io.getquill._
  case class Address(street: String, zip: Int) extends Embedded
  given Embedable[Address]
  val ctx = new MirrorContext(MirrorSqlDialect, Literal)
  import ctx._

  case class Node(id: Int, timestamp: Int, status: String)
  case class Master(key: Int, lastCheck: Int, state: String)
  case class Worker(shard: Int, lastTime: Int, reply: String)


  trait GroupKey[T, G]:
    inline def apply(inline t: T): G
  trait EarlierThan[T]:
    inline def apply(inline a: T, inline b: T): Boolean
  
  inline given GroupKey[Node, Int]:
    inline def apply(inline t: Node): Int = t.id
  inline given GroupKey[Master, Int]:
    inline def apply(inline t: Master): Int = t.key
  inline given GroupKey[Worker, Int]: 
    inline def apply(inline t: Worker): Int = t.shard

  inline given EarlierThan[Node]: 
    inline def apply(inline a: Node, inline b: Node) = a.timestamp < b.timestamp
  inline given EarlierThan[Master]: 
    inline def apply(inline a: Master, inline b: Master) = a.lastCheck < b.lastCheck
  inline given EarlierThan[Worker]: 
    inline def apply(inline a: Worker, inline b: Worker) = a.lastTime < b.lastTime

  def main(args: Array[String]): Unit = {
        
    inline def latestStatus[T, G](inline q: Query[T])(using inline groupKey: GroupKey[T, G], inline earlierThan: EarlierThan[T]) =
      q.leftJoin(q)
      .on((a, b) => 
          groupKey(b) == groupKey(a) &&
          earlierThan(b, a)
      )
      .filter((a, b) => 
        b.map(b => groupKey(b)).isEmpty)
      .map((a, b) => a)

    inline def nodesLatest = quote { latestStatus(query[Node]) }
    inline def mastersLatest = quote { latestStatus(query[Master]) }
    inline def workersLatest = quote { latestStatus(query[Worker]) }

    println( run(nodesLatest).string )
    println( run(mastersLatest).string )
    println( run(workersLatest).string )
  }
}