package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class DataBlock extends writableObject<DataBlock>{
    public byte[] data;

    DataBlock(){
        data = new byte[1024];
    }

    @Override
    public ByteBuffer flush() {
        return ByteBuffer.wrap(data);
    }

    @Override
    public DataBlock parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        mem.get(data, 0, len);
        return this;
    }
}
