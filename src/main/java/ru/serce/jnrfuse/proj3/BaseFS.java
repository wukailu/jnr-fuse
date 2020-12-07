package ru.serce.jnrfuse.proj3;

import javafx.util.Pair;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

// The first block is not used.
public class BaseFS {
    protected ByteBuffer mem;
    /* The total size of FS, counted in Byte */
    public int total_size;
    public Checkpoint checkpoint1, checkpoint2, checkpoint;
    public Map<Integer, Integer> oldInodeMap, newInodeMap;
    public int inodeCnt;

    BaseFS(int total_size){
        this(ByteBuffer.allocate(total_size));
    }

    // Load from existing mem
    BaseFS(ByteBuffer mem){
        this.mem = mem;
        mem.position(0).mark();
        this.total_size = mem.capacity();
        checkpoint1 = new Checkpoint();
        checkpoint2 = new Checkpoint();
        checkpoint1.parse(mem, this.total_size-2048, 1024);
        checkpoint2.parse(mem, this.total_size-1024, 1024);
        if (checkpoint1.valid())
            checkpoint = checkpoint1;
        else
            checkpoint = checkpoint2;
        oldInodeMap = new HashMap<Integer, Integer>();
        newInodeMap = new HashMap<Integer, Integer>();
        inodeCnt = 1;
        int lastInodeMap = checkpoint.lastInodeMap;
        while (lastInodeMap > 0){
            InodeMap inodeMap = new InodeMap().parse(mem, lastInodeMap, 1024);
            oldInodeMap.putAll(inodeMap.inodeMap);
            lastInodeMap = inodeMap.preInodeMapAddress;
        }
        for(int i: oldInodeMap.values())
            inodeCnt = Math.max(inodeCnt, i+1);
    }

    public int getInodeAddress(int inode){
        Object ret = newInodeMap.get(inode);
        if (ret == null)
            ret = oldInodeMap.get(inode);
        assert ret != null;
        return (int) ret;
    }

    // address is page address, so it's like 0x3848f000
    public void setInodeAddress(int inode, int address){
        newInodeMap.put(inode, address);
    }

    // return the inode of the file
    public int path2Inode(String path){
        assert path.startsWith("/"); //TODO: fix this for windows
        String[] pathList = path.split("/");
        int currentInode = 1;
        for (String s: pathList){
            Inode inode = new Inode().parse(mem, getInodeAddress(currentInode), 1024);
            //TODO: check permission here
            DirectoryBlock directory = new DirectoryBlock().parse(inode.read(mem, 0, inode.size), 0, inode.size);
            Object ret = directory.contents.get(s);
            if(ret == null)
                return -1;
            currentInode = (int) ret;
        }
        return currentInode;
    }

    public ByteBuffer inode2Data(int id){
        Inode node = new Inode().parse(mem, getInodeAddress(id), 1024);
        return node.read(mem, 0, node.space);
    }
}
