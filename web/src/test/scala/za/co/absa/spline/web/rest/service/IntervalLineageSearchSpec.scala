package za.co.absa.spline.web.rest.service

import java.net.URI
import java.util.UUID

import org.mockito.ArgumentMatchers.{eq => ≡, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFlatSpec, Matchers}
import za.co.absa.spline.model.dt.Simple
import za.co.absa.spline.model.op._
import za.co.absa.spline.model.{Attribute, DataLineage, MetaDataset, Schema, _}
import za.co.absa.spline.persistence.api.{CloseableIterable, DataLineageReader}
import za.co.absa.spline.web.rest.service.LineageService

import scala.concurrent.Future
import scala.language.postfixOps

/*
 * Copyright 2017 Barclays Africa Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
class IntervalLineageSearchSpec extends AsyncFlatSpec with Matchers with MockitoSugar {

  /*
      Composite [lineage] is a lineage viewed as an operation on datasets produced by other lineages
      This is a small example of 2 composites where one composite output is the other composite's input

          S1 --> S2

      The schema of C is the same as in A
  */

  val UUIDS1: UUID = UUID fromString "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
  val UUIDS2: UUID = UUID fromString "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

  val xUUID1: UUID = UUID fromString "11111111-1111-1111-1111-111111111111"
  val xUUID2: UUID = UUID fromString "22222222-2222-2222-2222-222222222222"
  val xUUID3: UUID = UUID fromString "33333333-3333-3333-3333-333333333333"

  val lineage1 = DataLineage("AppId1", "AppName1", 0, Seq(
    BatchWrite(OperationProps(UUID fromString "6d4d9268-2cf1-19d8-b654-d3a52f0affa1", "SaveIntoDataSourceCommand", Seq(), UUIDS1), "fileS1", "fileS1.txt", append = false)),
    Seq(MetaDataset(UUIDS1, Schema(Seq(xUUID3)))),
    Seq(Attribute(xUUID3, "a", Simple("int", nullable = false))))

  val lineage2 = DataLineage("AppId2", "AppName2", 0, Seq(
    BatchWrite(OperationProps(UUID fromString "6d4d9268-2cf1-19d8-b654-d3a52f0affa3", "SaveIntoDataSourceCommand", Seq(xUUID2), UUIDS2), "fileOut", "fileOut.txt", append = false),
    BatchRead(OperationProps(UUID fromString "6d4d9268-2cf1-19d8-b654-d3a52f0affa2", "LogicalRelation", Seq(UUIDS1), xUUID2), "fileS1", Seq(MetaDataSource("fileS1.txt", Seq(UUIDS1))))),
    Seq(
      MetaDataset(UUIDS2, Schema(Seq(xUUID3))),
      MetaDataset(xUUID2, Schema(Seq(xUUID3))),
      MetaDataset(UUIDS1, Schema(Seq(xUUID3)))),
    Seq(Attribute(xUUID3, "b", Simple("long", nullable = false))))

  it should "be able to construct small high order lineage out of 2 composites" in {
    val readerMock: DataLineageReader = mock[DataLineageReader]

    when(readerMock.loadByDatasetId(≡(UUIDS1))(any())) thenReturn Future.successful(Some(lineage1))
    when(readerMock.loadByDatasetId(≡(UUIDS2))(any())) thenReturn Future.successful(Some(lineage2))

    when(readerMock.getByDatasetIdsByPathAndInterval(≡("fileS1.txt"), any(), any())(any())) thenReturn
      Future.successful(new CloseableIterable[UUID](iterator = Seq(UUIDS1).iterator, closeFunction = {}))
    when(readerMock.getByDatasetIdsByPathAndInterval(≡("fileOut.txt"), any(), any())(any())) thenReturn
      Future.successful(new CloseableIterable[UUID](iterator = Seq(UUIDS2).iterator, closeFunction = {}))
//    when(readerMock.getByDatasetIdsByPathAndInterval(≡(UUIDS2))(any())) thenReturn Future.successful(Some(lineage2))

    when(readerMock.getDatasetDescriptor(≡(UUIDS1))(any())) thenReturn
      Future.successful(PersistedDatasetDescriptor(UUIDS1, lineage1.appId, lineage1.appId, new URI("fileS1.txt"), 11))
    when(readerMock.getDatasetDescriptor(≡(UUIDS2))(any())) thenReturn
      Future.successful(PersistedDatasetDescriptor(UUIDS2, lineage2.appId, lineage2.appId, new URI("fileOut.txt"), 11))
    when(readerMock.findByInputId(≡(UUIDS2))(any())) thenReturn Future.successful(new CloseableIterable(Iterator.empty, {}))

    val svc = new IntervalLineageService(readerMock)

    for (lin <- svc(UUIDS2, 10, 20)) yield {
      lin.operations.size shouldEqual 2
      lin.datasets.size shouldEqual 2
      lin.attributes.size shouldEqual 2
    }
  }
}
