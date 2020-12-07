package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class DataBlock extends writableObject<DataBlock>{
    public byte[] data;

    DataBlock(ByteBuffer mem, int startAddress, int len){
        data = new byte[1024];
        mem.position(startAddress);
        mem.get(data, 0, len);
    }

    @Override
    public int flush(ByteBuffer mem, int startAddress) {
        mem.position(startAddress);
        mem.put(data);
        return 0;
    }

    @Override
    public DataBlock parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        mem.get(data);
        return this;
    }
}
