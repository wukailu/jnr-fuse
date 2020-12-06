package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public interface writableObject<T> {
    // flush bits to memory and return endAddress; [,)
    int flush(ByteBuffer mem, int startAddress);
    T parse(ByteBuffer mem, int startAddress, int len);
}
