package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class DirectoryBlock extends writableObject<DirectoryBlock> {
    public Map<String, Integer> contents;

    DirectoryBlock(){
        contents = new HashMap<String, Integer>();
    }

    @Override
    public ByteBuffer flush() {
        ByteBuffer mem = ByteBuffer.allocate(totalBlocks()*1024);
        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            mem.putInt(entry.getKey().length());
            mem.put(entry.getKey().getBytes());
            mem.putInt(entry.getValue());
        }
        mem.putInt(0);
        mem.position(0);
        return mem;
    }

    public int totalBlocks(){
        int all = 0;
        for (Map.Entry<String, Integer> entry : contents.entrySet()){
            all += 4;
            all += entry.getKey().getBytes().length;
            all += 4;
        }
        all += 4;
        return all/1024 + 1;
    }

    @Override
    public DirectoryBlock parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        while (len > 0){
            int name_len = mem.getInt();
            len = len - 4;
            if (name_len == 0)
                break;
            byte[] str = new byte[name_len];
            mem.get(str);
            len = len - name_len;
            contents.put(new String(str), mem.getInt());
        }
        return this;
    }
}
