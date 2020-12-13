package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class Checkpoint extends writableObject<Checkpoint>{
    public int lastInodeMap; // point to next block of last inode map
    public long beginTime;
    public long endTime;

    Checkpoint(){
        this.beginTime = System.currentTimeMillis();
        this.endTime = beginTime;
        this.lastInodeMap = 0;
    }

    public void update(int p)
    {
        beginTime = System.currentTimeMillis();
        endTime = beginTime;
        lastInodeMap = p;
    }

    @Override
    public ByteBuffer flush() {
        ByteBuffer mem = ByteBuffer.allocate(1024);
        mem.putLong(beginTime);
        mem.putInt(lastInodeMap);
        mem.putLong(endTime);
        mem.position(0);
        return mem;
    }

    @Override
    public Checkpoint parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        beginTime = mem.getLong();
        lastInodeMap = mem.getInt();
        endTime = mem.getLong();
        return this;
    }

    public boolean valid(){
        return beginTime == endTime;
    }
}
