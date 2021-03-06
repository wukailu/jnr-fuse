package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class InodeMap extends writableObject<InodeMap>{
    public Map<Integer, Integer> inodeMap;
    public int preInodeMapAddress;

    InodeMap(){
        inodeMap = new HashMap<Integer, Integer>();
        preInodeMapAddress = -1;
    }

    public int size()
    {
        return inodeMap.size();
    }

    @Override
    public ByteBuffer flush() {
        ByteBuffer mem = ByteBuffer.allocate(1024);
        mem.putInt(preInodeMapAddress);
        mem.putInt(0);
        for (Map.Entry<Integer, Integer> entry : inodeMap.entrySet()) {
            mem.putInt(entry.getKey());
            mem.putInt(entry.getValue());
        }
        mem.position(0);
        return mem;
    }

    @Override
    public InodeMap parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        preInodeMapAddress = mem.getInt();
        mem.getInt();
        for (int i = 8; i < len; i+=8)
            inodeMap.put(mem.getInt(), mem.getInt());
        return this;
    }
}
