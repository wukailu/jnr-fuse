package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;

public class LV2IndirectBlock extends LV1IndirectBlock {

    @Override
    public ByteBuffer read(ByteBuffer mem, int startAddress, int len) {
        assert len > 0;
        ByteBuffer ret = ByteBuffer.allocate(len);
        for (int block : subBlocks) {
            if (block != 0) {
                if (startAddress >= 1024 * 256)
                    startAddress -= 1024 * 256;
                else {
                    LV1IndirectBlock lv1IndirectBlock = new LV1IndirectBlock().parse(mem, block, 1024);
                    ByteBuffer data = lv1IndirectBlock.read(mem, startAddress, Math.min(1024*256, len));
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

    @Override
    public int getSize(ByteBuffer mem) {
        int last = subBlockLen - 1;
        if (last == -1) return 0;
        int others = new LV1IndirectBlock().parse(mem, subBlocks[last], 1024).getSize(mem);
        return last * 1024 * subBlocks.length + others;
    }

    @Override
    public void write(ByteBuffer data, ByteBuffer mem, int startAddress, int len) {
        for (int i = 0; i < subBlocks.length; i++) {
            int block = subBlocks[i];
            if (startAddress >= 1024 * 256)
                startAddress -= 1024 * 256;
            else {
                LV1IndirectBlock lv1IndirectBlock = new LV1IndirectBlock();
                if (block != 0)
                    lv1IndirectBlock.parse(mem, block, 1024);
                lv1IndirectBlock.write(data, mem, startAddress, len);

                mem.reset();
                subBlocks[i] = mem.position();
                lv1IndirectBlock.flush(mem, mem.position());
                mem.mark();
                len -= Math.min(len, 1024 * 256 - startAddress);
                startAddress = 0;
            }
            if (len == 0)
                break;
        }
        for (int i = 0; i < subBlocks.length; i++)
            if (subBlocks[i] != 0)
                subBlockLen = i+1;
    }

}
