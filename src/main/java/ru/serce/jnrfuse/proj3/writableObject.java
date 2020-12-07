package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public abstract class writableObject<T> {
    // flush bits to memory and return endAddress; [,)
    public abstract int flush(ByteBuffer mem, int startAddress);
    public int flush(ByteBuffer mem){
        return flush(mem, mem.position());
    }
    public abstract T parse(ByteBuffer mem, int startAddress, int len);
    public T parse(ByteBuffer mem){
        return parse(mem, mem.position(), mem.remaining());
    }
}
