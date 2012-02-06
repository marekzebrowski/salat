package com.novus.salat.test.performance

import com.novus.salat.test._
import com.novus.salat.test.RichDuration._
import com.novus.salat.util.Logging
import com.mongodb.casbah.Imports._
import org.specs2.mutable._
import org.specs2.specification.Scope
import com.novus.salat._
import com.novus.salat.dao.SalatDAO
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class BigDecimalStrategyPerformanceSpec extends Specification with Logging {

  // force sequential run
  override def is = args(sequential = true) ^ super.is

  case class bdc(strategy: BigDecimalStrategy) extends Scope {
    implicit val ctx = new Context {
      val name = "testBdc_%s".format(System.currentTimeMillis())
      override val typeHintStrategy = NeverTypeHint
      override val bigDecimalStrategy = strategy
    }

    val name = strategy.getClass.getSimpleName
    val coll = MongoConnection()("salat_test_performance")(name)
    coll.drop()
    coll.count must_== 0L

    val generator = new Random()
    val rangeMin = -1000000d
    val rangeMax = 1000000d
    val outTimes = ArrayBuffer.empty[Long]
    val inTimes = ArrayBuffer.empty[Long]

    object FooDAO extends SalatDAO[Foo, ObjectId](collection = coll) {
      override def insert(t: Foo) = timeAndLogNanos {
        super.insert(t)
      } {
        ns =>
          inTimes += ns
      }

      def findAll(): List[Foo] = {
        val builder = List.newBuilder[Foo]
        val cursor = coll.find()
        while (cursor.hasNext) {
          timeAndLogNanos {
            builder += _grater.asObject(cursor.next())
          } {
            ns =>
              outTimes += ns
          }
        }
        builder.result()
      }
    }

    def serialize(limit: Int) {
      //      log.debug("insert: %s - called with limit of %d", name, limit)
      for (i <- 0 until limit) {
        val r = (rangeMin + (rangeMax - rangeMin) * generator.nextDouble()).toString
        //        log.debug("serialize: r=%s", r)
        FooDAO.insert(Foo(x = BigDecimal(r, strategy.mathCtx)))
        if (i > 0 && i % 1000 == 0) {
          //          log.debug("insert: %s - %d of %d", name, i, limit)
        }
      }
      //      log.debug("inTimes: [%s]", inTimes.sorted.mkString(", "))
      inTimes.size must_== limit
    }

    def deserialize(limit: Int): List[Foo] = {
      val deserialized = FooDAO.findAll()
      //      log.debug("outTimes: [%s]", outTimes.sorted.mkString(", "))
      outTimes.size must_== limit
      deserialized.size must_== limit
      deserialized
    }

    def stats() {
      log.info(""" 

--------------------
      
COLLECTION: %s [%d entries]

STRATEGY: %s

SERIALIZATION TIMES:
  total: %s
  avg: %s ms
  median: %s ms

DESERIALIZATION TIMES:
  total: %s
  avg: %s ms
  median: %s ms

%s      

-------------------

      """,
        coll.name, coll.size,
        strategy.getClass.getName,
        (inTimes.sum / 1000000L).tersePrint, avg(inTimes) / 1000000d, median(inTimes) / 1000000d,
        (outTimes.sum / 1000000L).tersePrint, avg(outTimes) / 1000000d, median(outTimes) / 1000000d,
        coll.stats)
    }
  }

  "Testing performance of BigDecimalStrategy" should {
    val limit = 10000
    "test performance of BigDecimal <-> Binary" in new bdc(BigDecimalToBinaryStrategy(mathCtx = DefaultMathContext)) {
      serialize(limit)
      val deserialized = deserialize(limit)
      stats()
      deserialized.size must_== limit
    }
    "test performance of BigDecimal <-> Double" in new bdc(BigDecimalToDoubleStrategy(mathCtx = DefaultMathContext)) {
      serialize(limit)
      val deserialized = deserialize(limit)
      stats()
      deserialized.size must_== limit
    }
    "test performance of BigDecimal <-> String" in new bdc(BigDecimalToStringStrategy(mathCtx = DefaultMathContext)) {
      serialize(limit)
      val deserialized = deserialize(limit)
      stats()
      deserialized.size must_== limit
    }
  }

}