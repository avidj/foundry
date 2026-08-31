// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author david
 */
public class StringWriteAheadLog implements WriteAheadLog {
  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private static final DateTimeFormatter DATE_FORMAT =
    DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss-SSS");
  static final String LOG_SUFFIX = ".wal.log";
  static final String SNAPSHOT_SUFFIX = ".wal.snap";
  private static final char TOMBSTONE = '\0';
  private static final char DELIM = ':';
  private static final char NL = '\n';
  private final String walDir;
  private FileChannel channel;
  private boolean recovered = false;
  private String currentLogFile;
  private final Object mutex = new Object();
  private boolean rotating = false;

  public StringWriteAheadLog(String walDir) {
    this.walDir = walDir;
  }

  @Override
  public Map<String, String> recover() throws IOException {
    synchronized ( mutex ) {
      String snapshot = determineLatestSnapshotFileName();
      String logFile = determineLogFileName();
      Map<String, String> memTable = recoverFrom(snapshot, logFile);
      channel = openLogFile(logFile);
      recovered = true;
      return memTable;
    }
  }

  private Map<String, String> recoverFrom(
          String snapshot, String logFile) throws IOException {
    Map<String, String> memTable = new HashMap<>();
    redo(memTable, snapshot);
    redo(memTable, logFile);
    return memTable;
  }

  private void redo(Map<String, String> memTable, String fileName) throws IOException {
    if ( fileName == null ) {
      return;
    }
    File file = new File(fileName);
    if ( !file.exists() ) {
      return;
    }
    String line = null;
    StringBuilder token;
    String key;
    String value;
    try ( BufferedReader r = new BufferedReader(new FileReader(file, CHARSET)) ) {
      while ( ( line = r.readLine() ) != null ) {
        token = new StringBuilder();
        int i = 0;
        char[] chars = line.toCharArray();
        i = readToken(token, chars, i);
        key = token.toString();
        token = new StringBuilder();
        i = readToken(token, chars, i);
        if ( i == -1 ) {
          memTable.remove(key);
        } else {
          assert(i == chars.length);
          value = token.toString();
          memTable.put(key, value);
        }
      }
    }
  }

  private int readToken(StringBuilder token, char[] chars, int i) {
    boolean escaped = false;
    char c = '\0';
    for ( ; i < chars.length; i++ ) {
      c = chars[i];
      if ( escaped ) {
        if ( c == 'n' ) { token.append('\n'); }
        else if ( c == '\\' ) { token.append('\\'); }
        else if ( c == ':' ) { token.append(c); }
        escaped = false;
      } else {
        if ( c == '\\' ) { escaped = true; continue; }
        if ( c == ':' ) { return i + 1; }
        if ( c == '\0' ) { return -1; }
        token.append(c);
      }
    }
    return i;
  }

  @Override
  public void rotate() throws IOException {
    String oldLogFile = null;
    String oldSnapshot = null;
    synchronized ( mutex ) {
      if ( rotating ) {
        return;
      }
      checkIsOpen();
      rotating = true;
      try {
        oldLogFile = currentLogFile;
        oldSnapshot = this.determineLatestSnapshotFileName();
        closeLogFile();
        openLogFile(this.newLogFileName());
      } catch ( IOException e ) {
        rotating = false;
        throw e;
      }
    }
    Map<String, String> snapshot = recoverFrom(oldSnapshot, oldLogFile);
    final String snapshotFileName = newSnapshotFileName();
    try ( FileChannel snapChannel = FileChannel.open(
            Paths.get(snapshotFileName),
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
            StandardOpenOption.CREATE) ) {
      for ( Map.Entry<String, String> entry : snapshot.entrySet() ) {
        appendTo(entry.getKey(), entry.getValue(), snapChannel);
      }
      snapChannel.force(true);

      if (oldLogFile != null) {
        new File(oldLogFile).delete();
      }
      if (oldSnapshot != null) {
        new File(oldSnapshot).delete();
      }
    } finally {
      synchronized ( mutex ) {
        rotating = false;
      }
    }
  }

  @Override
  public void close() throws IOException {
    synchronized ( mutex ) {
      if ( !recovered ) {
        return;
      }
      channel.close();
      recovered = false;
    }
  }

  private FileChannel openLogFile(String path) throws IOException {
    this.currentLogFile = path;
    channel = FileChannel.open(
            Paths.get(path),
            StandardOpenOption.WRITE,   // open for write
            StandardOpenOption.APPEND,  // append on write
            StandardOpenOption.CREATE); // create if not exists
    return channel;
  }

  private void closeLogFile() throws IOException {
    this.channel.close();
    this.channel = null;
  }

  private ByteBuffer escape(String s, ByteBuffer buf) {
    for ( char c : s.toCharArray() ) {
      if ( c == '\n' ) {
        buf.put((byte)'\\');
        buf.put((byte)'n');
        continue;
      }
      if ( c == '\\' || c == ':') {
        buf.put((byte)'\\');
      }
      buf.put((byte)c);
    }
    return buf;
  }

  private ByteBuffer encode(String key, String value) {
    int len = value != null ?
            (key.length() + value.length()) * 2 + 2 :
            key.length() * 2 + 3;
    ByteBuffer buf = ByteBuffer.allocate(len);
    escape(key, buf);
    buf.put((byte)DELIM);
    if ( value != null ) {
      escape(value, buf);
    } else {
      buf.put((byte)TOMBSTONE);
    }
    buf.put((byte)NL);
    buf.flip();
    return buf;
  }

  @Override
  public void append(String key, String value) throws IOException {
    synchronized ( mutex ) {
      appendTo(key, value, channel);
      channel.force(true);
    }
  }

  @Override
  public void delete(String key) throws IOException {
    synchronized ( mutex ) {
      appendTo(key, null, channel);
      channel.force(true);
    }
  }

  private void appendTo(String key, String value, FileChannel channel) throws IOException {
    checkIsOpen();
    channel.write(encode(key, value));
  }

  private void checkIsOpen() throws IllegalStateException {
    if ( !recovered ) {
      throw new IllegalStateException("WAL is closed, must recover()");
    }
  }

  private String newLogFileName() {
    LocalDateTime now = LocalDateTime.now();
    return walDir + File.separator + DATE_FORMAT.format(now) + LOG_SUFFIX;
  }

  @Override
  public String determineLogFileName() {
    FilenameFilter filter = (dir, name) -> name.endsWith(LOG_SUFFIX);
    File dir = new File(walDir);
    String[] files = dir.list(filter);
    if ( files.length == 0 ) {
      return newLogFileName();
    }
    Arrays.sort(files);
    return walDir + File.separator + files[files.length - 1];
  }

  private String newSnapshotFileName() {
    LocalDateTime now = LocalDateTime.now();
    return walDir + File.separator + DATE_FORMAT.format(now) + SNAPSHOT_SUFFIX;
  }

  private String determineLatestSnapshotFileName() throws IOException {
    FilenameFilter filter = (dir, name) -> name.endsWith(SNAPSHOT_SUFFIX);
    File dir = new File(walDir);
    String[] files = dir.list(filter);
    if ( files.length == 0 ) {
      return null;
    }
    Arrays.sort(files);
    return walDir + File.separator + files[files.length - 1];
  }
}
