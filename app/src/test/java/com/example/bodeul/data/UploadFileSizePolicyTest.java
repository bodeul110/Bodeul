package com.example.bodeul.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 업로드 크기 판정이 메타데이터 없는 파일도 앱 단에서 차단하는지 검증한다.
 */
public class UploadFileSizePolicyTest {
    @Test
    public void fromKnownSize_allowsFileAtLimit() {
        UploadFileSizePolicy.Result result = UploadFileSizePolicy.fromKnownSize(10L, 10L);

        assertEquals(UploadFileSizePolicy.Status.ALLOWED, result.getStatus());
        assertEquals(10L, result.getFileSizeBytes());
    }

    @Test
    public void fromKnownSize_blocksFileOverLimit() {
        UploadFileSizePolicy.Result result = UploadFileSizePolicy.fromKnownSize(11L, 10L);

        assertEquals(UploadFileSizePolicy.Status.TOO_LARGE, result.getStatus());
        assertEquals(11L, result.getFileSizeBytes());
    }

    @Test
    public void fromKnownSize_marksNegativeSizeAsUnknown() {
        UploadFileSizePolicy.Result result = UploadFileSizePolicy.fromKnownSize(-1L, 10L);

        assertEquals(UploadFileSizePolicy.Status.UNKNOWN, result.getStatus());
    }

    @Test
    public void inspectStream_allowsSmallUnknownMetadataFile() {
        byte[] fileBytes = new byte[] {1, 2, 3, 4};

        UploadFileSizePolicy.Result result = UploadFileSizePolicy.inspectStream(
                new ByteArrayInputStream(fileBytes),
                10L
        );

        assertEquals(UploadFileSizePolicy.Status.ALLOWED, result.getStatus());
        assertEquals(4L, result.getFileSizeBytes());
    }

    @Test
    public void inspectStream_blocksOverLimitBeforeReadingWholeFile() {
        CountingInputStream inputStream = new CountingInputStream(100L);

        UploadFileSizePolicy.Result result = UploadFileSizePolicy.inspectStream(inputStream, 10L);

        assertEquals(UploadFileSizePolicy.Status.TOO_LARGE, result.getStatus());
        assertEquals(11L, result.getFileSizeBytes());
        assertEquals(11L, inputStream.getReadBytes());
    }

    @Test
    public void inspectStream_marksReadFailureAsUnknown() {
        UploadFileSizePolicy.Result result = UploadFileSizePolicy.inspectStream(
                new FailingInputStream(),
                10L
        );

        assertEquals(UploadFileSizePolicy.Status.UNKNOWN, result.getStatus());
    }

    private static final class CountingInputStream extends InputStream {
        private final long totalBytes;
        private long readBytes;

        CountingInputStream(long totalBytes) {
            this.totalBytes = totalBytes;
        }

        long getReadBytes() {
            return readBytes;
        }

        @Override
        public int read() {
            if (readBytes >= totalBytes) {
                return -1;
            }
            readBytes++;
            return 1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (readBytes >= totalBytes) {
                return -1;
            }

            int readableBytes = (int) Math.min(length, totalBytes - readBytes);
            for (int index = 0; index < readableBytes; index++) {
                buffer[offset + index] = 1;
            }
            readBytes += readableBytes;
            return readableBytes;
        }
    }

    private static final class FailingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("read failed");
        }
    }
}
