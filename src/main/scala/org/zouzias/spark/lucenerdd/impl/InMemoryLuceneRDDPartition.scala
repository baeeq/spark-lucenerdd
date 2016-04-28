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

package org.zouzias.spark.lucenerdd.impl

import org.apache.lucene.document._
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.search._
import org.apache.spark.Logging
import org.zouzias.spark.lucenerdd.AbstractLuceneRDDPartition
import org.zouzias.spark.lucenerdd.analyze.WSAnalyzer
import org.zouzias.spark.lucenerdd.model.SparkScoreDoc
import org.zouzias.spark.lucenerdd.query.LuceneQueryHelpers
import org.zouzias.spark.lucenerdd.store.InMemoryIndexStorable

import scala.reflect.{ClassTag, _}

private[lucenerdd] class InMemoryLuceneRDDPartition[T]
(private val iter: Iterator[T])
(implicit docConversion: T => Document,
 override implicit val kTag: ClassTag[T])
  extends AbstractLuceneRDDPartition[T]
  with InMemoryIndexStorable
  with WSAnalyzer
  with Logging {

  private lazy val indexWriter = new IndexWriter(IndexDir,
    new IndexWriterConfig(Analyzer)
    .setOpenMode(OpenMode.CREATE))

  private val (iterOriginal, iterIndex) = iter.duplicate

  iterIndex.foreach { case elem =>
    // Convert it to lucene document
    indexWriter.addDocument(docConversion(elem))
  }

  indexWriter.commit()
  indexWriter.close()

  private val indexReader = DirectoryReader.open(IndexDir)
  private val indexSearcher = new IndexSearcher(indexReader)

  override def close(): Unit = {
    indexReader.close()
  }

  override def size: Long = {
    LuceneQueryHelpers.totalDocs(indexSearcher)
  }

  override def isDefined(elem: T): Boolean = {
    iterOriginal.contains(elem)
  }

  override def multiTermQuery(docMap: Map[String, String], topK: Int): Seq[SparkScoreDoc] = {
   LuceneQueryHelpers.multiTermQuery(indexSearcher, docMap, topK)
  }

  override def iterator: Iterator[T] = {
    iterOriginal
  }

  override def filter(pred: T => Boolean): AbstractLuceneRDDPartition[T] =
    new InMemoryLuceneRDDPartition(iterOriginal.filter(pred))(docConversion, kTag)

  override def termQuery(fieldName: String, fieldText: String,
                         topK: Int = 1): Iterable[SparkScoreDoc] = {
    LuceneQueryHelpers.termQuery(indexSearcher, fieldName, fieldText, topK)
  }

  override def query(q: Query, topK: Int): Iterable[SparkScoreDoc] = {
    LuceneQueryHelpers.searchTopK(indexSearcher, q, topK)
  }

  override def prefixQuery(fieldName: String, fieldText: String,
                           topK: Int): Iterable[SparkScoreDoc] = {
    LuceneQueryHelpers.prefixQuery(indexSearcher, fieldName, fieldText, topK)
   }

  override def fuzzyQuery(fieldName: String, fieldText: String,
                          maxEdits: Int, topK: Int): Iterable[SparkScoreDoc] = {
    LuceneQueryHelpers.fuzzyQuery(indexSearcher, fieldName, fieldText, maxEdits, topK)
  }

  override def phraseQuery(fieldName: String, fieldText: String,
                           topK: Int): Iterable[SparkScoreDoc] = {
    LuceneQueryHelpers.phraseQuery(indexSearcher, fieldName, fieldText, topK)
  }
}

object InMemoryLuceneRDDPartition {
  def apply[T: ClassTag]
      (iter: Iterator[T])(implicit docConversion: T => Document): InMemoryLuceneRDDPartition[T] = {
    new InMemoryLuceneRDDPartition[T](iter)(docConversion, classTag[T])
  }
}