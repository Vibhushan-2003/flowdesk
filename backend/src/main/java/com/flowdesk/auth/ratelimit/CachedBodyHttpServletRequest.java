package com.flowdesk.auth.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Caches the request body in memory up to a strictly bounded maximum size.
 * If the body exceeds maxBytes, throws PayloadTooLargeException to prevent memory exhaustion.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        this(request, 8192);
    }

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBytes) throws IOException {
        super(request);
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            throw new PayloadTooLargeException("Request payload exceeds maximum allowed size of " + maxBytes + " bytes");
        }
        String headerLength = request.getHeader("Content-Length");
        if (headerLength != null && !headerLength.isBlank()) {
            try {
                long parsed = Long.parseLong(headerLength.trim());
                if (parsed > maxBytes) {
                    throw new PayloadTooLargeException("Request payload exceeds maximum allowed size of " + maxBytes + " bytes");
                }
            } catch (NumberFormatException ignored) {
            }
        }
        ServletInputStream inputStream = request.getInputStream();
        if (inputStream != null) {
            this.cachedBody = readLimitedBytes(inputStream, maxBytes);
        } else {
            this.cachedBody = new byte[0];
        }
    }

    private static byte[] readLimitedBytes(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int totalRead = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            totalRead += read;
            if (totalRead > maxBytes) {
                throw new PayloadTooLargeException("Request payload exceeds maximum allowed size of " + maxBytes + " bytes");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        return new CachedServletInputStream(byteArrayInputStream);
    }

    @Override
    public BufferedReader getReader() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
    }

    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        CachedServletInputStream(ByteArrayInputStream buffer) {
            this.buffer = buffer;
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return buffer.read(b, off, len);
        }
    }

    public static class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException(String message) {
            super(message);
        }
    }
}
