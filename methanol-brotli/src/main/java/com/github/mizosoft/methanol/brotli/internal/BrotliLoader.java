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

package com.github.mizosoft.methanol.brotli.internal;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Helper class for loading brotli JNI and setting bundled dictionary. */
class BrotliLoader {

  private static final String LINUX = "linux";
  private static final String WINDOWS = "windows";
  private static final String MAC_OS = "macos";

  // Maps arch path in jar to os.arch aliases
  private static final Map<String, Set<String>> ARCH_PATHS =
      Map.of(
          "x86", Set.of("x86", "i386", "i486", "i586", "i686"),
          "x86-64" /* '-' and not '_' is used in arch path */, Set.of("x86_64", "amd64"));

  static final String BASE_LIB_NAME = "brotlijni";
  static final String ENTRY_DIR_PREFIX = BrotliLoader.class.getName() + "-";
  private static final String LIB_ROOT = "native";
  private static final String DICT_PATH = "/data/dictionary.bin";

  public static final int BROTLI_DICT_SIZE = 122784;
  private static final byte[] BROTLI_DICT_SHA_256 =
      new byte[] {
        32, -28, 46, -79, -75, 17, -62, 24, 6, -44, -46, 39, -48, 126, 93, -48,
        104, 119, -40, -50, 123, 58, -127, 127, 55, -113, 49, 54, 83, -13, 92, 112
      };

  private static final Logger LOGGER = Logger.getLogger(BrotliLoader.class.getName());

  private static volatile boolean loaded;

  static void ensureLoaded() throws IOException {
    if (!loaded) {
      synchronized (BrotliLoader.class) {
        if (!loaded) {
          ByteBuffer dictData = loadBrotliDict();
          LibEntry entry = extractLib();
          entry.deleteOnExit();
          entry.loadLib();
          if (!CommonJNI.nativeSetDictionaryData(dictData)) {
            throw new IOException("failed to set brotli dictionary");
          }
          loaded = true; // Mission accomplished!
        }
      }
    }
  }

  static LibEntry extractLib() throws IOException {
    String libName = System.mapLibraryName(BASE_LIB_NAME);
    String libPath = findLibPath(libName);
    URL libUrl = BrotliLoader.class.getResource(libPath);
    if (libUrl == null) {
      throw new FileNotFoundException("couldn't find brotli jni library: " + libPath);
    }
    Path tempDir = getTempDir();
    cleanStaleEntries(tempDir, libName);
    return createLibEntry(tempDir, libName, libUrl);
  }

  private static void cleanStaleEntries(Path tempDir, String libName) {
    try (Stream<Path> entryDirs =
        Files.list(tempDir).filter(p -> p.getFileName().toString().startsWith(ENTRY_DIR_PREFIX))) {
      entryDirs.forEach(
          dir -> {
            LibEntry entry = new LibEntry(dir, libName);
            if (entry.isStale()) {
              try {
                entry.deleteIfExists();
              } catch (IOException ioe) {
                LOGGER.log(Level.WARNING, "couldn't delete stale entry: " + dir, ioe);
              }
            }
          });
    } catch (IOException ioe) {
      LOGGER.log(Level.WARNING, "couldn't perform cleanup routine for stale entries", ioe);
    }
  }

  private static LibEntry createLibEntry(Path tempDir, String libName, URL libUrl)
      throws IOException {
    Path entryDir = Files.createTempDirectory(tempDir, ENTRY_DIR_PREFIX);
    LibEntry entry = new LibEntry(entryDir, libName);
    try (InputStream libIn = libUrl.openStream()) {
      entry.create(libIn);
    } catch (IOException ioe) {
      try {
        entry.deleteIfExists();
      } catch (IOException suppressed) {
        ioe.addSuppressed(suppressed);
      }
      throw ioe;
    }
    return entry;
  }

  private static Path getTempDir() {
    return Path.of(
        System.getProperty(
            "com.github.mizosoft.methanol.brotli.tmpdir", System.getProperty("java.io.tmpdir")));
  }

  private static String findLibPath(String libName) {
    String os = System.getProperty("os.name");
    String normalizedOs = normalizeOs(os.toLowerCase(Locale.ROOT));
    if (normalizedOs == null) {
      throw new UnsupportedOperationException("unrecognized OS: " + os);
    }
    String arch = System.getProperty("os.arch");
    String normalizedArch = normalizeArch(arch.toLowerCase(Locale.ROOT));
    if (normalizedArch == null) {
      throw new UnsupportedOperationException("unrecognized architecture: " + arch);
    }
    return String.format("/%s/%s/%s/%s", LIB_ROOT, normalizedOs, normalizedArch, libName);
  }

  private static @Nullable String normalizeOs(String os) {
    if (os.contains("linux")) {
      return LINUX;
    } else if (os.contains("windows")) {
      return WINDOWS;
    } else if (os.contains("mac os x") || os.contains("darwin") || os.contains("osx")) {
      return MAC_OS;
    }
    return null;
  }

  private static @Nullable String normalizeArch(String arch) {
    return ARCH_PATHS.entrySet().stream()
        .filter(e -> e.getValue().contains(arch))
        .findFirst()
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  private static ByteBuffer loadBrotliDict() throws IOException {
    InputStream dicIn = BrotliLoader.class.getResourceAsStream(DICT_PATH);
    if (dicIn == null) {
      throw new FileNotFoundException("couldn't find brotli dictionary: " + DICT_PATH);
    }
    MessageDigest dictDigest;
    try {
      dictDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("cannot digest brotli dictionary", e);
    }
    // buffer must be direct as the native address
    // is directory passed to BrotliSetDictionaryData from the jni side
    ByteBuffer dictData = ByteBuffer.allocateDirect(BROTLI_DICT_SIZE);
    try (dicIn) {
      int read;
      byte[] tempBuffer = new byte[4 * 1024];
      while ((read = dicIn.read(tempBuffer)) != -1) {
        dictData.put(tempBuffer, 0, read);
        dictDigest.update(tempBuffer, 0, read);
      }
    } catch (BufferOverflowException e) {
      throw new IOException("too large dictionary");
    }
    if (dictData.hasRemaining()) {
      throw new IOException("incomplete dictionary");
    }
    if (!Arrays.equals(dictDigest.digest(), BROTLI_DICT_SHA_256)) {
      throw new IOException("dictionary is corrupt");
    }
    return dictData;
  }

  /** Represents a temp entry for the extracted library and it's lock file. */
  private static final class LibEntry {

    private final Path dir;
    private final Path lockFile;
    private final Path libFile;

    LibEntry(Path dir, String libName) {
      this.dir = dir;
      lockFile = dir.resolve(libName + ".lock");
      libFile = dir.resolve(libName);
    }

    void create(InputStream libIn) throws IOException {
      // lockFile must be created first to not erroneously report staleness
      // to other instances in between libFile creation and lockFile creation
      Files.createFile(lockFile);
      Files.copy(libIn, libFile);
    }

    void deleteIfExists() throws IOException {
      Files.deleteIfExists(lockFile);
      Files.deleteIfExists(libFile);
      Files.deleteIfExists(dir);
    }

    void deleteOnExit() {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    try {
                      deleteIfExists();
                    } catch (IOException ignored) {
                      // An AccessDeniedException will ALWAYS be thrown
                      // in windows so it might be annoying to log it
                    }
                  }));
    }

    boolean isStale() {
      // Staleness is only reported on a "complete" entry (has both the lib file and
      // the lock file). This is because entry creation is not atomic, so care must
      // be taken to not delete an entry that is still under creation (still empty)
      return Files.notExists(lockFile) && Files.exists(libFile);
    }

    void loadLib() {
      System.load(libFile.toAbsolutePath().toString());
    }
  }
}
