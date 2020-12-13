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
    public ByteBuffer flush() {
        ByteBuffer mem = ByteBuffer.allocate(1024);
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
        mem.position(1024-15*4);
        for (int i: dataBlocks) mem.putInt(i);
        for (int i: lv1Block) mem.putInt(i);
        for (int i: lv2Block) mem.putInt(i);
        mem.position(0);
        return mem;
    }

    @Override
    public Inode parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
//        assert len >= 1024;
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

    public void clearData(){
        Arrays.fill(dataBlocks, 0);
        Arrays.fill(lv1Block, 0);
        Arrays.fill(lv2Block, 0);
        updateSize(0);
    }

    /***
     * @param manager gloable memory manager
     * @param startAddress Start address relative to the file head. 0 is the head of the file.
     *                     Should be smaller than file size.
     * @param len The size of bytes to read.
     * @return Return a ByteBuffer containing required data.
     */
    public ByteBuffer read(LogFS.MemoryManager manager, int startAddress, int len){
        if (startAddress > size)
            return ByteBuffer.allocate(len);
        ByteBuffer ret = ByteBuffer.allocate(len);

        if (startAddress < dataBlocks.length * 1024){
            ByteBuffer data = LV1IndirectBlock.readBlocks(manager, startAddress, len, dataBlocks);
            len -= data.remaining();
            startAddress = 0;
            ret.put(data);
        }else{
            startAddress -= dataBlocks.length * 1024;
        }

        if (startAddress < lv1Block.length * 1024 * 256){
            ByteBuffer data = LV2IndirectBlock.readLv1Blocks(manager, startAddress, len, lv1Block);
            len -= data.remaining();
            startAddress = 0;
            ret.put(data);
        }else{
            startAddress -= lv1Block.length * 1024 * 256;
        }

        for (int lv2: lv2Block){
            if(lv2 != 0 && len > 0){
                if (startAddress < 1024 * 256 * 256){
                    ByteBuffer data = new LV2IndirectBlock().parse(manager.read(lv2, 1024)).read(manager, startAddress, len);
                    len -= data.remaining();
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
     * @param manager   gloable memory manager
     * @param startAddress  Start address relative to the file head. 0 is the head of the file.
     *                      Should be smaller than file size.
     * @param len   The size of bytes to write, started from data.position().
     */
    public void write(ByteBuffer data, LogFS.MemoryManager manager, int startAddress, int len){
        if (startAddress > size)
            write(ByteBuffer.allocate(startAddress-size), manager, size, startAddress-size);

        updateSize(Math.max(size, startAddress + len));

        if (startAddress < dataBlocks.length * 1024){
            LV1IndirectBlock.writeBlocks(data, manager, startAddress, len, dataBlocks);
            len -= Math.min(len, dataBlocks.length * 1024 - startAddress);
            startAddress = 0;
        }else{
            startAddress -= dataBlocks.length * 1024;
        }

        if (startAddress < lv1Block.length * 1024 * 256){
            LV2IndirectBlock.writeLv1Blocks(data, manager, startAddress, len, lv1Block);
            len -= Math.min(len, lv1Block.length * 1024 * 256 - startAddress);
            startAddress = 0;
        }else{
            startAddress -= lv1Block.length * 1024 * 256;
        }

        for (int i = 0; i < lv2Block.length; i++){
            int lv2 = lv2Block[i];
            if(len > 0){
                if (startAddress < 1024 * 256 * 256){
                    LV2IndirectBlock indirectBlock = new LV2IndirectBlock();
                    if (lv2 != 0){
                        indirectBlock.parse(manager.read(lv2, 1024));
                    }
                    indirectBlock.write(data, manager, startAddress, len);
                    len -= Math.min(len, 1024 * 256 * 256 - startAddress);
                    startAddress = 0;
                    lv2Block[i] = manager.write(indirectBlock.flush());
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

    public static Handler SetTimestamp(String type){
        return new SetTimestampHandler(type);
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

    public static class SetTimestampHandler extends Handler{
        private final String type;
        SetTimestampHandler(String type) {
            assert type.equals("a") || type.equals("m") || type.equals("c");
            this.type = type;
        }

        @Override
        Inode process(Inode inode) {
            switch (type) {
                case "a":
                    inode.lastAccessTime = System.currentTimeMillis();
                    break;
                case "m":
                    inode.lastModifyTime = System.currentTimeMillis();
                    break;
                case "c":
                    inode.lastChangeTime = System.currentTimeMillis();
                    break;
            }
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
