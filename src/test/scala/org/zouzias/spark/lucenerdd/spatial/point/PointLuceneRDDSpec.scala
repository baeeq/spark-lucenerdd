/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zouzias.spark.pointLuceneRDD.spatial.point

import com.holdenkarau.spark.testing.SharedSparkContext
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.zouzias.spark.lucenerdd.spatial.point.PointLuceneRDD
import org.zouzias.spark.lucenerdd.implicits.LuceneRDDImplicits._
import org.zouzias.spark.lucenerdd.spatial.implicits.PointLuceneRDDImplicits._

class PointLuceneRDDSpec extends FlatSpec
  with Matchers
  with BeforeAndAfterEach
  with SharedSparkContext {

  var pointLuceneRDD: PointLuceneRDD[_, _] = _

  override def afterEach() {
    pointLuceneRDD.close()
  }

  // Check if sequence is sorted in descending order
  def sortedDesc(seq : Seq[Float]) : Boolean = {
    if (seq.isEmpty) true else seq.zip(seq.tail).forall(x => x._1 >= x._2)
  }

  "PointLuceneRDD.knn" should "return k-nearest neighbors (knn)" in {
    val Bern = ( (7.45, 46.95), "Bern")
    val Zurich = ( (8.55, 47.366667), "Zurich")
    val Laussanne = ( (6.6335, 46.519833), "Laussanne")
    val Athens = ((23.716667, 37.966667), "Athens")
    val Toronto = ((-79.4, 43.7), "Toronto")
    val k = 5

    val cities = Array(Bern, Zurich, Laussanne, Athens, Toronto)
    val rdd = sc.parallelize(cities)
    pointLuceneRDD = PointLuceneRDD(rdd)

    val results = pointLuceneRDD.knn(Bern._1, k)

    results.head.doc.textField("_1").exists(l => l.exists(_ == "Bern")) should equal(true)
    results.last.doc.textField("_1").exists(l => l.exists(_ == "Toronto")) should equal(true)

    // Reverted distances
    val revertedDists = results.map(_.score).toList.reverse
    sortedDesc(revertedDists)
  }
}
