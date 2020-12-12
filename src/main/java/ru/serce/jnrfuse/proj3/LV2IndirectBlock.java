package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class LV2IndirectBlock extends LV1IndirectBlock {

    @Override
    public ByteBuffer read(ByteBuffer mem, int startAddress, int len) {
        return readLv1Blocks(mem, startAddress, len, subBlocks);
    }

    @Override
    public int getSize(ByteBuffer mem) {
        int last = subBlockLen - 1;
        if (last == -1) return 0;
        int others = new LV1IndirectBlock().parse(mem, subBlocks[last], 1024).getSize(mem);
        return last * 1024 * subBlocks.length + others;
    }

    @Override
    public void write(ByteBuffer data, ByteBuffer mem, int startAddress, int len) {
        if (len <= 0) return;
        writeLv1Blocks(data, mem, startAddress, len, subBlocks);
        for (int i = 0; i < subBlocks.length; i++)
            if (subBlocks[i] != 0)
                subBlockLen = i+1;
    }

    public static ByteBuffer readLv1Blocks(ByteBuffer mem, int startAddress, int len, int[] lv1blocks){
        ByteBuffer ret = ByteBuffer.allocate(len);
        for (int block : lv1blocks) {
            if (block != 0) {
                if (startAddress >= 1024 * 256)
                    startAddress -= 1024 * 256;
                else {
                    ByteBuffer data = new LV1IndirectBlock().parse(mem, block, 1024).read(mem, startAddress, len);
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

    public static void writeLv1Blocks(ByteBuffer data, ByteBuffer mem, int startAddress, int len, int[] lv1blocks){
        for (int i = 0; i < lv1blocks.length; i++) {
            int lv1 = lv1blocks[i];
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
                    lv1blocks[i] = mem.position();
                    indirectBlock.flush(mem, mem.position());
                    mem.mark();
                }else {
                    startAddress -= 1024 * 256;
                }
            }
        }
    }
}
