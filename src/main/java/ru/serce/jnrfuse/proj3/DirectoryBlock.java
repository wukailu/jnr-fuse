package ru.serce.jnrfuse.proj3;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class DirectoryBlock implements writableObject<DirectoryBlock> {
    public Map<String, Integer> contents;

    DirectoryBlock(){
        contents = new HashMap<String, Integer>();
    }

    @Override
    public int flush(ByteBuffer mem, int startAddress) {
        mem.position(startAddress);
        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            mem.putInt(entry.getKey().length());
            mem.put(entry.getKey().getBytes());
            mem.putInt(entry.getValue());
        }
        return mem.position();
    }

    @Override
    public DirectoryBlock parse(ByteBuffer mem, int startAddress, int len) {
        mem.position(startAddress);
        while (true){
            int name_len = mem.getInt();
            if (name_len == 0)
                break;
            byte[] str = new byte[name_len];
            mem.get(str);
            contents.put(new String(str), mem.getInt());
        }
        return this;
    }
}
