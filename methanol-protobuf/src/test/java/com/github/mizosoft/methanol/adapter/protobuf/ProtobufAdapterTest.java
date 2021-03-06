/*
 * Copyright (c) 2019, 2020 Moataz Abdelnasser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.mizosoft.methanol.adapter.protobuf;

import static com.github.mizosoft.methanol.adapter.protobuf.ProtobufAdapterFactory.createDecoder;
import static com.github.mizosoft.methanol.adapter.protobuf.ProtobufAdapterFactory.createEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.TypeRef;
import com.github.mizosoft.methanol.adapter.protobuf.TestProto.AwesomePerson;
import com.github.mizosoft.methanol.adapter.protobuf.TestProto.Awesomeness;
import com.github.mizosoft.methanol.testutils.BodyCollector;
import com.github.mizosoft.methanol.testutils.TestUtils;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProtobufAdapterTest {

  @Test
  void isCompatibleWith_supportsType() {
    for (var c : List.of(createEncoder(), ProtobufAdapterFactory.createDecoder())) {
      assertTrue(c.isCompatibleWith(MediaType.of("application", "octet-stream")));
      assertTrue(c.isCompatibleWith(MediaType.of("application", "x-protobuf")));
      assertTrue(c.isCompatibleWith(MediaType.of("application", "*")));
      assertFalse(c.isCompatibleWith(MediaType.of("application", "json")));
      assertTrue(c.supportsType(TypeRef.from(MessageLite.class)));
      assertTrue(c.supportsType(TypeRef.from(Message.class)));
      assertFalse(c.supportsType(TypeRef.from(String.class)));
    }
  }

  @Test
  void unsupportedConversion_encoder() {
    var encoder = createEncoder();
    assertThrows(UnsupportedOperationException.class, () -> encoder.toBody("Not a Message!", null));
    assertThrows(UnsupportedOperationException.class,
        () -> encoder.toBody(AwesomePerson.newBuilder().build(), MediaType.of("text", "plain")));
  }

  @Test
  void unsupportedConversion_decoder() {
    var decoder = ProtobufAdapterFactory.createDecoder();
    assertThrows(UnsupportedOperationException.class,
        () -> decoder.toObject(TypeRef.from(String.class), null));
    assertThrows(UnsupportedOperationException.class,
        () -> decoder.toObject(TypeRef.from(AwesomePerson.class),
            MediaType.of("text", "plain")));
  }

  @Test
  void serializeMessage() {
    var elon = AwesomePerson.newBuilder()
        .setFirstName("Elon")
        .setLastName("Musk")
        .setAge(48)
        .build();
    var body = createEncoder().toBody(elon, null);
    assertEquals(elon.toByteString(), ByteString.copyFrom(BodyCollector.collect(body)));
  }

  @Test
  void deserializeMessage() {
    var subscriber = ProtobufAdapterFactory
        .createDecoder().toObject(TypeRef.from(AwesomePerson.class), null);
    var elon = AwesomePerson.newBuilder()
        .setFirstName("Elon")
        .setLastName("Musk")
        .setAge(48)
        .build();
    assertEquals(elon, publishMessage(subscriber, elon));
  }

  @Test
  void deserializeMessage_withExtensions() {
    var registry = ExtensionRegistry.newInstance();
    registry.add(TestProto.awesomeness);
    var subscriber = createDecoder(registry)
        .toObject(TypeRef.from(AwesomePerson.class), null);
    var elon = AwesomePerson.newBuilder()
        .setFirstName("Elon")
        .setLastName("Musk")
        .setAge(48)
        .setExtension(TestProto.awesomeness, Awesomeness.SUPER_AWESOME)
        .build();
    assertEquals(elon, publishMessage(subscriber, elon));
  }

  @Test
  void deserializeMessage_deferred() {
    var subscriber = ProtobufAdapterFactory.createDecoder()
        .toDeferredObject(TypeRef.from(AwesomePerson.class), null);
    var elon = AwesomePerson.newBuilder()
        .setFirstName("Elon")
        .setLastName("Musk")
        .setAge(48)
        .build();
    var supplier = subscriber.getBody().toCompletableFuture().getNow(null);
    assertNotNull(supplier);
    new Thread(() -> {
      subscriber.onSubscribe(TestUtils.NOOP_SUBSCRIPTION);
      subscriber.onNext(List.of(ByteBuffer.wrap(elon.toByteArray())));
      subscriber.onComplete();
    }).start();
    assertEquals(elon, supplier.get());
  }

  @Test
  void deserializeMessage_deferredWithError() {
    var subscriber = ProtobufAdapterFactory.createDecoder()
        .toDeferredObject(TypeRef.from(AwesomePerson.class), null);
    var supplier = subscriber.getBody().toCompletableFuture().getNow(null);
    assertNotNull(supplier);
    new Thread(() -> {
      subscriber.onSubscribe(TestUtils.NOOP_SUBSCRIPTION);
      subscriber.onNext(List.of(ByteBuffer.wrap("not wire, obviously.".getBytes())));
      subscriber.onComplete();
    }).start();
    var uioe = assertThrows(UncheckedIOException.class, supplier::get);
    assertTrue(uioe.getCause() instanceof InvalidProtocolBufferException);
  }

  public static <T extends Message> T publishMessage(BodySubscriber<T> subscriber, Message message) {
    subscriber.onSubscribe(TestUtils.NOOP_SUBSCRIPTION);
    subscriber.onNext(List.of(ByteBuffer.wrap(message.toByteArray())));
    subscriber.onComplete();
    return subscriber.getBody().toCompletableFuture().join();
  }
}
