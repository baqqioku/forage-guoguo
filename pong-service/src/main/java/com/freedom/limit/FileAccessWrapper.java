package com.freedom.limit;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public interface FileAccessWrapper extends AutoCloseable {
    FileChannel getChannel() throws IOException;
    FileLock tryLock(long position, long size, boolean shared) throws IOException;
    long length() throws IOException;
    void seek(long pos) throws IOException;
    long readLong() throws IOException;
    int readInt() throws IOException;
    void writeLong(long v) throws IOException;
    void writeInt(int v) throws IOException;
    void close() throws IOException;
}
