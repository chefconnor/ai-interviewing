/**
 * A ring buffer implementation for storing byte data.
 * This is used to buffer audio data during speech detection.
 */
public class ByteRingBuffer {
    private byte[] buffer;
    private int capacity;
    private int writePosition = 0;
    private int readPosition = 0;
    private int available = 0;

    /**
     * Creates a new ByteRingBuffer with the specified capacity.
     *
     * @param capacity The maximum number of bytes the buffer can hold
     */
    public ByteRingBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new byte[capacity];
    }

    /**
     * Writes data to the buffer.
     *
     * @param data The array containing the data to write
     * @param offset The offset in the data array
     * @param length The number of bytes to write
     * @return The number of bytes written
     */
    public synchronized int write(byte[] data, int offset, int length) {
        if (length > capacity - available) {
            length = capacity - available;
        }

        // Write in two parts if the write wraps around the end of the buffer
        int firstPartLength = Math.min(length, capacity - writePosition);
        System.arraycopy(data, offset, buffer, writePosition, firstPartLength);

        if (firstPartLength < length) {
            // Write the second part at the beginning of the buffer
            System.arraycopy(data, offset + firstPartLength, buffer, 0, length - firstPartLength);
        }

        writePosition = (writePosition + length) % capacity;
        available += length;
        return length;
    }

    /**
     * Reads data from the buffer.
     *
     * @param data The array to read data into
     * @param offset The offset in the data array
     * @param length The maximum number of bytes to read
     * @return The number of bytes read
     */
    public synchronized int read(byte[] data, int offset, int length) {
        if (available == 0) {
            return 0;
        }

        if (length > available) {
            length = available;
        }

        // Read in two parts if the read wraps around the end of the buffer
        int firstPartLength = Math.min(length, capacity - readPosition);
        System.arraycopy(buffer, readPosition, data, offset, firstPartLength);

        if (firstPartLength < length) {
            // Read the second part from the beginning of the buffer
            System.arraycopy(buffer, 0, data, offset + firstPartLength, length - firstPartLength);
        }

        readPosition = (readPosition + length) % capacity;
        available -= length;
        return length;
    }

    /**
     * Reads all available data from the buffer.
     *
     * @return A new byte array containing all available data
     */
    public synchronized byte[] readAll() {
        byte[] result = new byte[available];
        read(result, 0, available);
        return result;
    }

    /**
     * Clears all data from the buffer.
     */
    public synchronized void clear() {
        readPosition = 0;
        writePosition = 0;
        available = 0;
    }

    /**
     * Gets the number of bytes available to read.
     *
     * @return The number of bytes available
     */
    public synchronized int available() {
        return available;
    }
}

