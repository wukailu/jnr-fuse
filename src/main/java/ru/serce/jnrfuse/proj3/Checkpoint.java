package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class Checkpoint extends writableObject<Checkpoint>{
    // TODO: YSY- add a free block bitmap. Now a Checkpoint has 12.5KB data.
    public int lastInodeMap; // point to next block of last inode map
    public BitSet free;
    public long beginTime;
    public long endTime;

    Checkpoint(){
        this.beginTime = System.currentTimeMillis();
        this.endTime = beginTime;
        this.lastInodeMap = 0;
    }

    public void updateLastInodeMap(int p)
    {
        beginTime = System.currentTimeMillis();
        endTime = beginTime;
        lastInodeMap = p;
    }

    public void updateFreeList(BitSet p)
    {
        beginTime = System.currentTimeMillis();
        endTime = beginTime;
        free = p;
    }

    @Override
    public ByteBuffer flush() {
        ByteBuffer mem = ByteBuffer.allocate(13 * 1024);
        mem.putLong(beginTime);
        mem.putInt(lastInodeMap);
        long[] f = free.toLongArray();
        for(long x : f)
            mem.putLong(x);
        mem.putLong(endTime);
        mem.position(0);
        return mem;
    }

    @Override
    public Checkpoint parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        beginTime = mem.getLong();
        lastInodeMap = mem.getInt();
        long[] f = new long[100 * 1024 / 64];
        for(int i = 0; i < f.length; i++)
            f[i] = mem.getLong();
        free = BitSet.valueOf(f);
        endTime = mem.getLong();
        return this;
    }

    public boolean valid(){
        return beginTime == endTime;
    }
}
