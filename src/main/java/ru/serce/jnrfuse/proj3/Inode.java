package ru.serce.jnrfuse.proj3;

import ru.serce.jnrfuse.struct.FileStat;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Inode extends writableObject<Inode> {
    public int id;
    public int uid;
    public int gid;
    public long mode;
    public long lastAccessTime;
    public long lastModifyTime;
    public long lastChangeTime;
    public long createTime;
    public int space; // occupied space
    public int size; // exact size
    public int hardLinks;
    public int[] dataBlocks;
    public int[] lv1Block;
    public int[] lv2Block;



    Inode(int id){
        this.id = id;
        uid = 0;
        gid = 0;
        mode = 0777 | FileStat.S_IFREG;
        lastAccessTime = System.currentTimeMillis();
        lastModifyTime = System.currentTimeMillis();
        createTime = System.currentTimeMillis();
        space = 0;
        size = 0;
        hardLinks = 1;
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
        mem.putLong(mode);
        mem.putLong(lastAccessTime);
        mem.putLong(lastModifyTime);
        mem.putLong(lastChangeTime);
        mem.putLong(createTime);
        mem.putInt(space);
        mem.putInt(size);
        mem.putInt(hardLinks);
        mem.position(startAddress+1024-15*4);
        for (int i: dataBlocks) mem.putInt(i);
        for (int i: lv1Block) mem.putInt(i);
        for (int i: lv2Block) mem.putInt(i);
        return mem.position();
    }

    @Override
    public Inode parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        assert len >= 1024;
        id = mem.getInt();
        uid = mem.getInt();
        gid = mem.getInt();
        mode = mem.getLong();
        lastAccessTime = mem.getLong();
        lastModifyTime = mem.getLong();
        lastChangeTime = mem.getLong();
        createTime = mem.getLong();
        space = mem.getInt();
        size = mem.getInt();
        hardLinks = mem.getInt();
        mem.position(startAddress+1024-15*4);
        for (int i = 0; i < dataBlocks.length; i++)
            dataBlocks[i] = mem.getInt();
        for (int i = 0; i < lv1Block.length; i++)
            lv1Block[i] = mem.getInt();
        for (int i = 0; i < lv2Block.length; i++)
            lv2Block[i] = mem.getInt();
        return this;
    }

    /***
     * @param mem gloable memory
     * @param startAddress Start address relative to the file head. 0 is the head of the file.
     *                     Should be smaller than file size.
     * @param len The size of bytes to read.
     * @return Return a ByteBuffer containing required data.
     */
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

    /***
     * @param data  Data to write, start from the data.position().
     * @param mem   gloable memory
     * @param startAddress  Start address relative to the file head. 0 is the head of the file.
     *                      Should be smaller than file size.
     * @param len   The size of bytes to write, started from data.position().
     */
    public void write(ByteBuffer data, ByteBuffer mem, int startAddress, int len){
        assert len > 0;
        assert startAddress < size;
        updateSize(Math.max(size, startAddress + len));
        mem.reset();

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
                    if (lv1 != 0){
                        int mark = mem.reset().position();
                        indirectBlock.parse(mem, lv1, 1024);
                        mem.position(mark).mark();
                    }
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
                    if (lv2 != 0){
                        int mark = mem.reset().position();
                        indirectBlock.parse(mem, lv2, 1024);
                        mem.position(mark).mark();
                    }
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

    public void updateSize(int newSize){
        if (newSize != size){
            size = newSize;
            space = size & 0xfffffc00;
            space = (space < size) ? space + 1024: space;
        }
    }

    public static Handler Identity = new IdentityHandler();

    public abstract static class Handler{
        abstract Inode process(Inode inode);
    }

    public static Handler Sequential(Handler... handlers){
        return new SequentialHandler(handlers);
    }

    public static Handler SetMode(int mode){
        return new SetModeHandler(mode);
    }

    public static Handler SetOwner(int uid, int gid){
        return new SetOwnerHandler(uid, gid);
    }

    public static class SequentialHandler extends Handler{
        private final Handler[] handlers;
        SequentialHandler(Handler... handlers){
            this.handlers = handlers;
        }

        @Override
        Inode process(Inode inode) {
            for(Handler handler: handlers)
                inode = handler.process(inode);
            return inode;
        }
    }

    public static class SetModeHandler extends Handler{
        private final int mode;
        SetModeHandler(int mode){
            this.mode = mode;
        }

        @Override
        Inode process(Inode inode) {
            inode.mode = mode;
            return inode;
        }
    }

    public static class SetOwnerHandler extends Handler{
        private final int uid, gid;
        SetOwnerHandler(int uid, int gid){
            this.uid = uid;
            this.gid = gid;
        }

        @Override
        Inode process(Inode inode) {
            inode.uid = uid;
            inode.gid = gid;
            return inode;
        }
    }

    public static class IdentityHandler extends Handler{
        @Override
        Inode process(Inode inode) {
            return inode;
        }
    }

}
