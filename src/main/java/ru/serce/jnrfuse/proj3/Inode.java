package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Inode implements writableObject<Inode> {
    public int id;
    public int uid;
    public int gid;
    public int controlBit;
    public long lastAccessTime;
    public long lastModifyTime;
    public long lastChangeTime;
    public long createTime;
    public int space; // occupied space
    public int size; // exact size
    public int fileType;
    public int[] hardLinks;
    public int[] dataBlocks;
    public int[] lv1Block;
    public int[] lv2Block;



    Inode(){
        id = 0;
        uid = 0;
        gid = 0;
        controlBit = 0;
        lastAccessTime = System.currentTimeMillis();
        lastModifyTime = System.currentTimeMillis();
        createTime = System.currentTimeMillis();
        space = 0;
        size = 0;
        fileType = 0;
        hardLinks = new int[0];
        dataBlocks = new int[12];
        lv1Block = new int[1];
        lv2Block = new int[2];
    }

    @Override
    public int flush(ByteBuffer mem, int startAddress) {
        mem.position(startAddress);
        mem.putInt(id);
        mem.putInt(uid);
        mem.putInt(gid);
        mem.putInt(controlBit);
        mem.putLong(lastAccessTime);
        mem.putLong(lastModifyTime);
        mem.putLong(lastChangeTime);
        mem.putLong(createTime);
        mem.putInt(space);
        mem.putInt(size);
        mem.putInt(fileType);
        mem.putInt(hardLinks.length);
        for(int i: hardLinks) mem.putInt(i);
        mem.position(startAddress+1024-15*4);
        for (int i: dataBlocks) mem.putInt(i);
        for (int i: lv1Block) mem.putInt(i);
        for (int i: lv2Block) mem.putInt(i);
        return mem.position();
    }

    @Override
    public Inode parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        assert len == 1024;
        id = mem.getInt();
        uid = mem.getInt();
        gid = mem.getInt();
        controlBit = mem.getInt();
        lastAccessTime = mem.getLong();
        lastModifyTime = mem.getLong();
        lastChangeTime = mem.getLong();
        createTime = mem.getLong();
        space = mem.getInt();
        size = mem.getInt();
        fileType = mem.getInt();
        hardLinks = new int[mem.getInt()];
        for (int i = 0; i < hardLinks.length; i++)
            hardLinks[i] = mem.getInt();
        mem.position(startAddress+1024-15*4);
        for (int i = 0; i < dataBlocks.length; i++)
            dataBlocks[i] = mem.getInt();
        for (int i = 0; i < lv1Block.length; i++)
            lv1Block[i] = mem.getInt();
        for (int i = 0; i < lv2Block.length; i++)
            lv2Block[i] = mem.getInt();
        return this;
    }

    public ByteBuffer read(ByteBuffer mem, int startAddress, int len){
        assert len > 0;
        ByteBuffer ret = ByteBuffer.allocate(len);

        if (startAddress < dataBlocks.length * 1024){
            ByteBuffer data = LV1IndirectBlock.readBlocks(mem, startAddress, len, dataBlocks);
            len -= data.remaining();
            startAddress = 0;
            ret.put(data);
        }else{
            startAddress -= dataBlocks.length * 1024;
        }

        for (int lv1: lv1Block){
            if(lv1 != 0 && len > 0){
                if (startAddress < 1024 * 256){
                    ByteBuffer data = new LV1IndirectBlock().parse(mem, lv1, 1024).read(mem, startAddress, len);
                    len -= data.capacity();
                    startAddress = 0;
                    ret.put(data);
                }else {
                    startAddress -= 1024 * 256;
                }
            }
        }

        for (int lv2: lv2Block){
            if(lv2 != 0 && len > 0){
                if (startAddress < 1024 * 256 * 256){
                    ByteBuffer data = new LV2IndirectBlock().parse(mem, lv2, 1024).read(mem, startAddress, len);
                    len -= data.capacity();
                    startAddress = 0;
                    ret.put(data);
                }else {
                    startAddress -= 1024 * 256 * 256;
                }
            }
        }

        ret.flip();
        return ret;
    }


    public void write(ByteBuffer data, ByteBuffer mem, int startAddress, int len){
        assert len > 0;
        assert startAddress < size;
        size = Math.max(size, startAddress + len);
        space = size & 0xfffffc00;
        space = (space < size) ? space + 1024: space;

        if (startAddress < dataBlocks.length * 1024){
            LV1IndirectBlock.writeBlocks(data, mem, startAddress, len, dataBlocks);
            len -= Math.min(len, dataBlocks.length * 1024 - startAddress);
            startAddress = 0;
        }else{
            startAddress -= dataBlocks.length * 1024;
        }

        for (int i = 0; i < lv1Block.length; i++) {
            int lv1 = lv1Block[i];
            if(len > 0){
                if (startAddress < 1024 * 256){
                    LV1IndirectBlock indirectBlock = new LV1IndirectBlock();
                    if (lv1 != 0)
                        indirectBlock.parse(mem, lv1, 1024);
                    indirectBlock.write(data, mem, startAddress, len);
                    len -= Math.min(len, 1024 * 256 - startAddress);
                    startAddress = 0;
                    mem.reset();
                    lv1Block[i] = mem.position();
                    indirectBlock.flush(mem, mem.position());
                    mem.mark();
                }else {
                    startAddress -= 1024 * 256;
                }
            }
        }

        for (int i = 0; i < lv2Block.length; i++){
            int lv2 = lv2Block[i];
            if(len > 0){
                if (startAddress < 1024 * 256 * 256){
                    LV2IndirectBlock indirectBlock = new LV2IndirectBlock();
                    if (lv2 != 0)
                        indirectBlock.parse(mem, lv2, 1024);
                    indirectBlock.write(data, mem, startAddress, len);
                    len -= Math.min(len, 1024 * 256 - startAddress);
                    startAddress = 0;
                    mem.reset();
                    lv2Block[i] = mem.position();
                    indirectBlock.flush(mem, mem.position());
                    mem.mark();
                }else {
                    startAddress -= 1024 * 256 * 256;
                }
            }
        }

        lastAccessTime = System.currentTimeMillis();
        lastModifyTime = System.currentTimeMillis();
        lastChangeTime = System.currentTimeMillis();
    }


}
