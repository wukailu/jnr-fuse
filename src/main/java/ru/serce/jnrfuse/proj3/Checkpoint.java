package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class Checkpoint implements writableObject<Checkpoint>{
    public int lastInodeMap; // point to next block of last inode map
    public long beginTime;
    public long endTime;

    Checkpoint(){
        this.beginTime = System.currentTimeMillis();
        this.endTime = System.currentTimeMillis();
        this.lastInodeMap = 0;
    }

    @Override
    public int flush(ByteBuffer mem, int startAddress) {
        mem.position(startAddress);
        mem.putInt(lastInodeMap);
        mem.putLong(beginTime);
        mem.putLong(endTime);
        return mem.position();
    }

    @Override
    public Checkpoint parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        lastInodeMap = mem.getInt();
        beginTime = mem.getLong();
        endTime = mem.getLong();
        return this;
    }

    public boolean valid(){
        return beginTime < endTime;
    }
}
