// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////
package com.google.pubsub.kafka.sink;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.pubsub.kafka.common.ConnectorUtils;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link CloudPubSubSinkTask}.
 */
public class CloudPubSubSinkTaskTest {

  private static final String CPS_TOPIC = "the";
  private static final String CPS_PROJECT = "quick";
  private static final String CPS_MIN_BATCH_SIZE1 = "2";
  private static final String CPS_MIN_BATCH_SIZE2 = "3";
  private static final String KAFKA_TOPIC = "brown";
  private static final ByteString KAFKA_MESSAGE1 = ByteString.copyFromUtf8("fox");
  private static final ByteString KAFKA_MESSAGE2 = ByteString.copyFromUtf8("jumped");
  private static final String KAFKA_MESSAGE_KEY = "over";
  private static final Schema STRING_SCHEMA = SchemaBuilder.string().build();
  private static final Schema BYTE_STRING_SCHEMA =
      SchemaBuilder.bytes().name(ConnectorUtils.SCHEMA_NAME).build();

  private CloudPubSubSinkTask task;
  private Map<String, String> props;
  private CloudPubSubPublisher publisher;

  @Before
  public void setup() {
    publisher = mock(CloudPubSubPublisher.class, RETURNS_DEEP_STUBS);
    task = new CloudPubSubSinkTask(publisher);
    props = new HashMap<>();
    props.put(ConnectorUtils.CPS_TOPIC_CONFIG, CPS_TOPIC);
    props.put(ConnectorUtils.CPS_PROJECT_CONFIG, CPS_PROJECT);
    props.put(CloudPubSubSinkConnector.CPS_MIN_BATCH_SIZE_CONFIG, CPS_MIN_BATCH_SIZE2);
  }

  /**
   * Tests that an exception is thrown when the schema of the value is not ByteString.
   */
  @Test(expected = DataException.class)
  public void testPutWhenValueSchemaIsNotByteString() {
    task.start(props);
    Schema wrongSchema = SchemaBuilder.type(Schema.Type.BOOLEAN).build();
    SinkRecord record = new SinkRecord(null, -1, null, null, wrongSchema, null, -1);
    List<SinkRecord> list = new ArrayList<>();
    list.add(record);
    task.put(list);

  }

  /**
   * Tests that an exception is thrown when the schema name of the value is not ByteString.
   */
  @Test(expected = DataException.class)
  public void testPutWhenValueSchemaNameIsNotByteString() {
    task.start(props);
    Schema wrongSchema = SchemaBuilder.type(Schema.Type.BYTES).name("").build();
    SinkRecord record = new SinkRecord(null, -1, null, null, wrongSchema, null, -1);
    List<SinkRecord> list = new ArrayList<>();
    list.add(record);
    task.put(list);
  }

  /**
   * Tests that is no publishes are started from put(), that the publisher never
   * invokes its publish() function.
   */
  @Test
  public void testPutWhereNoPublishesAreInvoked() {
    task.start(props);
    List<SinkRecord> records = getSampleRecords();
    task.put(records);
    verify(publisher, never()).publish(any(PublishRequest.class));
  }

  /**
   * Tests that if publishes are started from put(), that the PublishRequest sent to the
   * publisher is correct.
   */
  @Test
  public void testPutWherePublishesAreInvoked() {
    props.put(CloudPubSubSinkConnector.CPS_MIN_BATCH_SIZE_CONFIG, CPS_MIN_BATCH_SIZE1);
    task.start(props);
    List<SinkRecord> records = getSampleRecords();
    task.put(records);
    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(publisher, times(1)).publish(captor.capture());
    PublishRequest requestArg = captor.getValue();
    assertEquals(requestArg.getMessagesList(), getPubsubMessagesFromSampleRecords());
  }

  /**
   * Tests that a call to flush() processes the Future's that were generated during this
   * same call to flush() (i.e put() did not publish anything).
   */
  @Test
  public void testFlush() throws Exception{
    task.start(props);
    Map<TopicPartition, OffsetAndMetadata> partitionOffsets = new HashMap<>();
    partitionOffsets.put(new TopicPartition(KAFKA_TOPIC, 0), null);
    List<SinkRecord> records = getSampleRecords();
    ListenableFuture<PublishResponse> goodFuture =
        spy(Futures.immediateFuture(PublishResponse.getDefaultInstance()));
    when(publisher.publish(any(PublishRequest.class))).thenReturn(goodFuture);
    task.put(records);
    task.flush(partitionOffsets);
    verify(publisher, times(1)).publish(any(PublishRequest.class));
    verify(goodFuture, times(1)).get();
  }

  /**
   * Tests that if a Future that is being processed in flush() failed with an exception,
   * that an exception is thrown.
   */
  @Test(expected = RuntimeException.class)
  public void testFlushExceptionCase() throws Exception{
    task.start(props);
    Map<TopicPartition, OffsetAndMetadata> partitionOffsets = new HashMap<>();
    partitionOffsets.put(new TopicPartition(KAFKA_TOPIC, 0), null);
    List<SinkRecord> records = getSampleRecords();
    ListenableFuture<PublishResponse> badFuture =
        spy(Futures.immediateFailedFuture(new Exception()));
    when(publisher.publish(any(PublishRequest.class))).thenReturn(badFuture);
    task.put(records);
    task.flush(partitionOffsets);
    verify(publisher, times(1)).publish(any(PublishRequest.class));
    verify(badFuture, times(1)).get();
  }


  /**
   * Get some sample SinkRecords's to use in the tests.
   */
  private List<SinkRecord> getSampleRecords() {
    List<SinkRecord> records = new ArrayList<>();
    records.add(new SinkRecord(KAFKA_TOPIC, 0, STRING_SCHEMA, KAFKA_MESSAGE_KEY,
        BYTE_STRING_SCHEMA, KAFKA_MESSAGE1, -1));
    records.add(new SinkRecord(KAFKA_TOPIC, 0, STRING_SCHEMA, KAFKA_MESSAGE_KEY,
        BYTE_STRING_SCHEMA, KAFKA_MESSAGE2, -1));
    return records;
  }

  /**
   * Get some PubsubMessage's which correspond to the SinkRecord's created
   * in {@link #getSampleRecords()}.
   */
  private List<PubsubMessage> getPubsubMessagesFromSampleRecords() {
    List<PubsubMessage> messages = new ArrayList<>();
    Map<String, String> attributes = new HashMap<>();
    attributes.put(ConnectorUtils.CPS_MESSAGE_KEY_ATTRIBUTE, KAFKA_MESSAGE_KEY);
    attributes.put(ConnectorUtils.CPS_MESSAGE_KAFKA_TOPIC_ATTRIBUTE, KAFKA_TOPIC);
    attributes.put(ConnectorUtils.CPS_MESSAGE_PARTITION_ATTRIBUTE, String.valueOf(0));
    messages.add(
        PubsubMessage.newBuilder().putAllAttributes(attributes).setData(KAFKA_MESSAGE1).build());
    messages.add(
        PubsubMessage.newBuilder().putAllAttributes(attributes).setData(KAFKA_MESSAGE2).build());
    return messages;
  }
}

