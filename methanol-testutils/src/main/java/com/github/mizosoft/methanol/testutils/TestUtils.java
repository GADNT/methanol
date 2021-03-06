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

package com.github.mizosoft.methanol.testutils;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow.Subscription;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.SSLContext;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

public class TestUtils {

  public static final Subscription NOOP_SUBSCRIPTION =
      new Subscription() {
        @Override
        public void request(long n) {}

        @Override
        public void cancel() {}
      };

  public static void awaitUninterruptedly(CountDownLatch latch) {
    while (true) {
      try {
        latch.await();
        return;
      } catch (InterruptedException ignored) {
        // continue;
      }
    }
  }

  public static void shutdown(Executor... executors) {
    for (Executor e : executors) {
      if (e instanceof ExecutorService) {
        ((ExecutorService) e).shutdown();
      }
    }
  }

  public static byte[] gunzip(byte[] data) {
    try {
      return new GZIPInputStream(new ByteArrayInputStream(data)).readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] inflate(byte[] data) {
    try {
      return new InflaterInputStream(new ByteArrayInputStream(data)).readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static HttpHeaders headers(String... pairs) {
    var headers = new LinkedHashMap<String, List<String>>();
    for (int i = 0, len = pairs.length; i < len; i += 2) {
      headers.put(pairs[i], List.of(pairs[i + 1]));
    }
    return HttpHeaders.of(headers, (n, v) -> true);
  }

  public static int copyRemaining(ByteBuffer src, ByteBuffer dst) {
    int toCopy = Math.min(src.remaining(), dst.remaining());
    int srcLimit = src.limit();
    src.limit(src.position() + toCopy);
    dst.put(src);
    src.limit(srcLimit);
    return toCopy;
  }

  public static byte[] load(Class<?> caller, String location) {
    var in = caller.getResourceAsStream(location);
    if (in == null) {
      throw new AssertionError("couldn't find resource: " + location);
    }
    try (in) {
      return in.readAllBytes();
    } catch (IOException ioe) {
      throw new UncheckedIOException(ioe);
    }
  }

  public static String loadAscii(Class<?> caller, String location) {
    return US_ASCII.decode(ByteBuffer.wrap(load(caller, location))).toString();
  }

  public static List<String> lines(String s) {
    return s.lines().collect(Collectors.toUnmodifiableList());
  }

  public static List<Path> listFiles(Path dir) throws IOException {
    try (var stream = Files.list(dir)) {
      return stream.collect(Collectors.toUnmodifiableList());
    }
  }

  /** Build {@code SSLContext} that trusts a self-assigned certificate for localhost in tests. */
  public static SSLContext localhostSslContext() throws IOException {
    var heldCertificate =
        new HeldCertificate.Builder()
            .addSubjectAlternativeName(InetAddress.getByName("localhost").getCanonicalHostName())
            .build();
    return new HandshakeCertificates.Builder()
        .heldCertificate(heldCertificate)
        .addTrustedCertificate(heldCertificate.certificate())
        .build()
        .sslContext();
  }
}
