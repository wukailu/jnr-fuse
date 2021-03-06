package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class LV2IndirectBlock extends LV1IndirectBlock {

    @Override
    public ByteBuffer read(LogFS.MemoryManager manager, int startAddress, int len) {
        return readLv1Blocks(manager, startAddress, len, subBlocks);
    }

    @Override
    public int getSize(LogFS.MemoryManager manager) {
        int last = subBlockLen - 1;
        if (last == -1) return 0;
        int others = new LV1IndirectBlock().parse(manager.read(subBlocks[last], 1024)).getSize(manager);
        return last * 1024 * subBlocks.length + others;
    }

    @Override
    public void write(ByteBuffer data, LogFS.MemoryManager manager, int startAddress, int len, boolean isTruncate) {
        if (len <= 0) return;
        writeLv1Blocks(data, manager, startAddress, len, subBlocks, isTruncate);
        for (int i = 0; i < subBlocks.length; i++)
            if (subBlocks[i] != 0)
                subBlockLen = i+1;
    }

    public static ByteBuffer readLv1Blocks(LogFS.MemoryManager manager, int startAddress, int len, int[] lv1blocks){
        ByteBuffer ret = ByteBuffer.allocate(len);
        for (int block : lv1blocks) {
            if (block != 0) {
                if (startAddress >= 1024 * 256)
                    startAddress -= 1024 * 256;
                else {
                    ByteBuffer data = new LV1IndirectBlock().parse(manager.read(block, 1024)).read(manager, startAddress, len);
                    len -= data.remaining();
                    startAddress = 0;
                    ret.put(data);
                }
                if (len == 0)
                    break;
            }
        }
        ret.flip();
        return ret;
    }

    public static void writeLv1Blocks(ByteBuffer data, LogFS.MemoryManager manager, int startAddress, int len, int[] lv1blocks, boolean isTruncate){
        for (int i = 0; i < lv1blocks.length; i++) {
            int lv1 = lv1blocks[i];
            if(len > 0){
                if (startAddress < 1024 * 256){
                    LV1IndirectBlock indirectBlock = new LV1IndirectBlock();
                    if (lv1 != 0){
                        indirectBlock.parse(manager.read(lv1, 1024));
                        manager.release(lv1);
                    }else if (isTruncate){
                        len -= Math.min(len, 1024 * 256 - startAddress);
                        startAddress = 0;
                        continue;
                    }

                    indirectBlock.write(data, manager, startAddress, len, isTruncate);
                    if (!isTruncate || indirectBlock.getSize(manager) != 0)
                        lv1blocks[i] = manager.write(indirectBlock.flush());
                    else
                        lv1blocks[i] = 0;

                    len -= Math.min(len, 1024 * 256 - startAddress);
                    startAddress = 0;
                }else {
                    startAddress -= 1024 * 256;
                }
            }
        }
    }
}
