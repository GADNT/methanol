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

package com.github.mizosoft.methanol.testing;

import static java.util.Objects.requireNonNull;

import com.github.mizosoft.methanol.MediaType;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link FileTypeDetector} that pulls media type from registered entries.
 */
public class RegistryFileTypeDetector extends FileTypeDetector {

  private static final ConcurrentMap<String, MediaType> REGISTRY = new ConcurrentHashMap<>();

  @Override
  public String probeContentType(Path path) {
    MediaType type = REGISTRY.get(getExtension(path).toLowerCase());
    return type != null ? type.toString() : null;
  }

  public static void register(String ext, MediaType type) {
    requireNonNull(ext);
    requireNonNull(type);
    REGISTRY.put(ext.toLowerCase(), type);
  }

  private static String getExtension(Path path) {
    String ext = "";
    Path filenameComponent = path.getFileName();
    if (filenameComponent != null) {
      String filename = filenameComponent.toString();
      int dot = filename.indexOf('.');
      if (dot != -1) {
        ext = filename.substring(dot + 1);
      }
    }
    return ext;
  }
}
