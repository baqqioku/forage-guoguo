package com.freedom.limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class PongRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(PongRateLimiter.class);
    private static final String LOCK_FILE = "/data/pong_rate_limit.lock";

    private final int maxRequestsPerSecond;
    private final ReentrantLock threadLock = new ReentrantLock();

    public PongRateLimiter(PongProperties pingProperties) {
        this.maxRequestsPerSecond = pingProperties.getLimitQps();
    }

    public boolean tryAcquire() {
        long currentTime = System.currentTimeMillis() / 1000;

        threadLock.lock();
        try {
            boolean result = doTryAcquire(currentTime);
            log.info("Rate limiter acquire result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Unexpected error in tryAcquire", e);
            return false;
        } finally {
            threadLock.unlock();
        }
    }

    private boolean doTryAcquire(long currentTime) {
        try (FileAccessWrapper stateFile = createFileAccess()) {
            FileLock lock = stateFile.tryLock(0, Long.MAX_VALUE, false);
            if (lock == null) {
                log.info("Could not acquire lock, another process is holding it.");
                return false;
            }

            try {
                long lastTimeStamp = 0;
                int requestCount = 0;

                if (stateFile.length() > 0) {
                    stateFile.seek(0);
                    lastTimeStamp = stateFile.readLong();
                    requestCount = stateFile.readInt();
                }

                if (currentTime == lastTimeStamp) {
                    if (requestCount >= maxRequestsPerSecond) {
                        log.info("Rate limit reached for second: {}", currentTime);
                        return false;
                    } else {
                        requestCount++;
                    }
                } else {
                    lastTimeStamp = currentTime;
                    requestCount = 1;
                }

                stateFile.seek(0);
                stateFile.writeLong(lastTimeStamp);
                stateFile.writeInt(requestCount);

                log.info("Request allowed for second: {}, count: {}", currentTime, requestCount);
                return true;
            } finally {
                lock.release();
            }
        } catch (IOException e) {
            log.error("Error accessing rate limit file", e);
            return false;
        }
    }

    protected FileAccessWrapper createFileAccess() throws IOException {
        return new RandomAccessFileWrapper(LOCK_FILE, "rw");
    }

    private static class RandomAccessFileWrapper implements FileAccessWrapper {
        private final RandomAccessFile file;
        private final FileChannel channel;

        public RandomAccessFileWrapper(String name, String mode) throws IOException {
            this.file = new RandomAccessFile(name, mode);
            this.channel = file.getChannel();
        }

        @Override
        public FileChannel getChannel() {
            return channel;
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return channel.tryLock(position, size, shared);
        }

        @Override
        public long length() throws IOException {
            return file.length();
        }

        @Override
        public void seek(long pos) throws IOException {
            file.seek(pos);
        }

        @Override
        public long readLong() throws IOException {
            return file.readLong();
        }

        @Override
        public int readInt() throws IOException {
            return file.readInt();
        }

        @Override
        public void writeLong(long v) throws IOException {
            file.writeLong(v);
        }

        @Override
        public void writeInt(int v) throws IOException {
            file.writeInt(v);
        }

        @Override
        public void close() throws IOException {
            channel.close();
            file.close();
        }
    }
}
