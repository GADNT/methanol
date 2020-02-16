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

package com.github.mizosoft.methanol.internal.spi;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Utility for loading/caching service providers. */
public class ServiceCache<S> {

  private final Class<S> service;
  private final ReentrantLock lock = new ReentrantLock();
  private volatile @MonotonicNonNull List<S> providers;

  public ServiceCache(Class<S> service) {
    this.service = requireNonNull(service);
  }

  public List<S> getProviders() {
    List<S> cached = providers;
    if (cached == null) {
      // Prevent getProvider() from being called from a provider constructor/method
      if (lock.isHeldByCurrentThread()) {
        throw new ServiceConfigurationError("recursive loading of providers");
      }
      try {
        lock.lock();
        cached = providers;
        if (cached == null) {
          cached = loadProviders();
          providers = cached;
        }
      } finally {
        lock.unlock();
      }
    }
    return providers;
  }

  private List<S> loadProviders() {
    return ServiceLoader.load(service, ClassLoader.getSystemClassLoader()).stream()
        .map(ServiceLoader.Provider::get)
        .collect(Collectors.toUnmodifiableList());
  }
}
