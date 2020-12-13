package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class LV1IndirectBlock extends writableObject<LV1IndirectBlock> {
    public int[] subBlocks;
    public int subBlockLen;

    LV1IndirectBlock(){
        subBlocks = new int[256];
        subBlockLen = 0;
    }

    @Override
    public ByteBuffer flush() {
        ByteBuffer mem = ByteBuffer.allocate(1024);
        for (int i: subBlocks)
            mem.putInt(i);
        mem.position(0);
        return mem;
    }

    @Override
    public LV1IndirectBlock parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        for (int i = 0; i < subBlocks.length; i++){
            subBlocks[i] = mem.getInt();
            if (subBlocks[i] != 0)
                subBlockLen = i+1;
        }
        return this;
    }

    public ByteBuffer read(LogFS.MemoryManager manager, int startAddress, int len){
        return readBlocks(manager, startAddress, len, subBlocks);
    }

    public void write(ByteBuffer data, LogFS.MemoryManager manager, int startAddress, int len){
        if (len <= 0) return;
        writeBlocks(data, manager, startAddress, len, subBlocks);
        for (int i = 0; i < subBlocks.length; i++)
            if (subBlocks[i] != 0)
                subBlockLen = i+1;
    }

    // TODO: this part can be speed up
    public static ByteBuffer readBlocks(LogFS.MemoryManager manager, int startAddress, int len, int[] blocks){
        assert len > 0;
        ByteBuffer ret = ByteBuffer.allocate(len);
        for (int block : blocks) {
            if (block != 0) {
                if (startAddress >= 1024)
                    startAddress -= 1024;
                else {
                    ret.put(manager.read(block + startAddress, Math.min(len, 1024 - startAddress)).array());
                    len -= Math.min(len, 1024 - startAddress);
                    startAddress = 0;
                }
                if (len == 0)
                    break;
            }
        }
        ret.flip();
        return ret;
    }

    public static void writeBlocks(ByteBuffer data, LogFS.MemoryManager manager, int startAddress, int len, int[] blocks){
        if(len == 0)
            return;
        for (int i = 0; i < blocks.length; i++) {
            int block = blocks[i];
            if (startAddress >= 1024)
                startAddress -= 1024;
            else {
                DataBlock d;
                if(startAddress != 0){
                    if (block != 0)
                        d = new DataBlock().parse(manager.read(block, 1024));
                    else
                        d = new DataBlock();
                    data.get(d.data, startAddress, Math.min(len, 1024 - startAddress));
                }else {
                    d = new DataBlock().parse(data, data.position(), Math.min(1024, len));
                }

                blocks[i] = manager.write(d.flush());

                len -= Math.min(len, 1024 - startAddress);
                startAddress = 0;
            }
            if (len == 0)
                break;
        }
    }

    public int getSize(ByteBuffer mem){
        return subBlockLen * 1024;
    }
}