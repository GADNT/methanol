/*
 * MIT License
 *
 * Copyright (c) 2019 Moataz Abdelnasser
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

package com.github.mizosoft.methanol;

import static java.util.Objects.requireNonNull;

import com.github.mizosoft.methanol.internal.extensions.BasicResponseInfo;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides additional {@link java.net.http.HttpResponse.BodyHandler} implementations.
 */
public class MoreBodyHandlers {

  private MoreBodyHandlers() { // non-instantiable
  }

  /**
   * Returns a {@code BodyHandler} that wraps the result of the given handler in a {@link
   * BodyDecoder} if required. The decoder is created using the factory corresponding to the value
   * of the {@code Content-Type} header, throwing {@code UnsupportedOperationException} if no such
   * factory is registered. If the header is not present, the result of the given handler is
   * returned directly.
   *
   * <p>The {@code Content-Encoding} and {@code Content-Length} headers are removed when invoking
   * the given handler to avoid recursive decompression attempts or using the wrong body length.
   *
   * @param downstreamHandler the handler returning the downstream
   * @param <T>               the subscriber's body type
   */
  public static <T> BodyHandler<T> decoding(BodyHandler<T> downstreamHandler) {
    requireNonNull(downstreamHandler);
    return decodingInternal(downstreamHandler, null);
  }

  /**
   * Returns a {@code BodyHandler} that wraps the result of the given handler in a {@link
   * BodyDecoder} with the given executor if required. The decoder is created using the factory
   * corresponding to the value of the {@code Content-Type} header, throwing {@code
   * UnsupportedOperationException} if no such factory is registered. If the header is not present,
   * the result of the given handler is returned directly.
   *
   * <p>The {@code Content-Encoding} and {@code Content-Length} headers are removed when invoking
   * the given handler to avoid recursive decompression attempts or using the wrong body length.
   *
   * @param downstreamHandler the handler returning the downstream
   * @param executor          the executor used to supply downstream items
   * @param <T>               the subscriber's body type
   */
  public static <T> BodyHandler<T> decoding(BodyHandler<T> downstreamHandler, Executor executor) {
    requireNonNull(downstreamHandler, "downstreamHandler");
    requireNonNull(executor, "executor");
    return decodingInternal(downstreamHandler, executor);
  }

  private static <T> BodyHandler<T> decodingInternal(
      BodyHandler<T> downstreamHandler, @Nullable Executor executor) {
    return info -> {
      Optional<String> encHeader = info.headers().firstValue("Content-Encoding");
      if (encHeader.isEmpty()) {
        return downstreamHandler.apply(info); // No decompression needed
      }
      String enc = encHeader.get();
      BodyDecoder.Factory factory = BodyDecoder.Factory.getFactory(enc)
          .orElseThrow(() -> new UnsupportedOperationException("Unsupported encoding: " + enc));
      HttpHeaders headersCopy = HttpHeaders.of(info.headers().map(),
          (n, v) -> !"Content-Encoding".equalsIgnoreCase(n)
              && !"Content-Length".equalsIgnoreCase(n));
      BodySubscriber<T> downstream = downstreamHandler.apply(
          new BasicResponseInfo(info.statusCode(), headersCopy, info.version()));
      return executor != null ? factory.create(downstream, executor) : factory.create(downstream);
    };
  }
}