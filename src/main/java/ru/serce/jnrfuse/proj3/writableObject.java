package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public abstract class writableObject<T> {
    // flush bits to memory and return endAddress; [,)
    public abstract ByteBuffer flush();
    public abstract T parse(ByteBuffer mem, int startAddress, int len);
    public T parse(ByteBuffer mem){
        return parse(mem, mem.position(), mem.remaining());
    }
}
